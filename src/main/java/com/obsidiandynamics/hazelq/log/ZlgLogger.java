package com.obsidiandynamics.hazelq.log;

import java.util.function.*;
import java.util.logging.*;

import com.hazelcast.logging.*;
import com.obsidiandynamics.zerolog.*;

/**
 *  Adapter from Hazelcast's {@link ILogger} façade to Zerolog.
 */
final class ZlgLogger extends AbstractLogger {
  private static final LevelMapping[] mappings = new LevelMapping[9];
  
  static final class LevelMapping {
    final Level julLevel;
    final int zlgLevel;
    
    LevelMapping(Level julLevel, int zlgLevel) {
      this.julLevel = julLevel;
      this.zlgLevel = zlgLevel;
    }
  }
  
  static {
    int i = 0;
    for (Level level : new Level[] {Level.ALL,
                                    Level.FINEST, 
                                    Level.FINER,
                                    Level.FINE,
                                    Level.CONFIG,
                                    Level.INFO,
                                    Level.WARNING,
                                    Level.SEVERE,
                                    Level.OFF}) {
      mappings[i++] = new LevelMapping(level, mapLevel(level));
    }
  }
  
  static int mapLevel(Level level) {
    final int intValue = level.intValue();
    
    if (intValue <= Level.FINER.intValue()) {
      return LogLevel.TRACE;
    } else if (intValue <= Level.FINE.intValue()) {
      return LogLevel.DEBUG;
    } else if (intValue <= Level.CONFIG.intValue()) {
      return LogLevel.CONF;
    } else if (intValue <= Level.INFO.intValue()) {
      return LogLevel.INFO;
    } else if (intValue <= Level.WARNING.intValue()) {
      return LogLevel.WARN;
    } else if (intValue <= Level.SEVERE.intValue()) {
      return LogLevel.ERROR;
    } else {
      return LogLevel.OFF;
    }
  }
  
  static Level findFirst(LevelMapping[] mappings, IntPredicate predicate) {
    for (LevelMapping mapping : mappings) {
      if (predicate.test(mapping.zlgLevel)) {
        return mapping.julLevel;
      }
    }
    throw new IllegalStateException("No matching level found");
  }
  
  private final Zlg zlg;
  
  ZlgLogger(Zlg zlg) {
    this.zlg = zlg;
  }

  @Override
  public void log(Level level, String message) {
    zlg.level(mapLevel(level)).format(message).done();
  }

  @Override
  public void log(Level level, String message, Throwable thrown) {
    zlg.level(mapLevel(level)).format(message).threw(thrown).done();
  }

  @Override
  public void log(LogEvent logEvent) {
    final LogRecord record = logEvent.getLogRecord();
    log(record.getLevel(), record.getMessage(), record.getThrown());
  }
  
  @Override
  public Level getLevel() {
    return findFirst(mappings, zlg::isEnabled);
  }

  @Override
  public boolean isLoggable(Level level) {
    return zlg.isEnabled(mapLevel(level));
  }
}
