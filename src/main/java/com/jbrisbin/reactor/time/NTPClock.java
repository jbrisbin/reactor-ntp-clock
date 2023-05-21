package com.jbrisbin.reactor.time;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import de.elbosso.util.lang.jmx.DynamicPOJOMBean;
import de.elbosso.util.lang.jmx.ModelPojoMBeanCreator;
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

  private final static org.slf4j.Logger CLASS_LOGGER =org.slf4j.LoggerFactory.getLogger(NTPClock.class);
  private final static org.slf4j.Logger EXCEPTION_LOGGER =org.slf4j.LoggerFactory.getLogger("ExceptionCatcher");
  private static final Logger LOG = LoggerFactory.getLogger(NTPClock.class);
  private static final AtomicLong COUNTER = new AtomicLong(1);

  private static final NTPClock INSTANCE;

  static {
    NTPUDPClient client = new NTPUDPClient();
    Configuration CONFIGURATION=new Configuration();
    //System.out.println(CONFIGURATION.fetch_NextNtpHostToUse()+" "+CONFIGURATION.getNtpHosts()[0]);
    try
    {
      javax.management.MBeanServer mbs = java.lang.management.ManagementFactory.getPlatformMBeanServer();
      javax.management.ObjectName name = new javax.management.ObjectName("NTPClock:type=configuration");
      mbs.registerMBean(new DynamicPOJOMBean(CONFIGURATION), name);
    }
    catch(java.lang.Exception t)
    {
      EXCEPTION_LOGGER.warn(t.getMessage(),t);
    }

    client.setDefaultTimeout(CONFIGURATION.getTimeout());

    String name = "ntp-clock-" + String.valueOf(COUNTER.getAndIncrement());

    INSTANCE = new NTPClock(name, ZoneId.systemDefault(), client, CONFIGURATION ,null);
  }

  private volatile long aOrB = 0;
  private volatile long nanoTimeA = System.nanoTime();
  private volatile long nanoTimeB = nanoTimeA;
  private volatile long lastNTPUpdateTimeA = System.currentTimeMillis();
  private volatile long lastNTPUpdateTimeB = lastNTPUpdateTimeA;
  private volatile long cachedNow;
  private final Configuration configuration;

  private final ZoneId zoneId;
  private final NTPUDPClient client;
  private final Flux<TimeInfo> timeUpdates;

  private final List<FluxSink<TimeInfo>> timeInfoSinks = new ArrayList<>();

  public NTPClock(String name,
                  ZoneId zoneId,
                  NTPUDPClient client,
                  Configuration configuration
                  ) {
    this(name, zoneId, client,configuration, null);
  }

  private NTPClock(String name,
                   ZoneId zoneId,
                   NTPUDPClient client,
                   Configuration configuration,
                   Flux<TimeInfo> timeUpdates) {
    this.zoneId = zoneId;
    this.client = client;
    this.configuration=configuration;

    if (null == timeUpdates) {
      String prefix = zoneId.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + "-";

      if (configuration.getResolutionNanos() > 0) {
        new Thread(() -> {
          for (; ; ) {
            LockSupport.parkNanos(configuration.getResolutionNanos());
            cachedNow = getNow();
          }
        }, prefix + name + "-cache").start();
      }

      new Thread(() -> {
        for (; ; ) {
          LockSupport.parkNanos(configuration.getPollIntervalNanos());

          String ntpHost = configuration.fetchNextNtpHostToUse();
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
      }, prefix + name).start();

      this.timeUpdates = Flux.<TimeInfo>create(timeInfoSinks::add)
          .doOnNext(timeInfo -> {
            long ntpTime = timeInfo.getReturnTime(),
                nanoTime = System.nanoTime();
            int pollInt = timeInfo.getMessage().getPoll();
            if (pollInt < 3) {
              pollInt = 3; // no less than 8s
            }
            configuration.setPollInterval((int) (Math.pow(2, pollInt) * 1000));

            long newNanoTime = System.nanoTime();
            long newNtpTime = TimeUnit.NANOSECONDS.toMillis((newNanoTime - nanoTime)) + ntpTime;
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
    return new NTPClock(
        "ntp-clock-" + String.valueOf(COUNTER.getAndIncrement()),
        zone,
        client,
        configuration,
        timeUpdates
    );
  }

  @Override
  public long millis() {
    if (configuration.getResolution() == 0) {
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
      return lastNTPUpdateTimeA + TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - nanoTimeA));
    } else {
      return lastNTPUpdateTimeB + TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - nanoTimeB));
    }
  }

  private synchronized void sink(TimeInfo timeInfo) {
    for (FluxSink<TimeInfo> sink : timeInfoSinks) {
      sink.next(timeInfo);
    }
  }

}
