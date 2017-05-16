package com.jbrisbin.reactor.time;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@State(Scope.Thread)
public class NTPClockBenchmarks {

  int count = 1000;

  NTPClock clock;

  @Setup
  public void setup() {
    clock = NTPClock.getInstance();
  }

  @Benchmark
  public void systemCurrentTimeMillis() {
    System.currentTimeMillis();
  }

  @Benchmark
  public void ntpClockMillis() {
    clock.millis();
  }

  public static void main(String... args) throws RunnerException {
    Options opts = new OptionsBuilder()
        .include(NTPClockBenchmarks.class.getSimpleName())
        .warmupIterations(3)
        .warmupTime(TimeValue.seconds(10))
        .measurementIterations(3)
        .measurementTime(TimeValue.seconds(20))
        .forks(1)
        .build();
    new Runner(opts).run();
  }

}
