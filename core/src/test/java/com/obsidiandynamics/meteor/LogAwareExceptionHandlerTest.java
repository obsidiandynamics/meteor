package com.obsidiandynamics.meteor;

import org.junit.*;

import com.obsidiandynamics.zerolog.*;

public final class LogAwareExceptionHandlerTest {
  @Test
  public void test() {
    final MockLogTarget logTarget = new MockLogTarget();
    final LogAwareExceptionHandler handler = new LogAwareExceptionHandler(logTarget::logger);
    final String summary = "summary";
    final Throwable error = new RuntimeException("error");
    
    handler.onException(summary, error);
    
    logTarget.entries().assertCount(1);
    logTarget.entries().forLevel(LogLevel.WARN).withMessage(summary).withThrowable(error).assertCount(1);
  }
}
