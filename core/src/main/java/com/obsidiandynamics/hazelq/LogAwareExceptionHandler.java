package com.obsidiandynamics.hazelq;

import java.util.function.*;

import org.slf4j.*;

import com.obsidiandynamics.func.*;

final class LogAwareExceptionHandler implements ExceptionHandler {
  private final Supplier<Logger> logSupplier;
  
  LogAwareExceptionHandler(Supplier<Logger> logSupplier) {
    this.logSupplier = logSupplier;
  }

  @Override
  public void onException(String summary, Throwable error) {
    logSupplier.get().warn(summary, error);
  }
}
