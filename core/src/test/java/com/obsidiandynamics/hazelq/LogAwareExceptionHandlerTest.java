package com.obsidiandynamics.hazelq;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.obsidiandynamics.zerolog.*;
import com.obsidiandynamics.zerolog.MockLogTarget.*;

public final class LogAwareExceptionHandlerTest {
  @Test
  public void test() {
    final MockLogTarget logTarget = new MockLogTarget();
    final LogAwareExceptionHandler handler = new LogAwareExceptionHandler(logTarget::logger);
    final String summary = "summary";
    final Throwable error = new RuntimeException("error");
    
    handler.onException(summary, error);
    
    final List<Entry> list = logTarget.entries().forLevel(LogLevel.WARN).withException(Throwable.class).list();
    assertEquals(1, list.size());
    assertEquals(summary, list.get(0).getMessage());
    assertEquals(error, list.get(0).getThrowable());
  }
}
