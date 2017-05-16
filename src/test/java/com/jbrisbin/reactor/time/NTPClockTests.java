package com.jbrisbin.reactor.time;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.net.ntp.TimeInfo;
import org.junit.jupiter.api.Test;

import reactor.core.scheduler.Schedulers;

/**
 * Tests of the NTPClock.
 */

public class NTPClockTests {

  @Test
  public void getsTimeUpdatesFromPool() throws InterruptedException {
    NTPClock clock = NTPClock.getInstance();
    TimeInfo timeInfo = clock.getTimeUpdates()
        .publishOn(Schedulers.elastic())
        .blockFirst();
    assertNotNull(timeInfo.getAddress().getHostAddress(), "Got time from a real server");
    Thread.sleep(500);
    assertTrue(System.currentTimeMillis() > timeInfo.getReturnTime(), "Now should be greater than NTP time");
  }

}
