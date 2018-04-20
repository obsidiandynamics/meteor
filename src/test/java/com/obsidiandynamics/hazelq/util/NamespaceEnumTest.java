package com.obsidiandynamics.hazelq.util;

import static org.junit.Assert.*;

import org.junit.*;

public final class NamespaceEnumTest {
  private enum E implements NamespaceEnum {
    TEST_PACKAGE
  }
  
  @Test
  public void testQualify() {
    assertEquals("test.package::item", E.TEST_PACKAGE.qualify("item"));
  }
}
