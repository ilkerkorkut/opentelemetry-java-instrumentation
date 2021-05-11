package io.opentelemetry.javaagent.instrumentation.hazelcast_4;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.spi.impl.ClientInvocation;
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
    return TRACER;
  }

  @Override
  protected String spanName(
      Connection connection, HazelcastClientTracer.ClientOperation clientOperation,
      String sanitizedStatement) {
    return clientOperation.getClientMessage().getOperationName();
  }

  @Override
  protected String sanitizeStatement(ClientOperation clientOperation) {
    return clientOperation.getStringOperation();
  }

  @Override
  protected String dbSystem(Connection clientConnection) {
    return clientConnection != null ? clientConnection.toString() : "";
  }

  @Override
  protected String dbStatement(
      Connection connection, HazelcastClientTracer.ClientOperation clientOperation,
      String sanitizedStatement) {
    return sanitizedStatement;
  }

  @Override
  protected InetSocketAddress peerAddress(Connection connection) {
    try {
      if (connection != null && connection.getRemoteAddress() != null) {
        return connection.getRemoteAddress().getInetSocketAddress();
      } else {
        return null;
      }
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
    private final ClientInvocation clientInvocation;
    private final Object objectName;

    public ClientOperation(ClientMessage clientMessage, ClientInvocation clientInvocation,
        Object objectName) {
      this.clientMessage = clientMessage;
      this.clientInvocation = clientInvocation;
      this.objectName = objectName;
    }

    public ClientMessage getClientMessage() {
      return clientMessage;
    }

    public ClientInvocation getClientInvocation() {
      return clientInvocation;
    }

    public Object getObjectName() {
      return objectName;
    }

    private String getStringOperation() {
      if (clientMessage != null && clientInvocation != null) {
        return "Operation Name: " + clientMessage.getOperationName() + " Distributed Object Name: "
            + this.getObjectName() + " Correlation Id: " + clientMessage.getCorrelationId();
      } else {
        return new String(new byte[] {}, StandardCharsets.UTF_8);
      }
    }

    @Override
    public String toString() {
      return "ClientOperation{" +
          "clientMessage=" + clientMessage +
          ", objectName=" + objectName +
          '}';
    }
  }
}
