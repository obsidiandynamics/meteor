package com.obsidiandynamics.meteor;

public enum InitialOffsetScheme {
  EARLIEST,
  LATEST,
  AUTO,
  NONE;
  
  InitialOffsetScheme resolveConcreteScheme(boolean useGroups) {
    if (this == AUTO) {
      return useGroups ? EARLIEST : LATEST;
    } else {
      return this;
    }
  }
}
