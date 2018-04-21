package com.obsidiandynamics.hazelq;

import java.util.function.*;

import com.obsidiandynamics.func.*;
import com.obsidiandynamics.zerolog.*;

final class LogAwareExceptionHandler implements ExceptionHandler {
  private final Supplier<Zlg> zlgSupplier;
  
  LogAwareExceptionHandler(Supplier<Zlg> zlgSupplier) {
    this.zlgSupplier = zlgSupplier;
  }

  @Override
  public void onException(String summary, Throwable error) {
    zlgSupplier.get().w(summary, error);
  }
}
