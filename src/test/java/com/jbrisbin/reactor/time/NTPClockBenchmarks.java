package com.jbrisbin.reactor.time;

import java.time.ZoneId;
import java.util.Arrays;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
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

  @Param({"0", "1", "50"})
  int resolution;

  NTPClock clock;

  @Setup
  public void setup() {
    NTPUDPClient client = new NTPUDPClient();
    clock = new NTPClock(
        "ntp-clock-benchmark",
        ZoneId.systemDefault(),
        Arrays.asList("0.pool.ntp.org", "1.pool.ntp.org", "2.pool.ntp.org", "3.pool.ntp.org"),
        client,
        8_000,
        resolution,
        null
    );
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
