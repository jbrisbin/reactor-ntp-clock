# NTP Clock Flux

`reactor-ntp-clock` is a `java.time.Clock` implementation that uses NTP and a Reactor `Flux` to manage the current time.
The included JMH benchmarks show about a 10-12% improvement in throughput over standard access of
`System.currentTimeMillis()` via the system clock.

### Building

Build the project with Maven.

```
mvn clean install
```

### Usage

A global `Clock` is available with a single threaded reader that updates the internal clock values and manages the NTP connection.
Get access to this singleton and use it like any other `CLock`.

```java
Instant now = NTPClock.getInstance().instant();
// Use the Instant API to manipulate the date
```

### Configuring

Two system properties govern what NTP servers to use to keep time updated and what the default timeout is:

* `-Dcom.jbrisbin.reactor.time.NTPClock.servers=pool-0.ntp.org,pool-1.ntp.org`
* `-Dcom.jbrisbin.reactor.time.NTPClock.timeout=30000`

### Streaming Update Events

`TimeInfo` events are published into a `Flux<TimeInfo>` that is accessible via `NTPClock.getTimeUpdates()`.
Subscribe to this Flux to get the raw `TimeInfo` objects that are created whenever and update is read from an NTP server.

```java
NTPClock.getInstance().getTimeUpdates().subscribe(timeInfo -> {
  // handle TimeInfos
});
```

### License

[https://www.apache.org/licenses/LICENSE-2.0.html](https://www.apache.org/licenses/LICENSE-2.0.html)