package com.obsidiandynamics.meteor.util;

public interface NamespaceEnum {
  default String qualify(String objectName) {
    return toString().toLowerCase().replace('_', '.') + "::" + objectName;
  }
}
