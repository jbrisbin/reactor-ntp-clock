package com.jbrisbin.reactor.time;

import javax.management.DynamicMBean;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Configuration extends de.elbosso.util.beans.EventHandlingSupport
{
    private static int DEFAULT_RESOLUTION;
    private static int DEFAULT_TIMEOUT;
    private static String[] DEFAULT_NTP_HOSTS;

    static {
        String prefix = Configuration.class.getPackage().getName();

        DEFAULT_TIMEOUT = Integer.valueOf(System.getProperty(prefix + ".timeout", "30000"));

        DEFAULT_NTP_HOSTS = System.getProperty(prefix + ".servers",
                "0.pool.ntp.org,1.pool.ntp.org,2.pool.ntp.org,3.pool.ntp.org").split(",");
        DEFAULT_RESOLUTION = Integer.valueOf(System.getProperty(prefix + ".resolution", "0"));

    }
    private int timeout;
    private java.lang.String ntpHost;
    private java.lang.String[] ntpHosts;
    private int resolution;
    private long resolutionNanos;
    private int nextNtpHost;
    private long pollInterval=8_000;
    private long pollIntervalNanos=8_000;

    public Configuration()
    {
        super();
        resolution=DEFAULT_RESOLUTION;
        this.resolutionNanos = TimeUnit.MILLISECONDS.toNanos(resolution);
        timeout=DEFAULT_TIMEOUT;
        ntpHosts= new java.lang.String[DEFAULT_NTP_HOSTS.length];
        System.arraycopy(DEFAULT_NTP_HOSTS,0,ntpHosts,0,DEFAULT_NTP_HOSTS.length);
        this.pollIntervalNanos = TimeUnit.MILLISECONDS.toNanos(pollInterval);
    }

    public synchronized int getTimeout()
    {
        return timeout;
    }

    public synchronized void setTimeout(int timeout)
    {
        int old = getTimeout();
        this.timeout = timeout;
        send("timeout", old, getTimeout());
    }

    public synchronized String[] getNtpHosts()
    {
        return ntpHosts;
    }

    public synchronized void setNtpHosts(String[] ntpHosts)
    {
        String[] old = getNtpHosts();
        if(ntpHosts!=null)
        {
            this.ntpHosts = new java.lang.String[ntpHosts.length];
            System.arraycopy(ntpHosts, 0, this.ntpHosts, 0, ntpHosts.length);
        }
        else
            this.ntpHosts=null;
        send("ntpHosts", old, getNtpHosts());
    }

    public synchronized int getResolution()
    {
        return resolution;
    }

    public synchronized void setResolution(int resolution)
    {
        int old = getResolution();
        this.resolution = resolution;
        send("resolution", old, getResolution());
        long oldn = getResolutionNanos();
        this.resolutionNanos = TimeUnit.MILLISECONDS.toNanos(resolution);
        send("resolutionNanos", oldn, getResolutionNanos());
    }

    public synchronized long getPollInterval()
    {
        return pollInterval;
    }

    public synchronized void setPollInterval(long pollInterval)
    {
        long old = getPollInterval();
        this.pollInterval = pollInterval;
        send("pollInterval", old, getPollInterval());
        long oldp = getPollIntervalNanos();
        this.pollIntervalNanos = TimeUnit.MILLISECONDS.toNanos(pollInterval);
        send("pollIntervalNanos", oldp, getPollIntervalNanos());
    }

    public long getPollIntervalNanos()
    {
        return pollIntervalNanos;
    }

    public synchronized long getResolutionNanos()
    {
        return resolutionNanos;
    }
    public synchronized java.lang.String fetchNextNtpHostToUse()
    {
        java.lang.String old=getNtpHost();
        ntpHost=ntpHosts.length < 2 ? ntpHosts[0] : ntpHosts[(nextNtpHost++ % ntpHosts.length)];
        send("ntpHost", old, getNtpHost());
        return ntpHost;
    }

    public String getNtpHost()
    {
        return ntpHost;
    }
}
