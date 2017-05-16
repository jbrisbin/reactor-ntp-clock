package com.jbrisbin.reactor.time;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@State(Scope.Thread)
public class TimeMeasurementBenchmarks {

  NTPClock clockWithCaching;
  NTPClock clockWithoutCaching;

  volatile long nanoTimeStart;

  @Setup
  public void setup() {
    NTPUDPClient client = new NTPUDPClient();
    ZoneId zoneId = ZoneId.systemDefault();
    List<String> ntpHosts = Arrays.asList("0.pool.ntp.org", "1.pool.ntp.org", "2.pool.ntp.org", "3.pool.ntp.org");
    int pollIntvl = 8_000;

    clockWithCaching = new NTPClock(
        "cached-ntp-clock-benchmark",
        zoneId,
        ntpHosts,
        client,
        pollIntvl,
        150
    );
    clockWithoutCaching = new NTPClock(
        "uncached-ntp-clock-benchmark",
        ZoneId.systemDefault(),
        ntpHosts,
        client,
        pollIntvl,
        0
    );

    nanoTimeStart = System.nanoTime();
  }

  @Benchmark
  public void nanoTimeElapsed(Blackhole bh) {
    long nanoTimeEnd = System.nanoTime();
    long elapsed = nanoTimeEnd - nanoTimeStart;
    bh.consume(elapsed);
    nanoTimeStart = nanoTimeEnd;
  }

  @Benchmark
  public void currentTimeMillis(Blackhole bh) {
    long now = System.currentTimeMillis();
    bh.consume(now);
  }

  @Benchmark
  public void cachedNtpClockMillis(Blackhole bh) {
    long now = clockWithCaching.millis();
    bh.consume(now);
  }

  @Benchmark
  public void uncachedNtpClockMillis(Blackhole bh) {
    long now = clockWithoutCaching.millis();
    bh.consume(now);
  }

  public static void main(String... args) throws RunnerException {
    Options opts = new OptionsBuilder()
        .include(TimeMeasurementBenchmarks.class.getSimpleName())
        .warmupIterations(3)
        .warmupTime(TimeValue.seconds(10))
        .measurementIterations(3)
        .measurementTime(TimeValue.seconds(10))
        .forks(1)
        .build();
    new Runner(opts).run();
  }

}
