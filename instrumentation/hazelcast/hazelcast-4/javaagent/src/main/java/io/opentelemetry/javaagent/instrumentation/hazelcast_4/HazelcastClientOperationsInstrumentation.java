package io.opentelemetry.javaagent.instrumentation.hazelcast_4;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.hazelcast_4.HazelcastClientTracer.tracer;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.hazelcast.client.impl.connection.ClientConnection;
import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.spi.impl.ClientInvocation;
import com.hazelcast.client.impl.spi.impl.ClientInvocationFuture;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HazelcastClientOperationsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.hazelcast.client.impl.spi.impl.ClientInvocation");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("invokeOnSelection"))
            .and(takesArguments(0)),
        HazelcastClientOperationsInstrumentation.class.getName()
            + "$HazelcastClientOperationsAdvice");
  }

  public static class HazelcastClientOperationsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This ClientInvocation clientInvocation,
        @Advice.FieldValue("objectName") final Object objectName,
        @Advice.FieldValue("sentConnection") final ClientConnection sentConnection,
        @Advice.FieldValue("clientMessage") final ClientMessage clientMessage,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      final List<String> IGNORED_OPERATIONS = Arrays.asList(
          "Client.Statistics",
          "Client.AddClusterViewListener",
          "Client.CreateProxy",
          "Client.LocalBackupListener");

      if (IGNORED_OPERATIONS.contains(clientMessage.getOperationName())) {
        return;
      }

      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(ClientInvocation.class);
      if (callDepth > 0) {
        return;
      }

      context = tracer().startSpan(currentContext(), sentConnection,
          new HazelcastClientTracer.ClientOperation(clientMessage, clientInvocation, objectName));
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.FieldValue("clientInvocationFuture") final ClientInvocationFuture future,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      try {
        if (throwable != null) {
          tracer().endExceptionally(context, throwable);
        } else {
          future.whenComplete(new HazelcastAsyncOperationHandler(context, scope, tracer()));
        }
      } finally {
        tracer().end(context);
        CallDepthThreadLocalMap.reset(ClientInvocation.class);
      }

    }
  }
}
