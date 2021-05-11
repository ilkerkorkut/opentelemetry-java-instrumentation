package io.opentelemetry.javaagent.instrumentation.hazelcast_4;

import com.hazelcast.client.impl.protocol.ClientMessage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.function.BiConsumer;

public class HazelcastAsyncOperationHandler implements BiConsumer<ClientMessage, Throwable> {

  private final Context context;
  private final Scope scope;
  private final HazelcastClientTracer hazelcastClientTracer;

  public HazelcastAsyncOperationHandler(final Context context, final Scope scope, final HazelcastClientTracer hazelcastClientTracer) {
    this.context = context;
    this.scope = scope;
    this.hazelcastClientTracer = hazelcastClientTracer;
  }

  public void onResponse(ClientMessage clientMessage) {
    hazelcastClientTracer.end(context);
  }

  public void onFailure(final Throwable t) {
    hazelcastClientTracer.endExceptionally(context, t);
  }

  @Override
  public void accept(ClientMessage clientMessage, Throwable throwable) {
    if (throwable != null) {
      onFailure(throwable);
    } else {
      onResponse(clientMessage);
    }
  }
}
