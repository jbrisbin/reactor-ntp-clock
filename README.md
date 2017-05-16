# NTP Clock Flux

`reactor-ntp-clock` is a `java.time.Clock` implementation that uses NTP and a Reactor `Flux` to manage the current time.
The included JMH benchmarks show about a 10-12% improvement in throughput over standard access of
`System.currentTimeMillis()` via the system clock and more than a 600% increase when using internal caching of the "now"
by setting a `resolution` to 1ms or more. This caching is important for situations where you're accessing the clock very
many times within a matter of milliseconds but you only really need an "approximate" time of some configurable resolution.

The global static instance of the `NTPClock` has this caching disabled by default. Either set the system property to set
a `resolution` value of 1ms or greater, or create a new `NTPClock` using the CTOR.

### Building

Build the project with Maven.

```
mvn clean install
```

### Usage

A global `Clock` is available with a single threaded reader that updates the internal clock values and manages the NTP connection.
Get access to this singleton and use it like any other `Clock`.

```java
Instant now = NTPClock.getInstance().instant();
// Use the Instant API to manipulate the date
```

### Configuring

Several system properties govern what NTP servers to use to keep time updated, what the default timeout is,
and what the resolution in `ms` of the Clock is:

* `-Dcom.jbrisbin.reactor.time.NTPClock.servers=0.pool.ntp.org,1.pool.ntp.org,2.pool.ntp.org,3.pool.ntp.org` Comma-separated list of NTP servers to round-robin through.
* `-Dcom.jbrisbin.reactor.time.NTPClock.timeout=30000` Default socket timeout value.
* `-Dcom.jbrisbin.reactor.time.NTPClock.resolution=0` A value of `0` means disable caching internally. 1 or greater means cache the "now" value every `resolution` milliseconds and return that cached value instead of computing it each time.

### Streaming Update Events

`TimeInfo` events are published into a `Flux<TimeInfo>` that is accessible via `NTPClock.getTimeUpdates()`.
Subscribe to this Flux to get the raw `TimeInfo` objects that are created whenever an update is read from an NTP server.

```java
NTPClock.getInstance().getTimeUpdates().subscribe(timeInfo -> {
  // handle TimeInfos
});
```

### TODO Items

* Watch for errors in getting the NTP time and, if over a configurable amount, fall back to updating the now cache with `currentTimeMillis`.
* Record metrics for what servers are being hit and what the pool interval is, if it changes, and report them.
* Expose more of the internals to external management at runtime.

### License

[https://www.apache.org/licenses/LICENSE-2.0.html](https://www.apache.org/licenses/LICENSE-2.0.html)