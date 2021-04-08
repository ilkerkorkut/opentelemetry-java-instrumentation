package io.opentelemetry.javaagent.instrumentation.hazelcast.v4_x;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class HazelcastInstrumentationModule extends InstrumentationModule {
  public HazelcastInstrumentationModule() {
    super("hazelcast", "hazelcast-4.x");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HazelcastClientOperationsInstrumentation());
  }
}
