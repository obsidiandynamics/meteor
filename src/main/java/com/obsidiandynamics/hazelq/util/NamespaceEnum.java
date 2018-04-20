package com.obsidiandynamics.hazelq.util;

public interface NamespaceEnum {
  default String qualify(String objectName) {
    return toString().toLowerCase().replace('_', '.') + "::" + objectName;
  }
}
