package io.opentelemetry.javaagent.instrumentation.hazelcast_4;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

import static java.util.Collections.singletonList;

@AutoService(InstrumentationModule.class)
public class HazelcastInstrumentationModule extends InstrumentationModule {
  public HazelcastInstrumentationModule() {
    super("hazelcast", "hazelcast-4");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HazelcastClientOperationsInstrumentation());
  }
}
