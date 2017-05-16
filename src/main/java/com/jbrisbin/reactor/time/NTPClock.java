package com.jbrisbin.reactor.time;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * {@link Clock} implementation that uses a configurable list of NTP servers (defaults to 'pool.ntp.org') to maintain
 * the current time. Calls to {@link Clock#millis()} return the current time as maintained by the NTP update process
 * and does not touch the system clock. Use as a drop-in replacement for System.currentTimeMillis() when needing the
 * current time a lot but on systems where accessing the system clock can be expensive at scale.
 */
public final class NTPClock extends Clock {

  private static final Logger LOG = LoggerFactory.getLogger(NTPClock.class);

  private static final NTPClock INSTANCE;

  static {
    NTPUDPClient client = new NTPUDPClient();
    String prefix = NTPClock.class.getPackage().getName();

    int timeout = Integer.valueOf(System.getProperty(prefix + ".timeout", "30000"));
    client.setDefaultTimeout(timeout);

    String[] ntpHosts = System.getProperty(prefix + ".servers",
        "0.pool.ntp.org,1.pool.ntp.org,2.pool.ntp.org,3.pool.ntp.org").split(",");
    int resolution = Integer.valueOf(System.getProperty(prefix + ".resolution", "0"));
    INSTANCE = new NTPClock("ntp-clock", ZoneId.systemDefault(), Arrays.asList(ntpHosts), client, 8_000, resolution,
        null);
  }

  private volatile long aOrB = 0;
  private volatile long nanoTimeA = System.nanoTime();
  private volatile long nanoTimeB = nanoTimeA;
  private volatile long lastNTPUpdateTimeA = System.currentTimeMillis();
  private volatile long lastNTPUpdateTimeB = lastNTPUpdateTimeA;
  private volatile long pollInterval;
  private volatile long cachedNow;

  private int nextNtpHost = 0;
  private int ntpHostCount = 0;

  private final ZoneId zoneId;
  private final List<String> ntpHosts;
  private final NTPUDPClient client;
  private final int resolution;
  private final Flux<TimeInfo> timeUpdates;

  private final List<FluxSink<TimeInfo>> timeInfoSinks = new ArrayList<>();

  public NTPClock(String name,
                  ZoneId zoneId,
                  List<String> ntpHosts,
                  NTPUDPClient client,
                  long pollInterval,
                  int resolution,
                  Flux<TimeInfo> timeUpdates) {
    this.zoneId = zoneId;
    this.ntpHosts = ntpHosts;
    this.ntpHostCount = ntpHosts.size();
    this.client = client;
    this.pollInterval = pollInterval;
    this.resolution = resolution;

    if (null == timeUpdates) {
      if (resolution > 0) {
        new Thread(() -> {
          for (; ; ) {
            LockSupport.parkNanos(resolution * 1_000_000);
            cachedNow = getNow();
          }
        }, name + "-cache").start();
      }

      new Thread(() -> {
        while (!Thread.currentThread().isInterrupted()) {
          LockSupport.parkNanos(pollInterval * 1_000_000);

          String ntpHost = (ntpHostCount < 2 ? ntpHosts.get(0) : ntpHosts.get((nextNtpHost++ % ntpHostCount)));
          InetAddress ntpServer;
          try {
            ntpServer = InetAddress.getByName(ntpHost);
          } catch (UnknownHostException e) {
            LOG.error(e.getMessage(), e);
            continue;
          }

          TimeInfo ntpTime;
          try {
            ntpTime = getClient().getTime(ntpServer);
          } catch (SocketTimeoutException e) {
            if (LOG.isDebugEnabled()) {
              LOG.debug(e.getMessage(), e);
            }
            continue;
          } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            continue;
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug("NTP time on server {}: {}", ntpServer.getHostAddress(), ntpTime.getMessage());
          }

          sink(ntpTime);
        }
      }, name).start();

      this.timeUpdates = Flux.<TimeInfo>create(timeInfoSinks::add)
          .doOnNext(timeInfo -> {
            long ntpTime = timeInfo.getReturnTime(),
                nanoTime = System.nanoTime();
            int pollInt = timeInfo.getMessage().getPoll();
            if (pollInt < 3) {
              pollInt = 3; // no less than 8s
            }
            NTPClock.this.pollInterval = (int) (Math.pow(2, pollInt) * 1000);

            long newNanoTime = System.nanoTime();
            long newNtpTime = ((newNanoTime - nanoTime) / 1_000_000) + ntpTime;
            if (aOrB == 0) {
              nanoTimeB = newNanoTime;
              lastNTPUpdateTimeB = newNtpTime;
              aOrB = 1;
            } else {
              nanoTimeA = newNanoTime;
              lastNTPUpdateTimeA = newNtpTime;
              aOrB = 0;
            }
          });
      this.timeUpdates.subscribe();
    } else {
      this.timeUpdates = timeUpdates;
    }
  }

  /**
   * Get the global singleton instance of the {@code NTPClock}. There can be only one.
   *
   * @return the global clock
   */
  public static NTPClock getInstance() {
    return INSTANCE;
  }

  /**
   * Get the {@link Flux} on which time updates are published.
   *
   * @return the {@code Flux<TimeInfo>} to receive time updates
   */
  public Flux<TimeInfo> getTimeUpdates() {
    return Flux.create(sink -> {
      synchronized (NTPClock.this) {
        timeInfoSinks.add(sink);
      }
    });
  }

  public ZoneId getZone() {
    return zoneId;
  }

  public Clock withZone(ZoneId zone) {
    return new NTPClock("ntp-clock-" + zone.getId(), zone, ntpHosts, client, pollInterval, resolution, timeUpdates);
  }

  @Override
  public long millis() {
    if (resolution == 0) {
      return getNow();
    } else {
      return cachedNow;
    }
  }

  public Instant instant() {
    return Instant.ofEpochMilli(millis());
  }

  private NTPUDPClient getClient() {
    return client;
  }

  private long getNow() {
    if (aOrB == 0) {
      return lastNTPUpdateTimeA + ((System.nanoTime() - nanoTimeA) / 1_000_000);
    } else {
      return lastNTPUpdateTimeB + ((System.nanoTime() - nanoTimeB) / 1_000_000);
    }
  }

  private synchronized void sink(TimeInfo timeInfo) {
    for (FluxSink<TimeInfo> sink : timeInfoSinks) {
      sink.next(timeInfo);
    }
  }

}
