package com.obsidiandynamics.hazelq;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.*;
import org.slf4j.*;

public final class LogAwareExceptionHandlerTest {
  @Test
  public void test() {
    final Logger log = mock(Logger.class);
    final LogAwareExceptionHandler handler = new LogAwareExceptionHandler(() -> log);
    final String summary = "summary";
    final Throwable error = new RuntimeException("error");
    handler.onException(summary, error);
    verify(log).warn(eq(summary), eq(error));
  }
}
