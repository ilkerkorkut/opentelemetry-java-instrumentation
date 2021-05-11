package io.opentelemetry.javaagent.instrumentation.hazelcast_4;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

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
