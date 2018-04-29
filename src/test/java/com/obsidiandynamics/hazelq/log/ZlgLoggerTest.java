package com.obsidiandynamics.hazelq.log;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.logging.*;

import org.junit.*;
import org.mockito.*;

import com.hazelcast.instance.*;
import com.hazelcast.logging.*;
import com.obsidiandynamics.hazelq.log.ZlgLogger.*;
import com.obsidiandynamics.zerolog.*;

public final class ZlgLoggerTest {
  @Test
  public void testMapLevel() {
    assertEquals(LogLevel.TRACE, ZlgLogger.mapLevel(Level.ALL));
    assertEquals(LogLevel.TRACE, ZlgLogger.mapLevel(Level.FINEST));
    assertEquals(LogLevel.TRACE, ZlgLogger.mapLevel(Level.FINER));
    assertEquals(LogLevel.DEBUG, ZlgLogger.mapLevel(Level.FINE));
    assertEquals(LogLevel.CONF, ZlgLogger.mapLevel(Level.CONFIG));
    assertEquals(LogLevel.INFO, ZlgLogger.mapLevel(Level.INFO));
    assertEquals(LogLevel.WARN, ZlgLogger.mapLevel(Level.WARNING));
    assertEquals(LogLevel.ERROR, ZlgLogger.mapLevel(Level.SEVERE));
    assertEquals(LogLevel.OFF, ZlgLogger.mapLevel(Level.OFF));
  }
  
  private static LevelMapping[] buildTestMappings() {
    final LevelMapping[] mappings = new LevelMapping[3];
    int i = 0;
    mappings[i++] = new LevelMapping(Level.INFO, LogLevel.INFO);
    mappings[i++] = new LevelMapping(Level.WARNING, LogLevel.WARN);
    mappings[i++] = new LevelMapping(Level.SEVERE, LogLevel.ERROR);
    return mappings;
  }

  @Test
  public void testFindFirst() {
    final Level first = ZlgLogger.findFirst(buildTestMappings(), level -> level == LogLevel.WARN);
    assertEquals(Level.WARNING, first);
  }

  @Test(expected=IllegalStateException.class)
  public void testFindFirstNonExistant() {
    ZlgLogger.findFirst(buildTestMappings(), level -> level == LogLevel.OFF);
  }
  
  @Test
  public void testLogLevelMessage() {
    final MockLogTarget logTarget = new MockLogTarget();
    final ZlgLogger logger = new ZlgLogger(logTarget.logger());
    
    logger.log(Level.WARNING, "test message");
    assertEquals(1, logTarget.entries().forLevel(LogLevel.WARN).containing("test message").list().size());
  }
  
  @Test
  public void testLogLevelThrowableMessage() {
    final MockLogTarget logTarget = new MockLogTarget();
    final ZlgLogger logger = new ZlgLogger(logTarget.logger());
    final IOException cause = new IOException("test exception");
    
    logger.log(Level.WARNING, "test message", cause);
    assertEquals(1, logTarget.entries()
                 .forLevel(LogLevel.WARN).containing("test message").withThrowableType(IOException.class).list().size());
  }
  
  @Test
  public void testLogEvent() {
    final MockLogTarget logTarget = new MockLogTarget();
    final ZlgLogger logger = new ZlgLogger(logTarget.logger());
    final IOException cause = new IOException("test exception");
    final LogRecord record = new LogRecord(Level.WARNING, "test message");
    record.setThrown(cause);
    final LogEvent event = new LogEvent(record, new SimpleMemberImpl());
    
    logger.log(event);
    assertEquals(1, logTarget.entries()
                 .forLevel(LogLevel.WARN).containing("test message").withThrowableType(IOException.class).list().size());
  }
  
  @Test
  public void testGetLevel() {
    final Zlg zlg = mock(Zlg.class, Answers.CALLS_REAL_METHODS);
    when(zlg.isEnabled(anyInt())).thenAnswer(invocation -> {
      final int level = invocation.getArgument(0);
      return level >= LogLevel.WARN;
    });
    final ZlgLogger logger = new ZlgLogger(zlg);
    
    assertEquals(Level.WARNING, logger.getLevel());
  }
  
  @Test
  public void testIsLoggable() {
    final Zlg zlg = mock(Zlg.class, Answers.CALLS_REAL_METHODS);
    when(zlg.isEnabled(anyInt())).thenAnswer(invocation -> {
      final int level = invocation.getArgument(0);
      return level >= LogLevel.WARN;
    });
    final ZlgLogger logger = new ZlgLogger(zlg);
    
    assertFalse(logger.isLoggable(Level.INFO));
    assertTrue(logger.isLoggable(Level.WARNING));
    assertTrue(logger.isLoggable(Level.SEVERE));
  }
}
