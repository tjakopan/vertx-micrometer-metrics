/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.vertx.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.micrometer.backends.BackendRegistries;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

/**
 * @author Joel Takvorian
 */
public final class RegistryInspector {

  private RegistryInspector() {
  }

  public static List<Datapoint> listWithoutTimers(String startsWith) {
    return listWithoutTimers(startsWith, MicrometerMetricsOptions.DEFAULT_REGISTRY_NAME);
  }

  public static List<Datapoint> listTimers(String startsWith) {
    return listTimers(startsWith, MicrometerMetricsOptions.DEFAULT_REGISTRY_NAME);
  }

  public static List<Datapoint> listWithoutTimers(String startsWith, String regName) {
    MeterRegistry registry = BackendRegistries.getNow(regName);
    return registry.getMeters().stream()
      .filter(m -> m.getId().getType() != Meter.Type.TIMER && m.getId().getType() != Meter.Type.LONG_TASK_TIMER)
      .filter(m -> m.getId().getName().startsWith(startsWith))
      .flatMap(m -> {
        String id = id(m);
        return StreamSupport.stream(m.measure().spliterator(), false)
          .map(measurement -> new Datapoint(id + "$" + measurement.getStatistic().name(), measurement.getValue()));
      })
      .collect(toList());
  }

  public static List<Datapoint> listTimers(String startsWith, String regName) {
    MeterRegistry registry = BackendRegistries.getNow(regName);
    return registry.getMeters().stream()
      .filter(m -> m.getId().getType() == Meter.Type.TIMER || m.getId().getType() == Meter.Type.LONG_TASK_TIMER)
      .filter(m -> m.getId().getName().startsWith(startsWith))
      .flatMap(m -> {
        String id = id(m);
        return StreamSupport.stream(m.measure().spliterator(), false)
          .map(measurement -> new Datapoint(id + "$" + measurement.getStatistic().name(), measurement.getValue()));
      })
      .collect(toList());
  }

  private static String id(Meter m) {
    return m.getId().getName() + "["
      + StreamSupport.stream(m.getId().getTags().spliterator(), false)
          .map(t -> t.getKey() + '=' + t.getValue())
          .collect(Collectors.joining(","))
      + "]";
  }

  public static RegistryInspector.Datapoint dp(String id, double value) {
    return new Datapoint(id, value);
  }

  public static RegistryInspector.Datapoint dp(String id, int value) {
    return new Datapoint(id, (double) value);
  }

  public static void waitForValue(Vertx vertx, TestContext context, String fullName, Predicate<Double> p) {
    waitForValue(vertx, context, MicrometerMetricsOptions.DEFAULT_REGISTRY_NAME, fullName, p);
  }

  public static void waitForValue(Vertx vertx, TestContext context, String regName, String fullName, Predicate<Double> p) {
    Async ready = context.async();
    vertx.setPeriodic(200, l -> {
      RegistryInspector.listWithoutTimers("", regName).stream()
        .filter(dp -> fullName.equals(dp.id()))
        .filter(dp -> p.test(dp.value()))
        .findAny()
        .ifPresent(dp -> ready.countDown());
    });
    ready.awaitSuccess(10000);
  }

  public static class Datapoint {
    private final String id;
    private final Double value;

    private Datapoint(String id, Double value) {
      this.id = id;
      this.value = value;
    }

    String id() {
      return id;
    }

    Double value() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Datapoint datapoint = (Datapoint) o;
      return Objects.equals(id, datapoint.id) &&
        Objects.equals(value, datapoint.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, value);
    }

    @Override
    public String toString() {
      return id + "/" + value;
    }
  }
}