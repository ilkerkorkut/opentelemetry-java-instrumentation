import com.hazelcast.client.HazelcastClient
import com.hazelcast.client.config.ClientConfig
import com.hazelcast.client.config.ClientNetworkConfig
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import com.hazelcast.map.IMap
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT

class HazelcastClientTest extends AgentInstrumentationSpecification {

  @Shared
  HazelcastInstance hazelcastInstance;

  @Shared
  HazelcastInstance hazelcastClient;

  def setupSpec() {
    println "Starting HazelcastInstance"
    hazelcastInstance = Hazelcast.newHazelcastInstance()
    ClientConfig clientConfig = new ClientConfig();
    clientConfig.setClusterName("dev")
    ClientNetworkConfig clientNetworkConfig = new ClientNetworkConfig()
    clientNetworkConfig.addAddress("192.168.1.38:5701")
    clientConfig.setNetworkConfig(clientNetworkConfig)
    hazelcastClient = HazelcastClient.newHazelcastClient(clientConfig);
  }

  def cleanupSpec() {
    hazelcastInstance.shutdown();
    hazelcastClient.shutdown();
  }

  def "map put"() {
    when:
    IMap<Integer, Integer> map = hazelcastClient.getMap("foo")
    map.put(3,5)
    println("!!!!!!!!!")
    println(map.get(3))
    println(getTraces())
    then:
    println(getTraces())
  }
}
