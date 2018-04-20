package com.obsidiandynamics.hazelq;

import static org.junit.Assert.*;

import java.io.*;

import org.junit.*;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.obsidiandynamics.yconf.*;

public final class GridHazelcastProviderTest {
  private HazelcastProvider provider;
  
  @Before
  public void before() {
    provider = GridHazelcastProvider.getInstance();
  }
  
  @After
  public void after() {
    if (provider != null) provider.shutdownAll();
  }
  
  @Test
  public void testCreate() {
    final Config config = new Config()
        .setProperty("hazelcast.shutdownhook.enabled", "false")
        .setProperty("hazelcast.logging.type", "none");
    
    final MulticastConfig multicastConfig = new MulticastConfig()
        .setEnabled(false);
    
    final TcpIpConfig tcpIpConfig = new TcpIpConfig()
        .setEnabled(false);
    
    config.setNetworkConfig(new NetworkConfig().setJoin(new JoinConfig()
                                                        .setMulticastConfig(multicastConfig)
                                                        .setTcpIpConfig(tcpIpConfig)));
    final HazelcastInstance instance = GridHazelcastProvider.getInstance().createInstance(config);
    assertNotNull(instance);
  }
  
  @Test
  public void testConfig() throws IOException {
    assertNotNull(new MappingContext().withParser(__reader -> new Object()).fromString("").map(GridHazelcastProvider.class));
  }
}
