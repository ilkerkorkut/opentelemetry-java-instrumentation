package io.opentelemetry.javaagent.instrumentation.hazelcast.v4_x;


import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.spi.ClientProxy;
import com.hazelcast.internal.nio.Connection;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class HazelcastClientTracer extends
    DatabaseClientTracer<Connection, HazelcastClientTracer.ClientOperation, String> {

  private static final HazelcastClientTracer TRACER = new HazelcastClientTracer();

  private HazelcastClientTracer() {
    super(NetPeerAttributes.INSTANCE);
  }

  public static HazelcastClientTracer tracer() {
    System.out.println("==== TRACER");
    return TRACER;
  }

  @Override
  protected String spanName(
      Connection connection, HazelcastClientTracer.ClientOperation clientOperation, String sanitizedStatement) {
    return clientOperation.getClientMessage().getOperationName();
  }

  @Override
  protected String sanitizeStatement(ClientOperation clientOperation) {
    return clientOperation.getStringOperation();
  }

  @Override
  protected String dbSystem(Connection clientConnection) {
    return clientConnection.toString();
  }

  @Override
  protected String dbStatement(
      Connection connection, HazelcastClientTracer.ClientOperation clientOperation, String sanitizedStatement) {
    return sanitizedStatement;
  }

  @Override
  protected InetSocketAddress peerAddress(Connection connection) {
    try {
      return connection.getRemoteAddress().getInetSocketAddress();
    } catch (UnknownHostException e) {
      return null;
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.hazelcast-4.x";
  }

  public static final class ClientOperation {
    private final ClientMessage clientMessage;
    private final ClientProxy clientProxy;

    public ClientOperation(ClientMessage clientMessage, ClientProxy clientProxy) {
      this.clientMessage = clientMessage;
      this.clientProxy = clientProxy;
    }

    public ClientMessage getClientMessage() {
      return clientMessage;
    }

    public ClientProxy getClientProxy() {
      return clientProxy;
    }

    private String getStringOperation() {
      if (clientMessage != null && clientProxy != null) {
        return "ServiceName: " +clientProxy.getServiceName() + " operationName: " + clientMessage.getOperationName() + " distributedObjectName: "+clientProxy.getName();
      } else {
        return new String(new byte[] {}, StandardCharsets.UTF_8);
      }
    }

    @Override
    public String toString() {
      return "ClientOperation{" +
          "clientMessage=" + clientMessage +
          ", clientProxy=" + clientProxy +
          '}';
    }
  }
}
