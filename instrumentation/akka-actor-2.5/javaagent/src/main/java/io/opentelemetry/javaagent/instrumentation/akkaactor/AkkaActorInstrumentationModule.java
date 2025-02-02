/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@AutoService(InstrumentationModule.class)
public class AkkaActorInstrumentationModule extends InstrumentationModule {
  public AkkaActorInstrumentationModule() {
    super("akka-actor", "akka-actor-2.5");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AkkaForkJoinPoolInstrumentation(),
        new AkkaForkJoinTaskInstrumentation(),
        new AkkaDispatcherInstrumentation(),
        new AkkaActorCellInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> map = new HashMap<>();
    map.put(Runnable.class.getName(), State.class.getName());
    map.put(Callable.class.getName(), State.class.getName());
    map.put(AkkaForkJoinTaskInstrumentation.TASK_CLASS_NAME, State.class.getName());
    map.put("akka.dispatch.Envelope", State.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }
}
