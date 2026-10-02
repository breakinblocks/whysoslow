package com.breakinblocks.whysoslow.profiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class TickProfiler {
    private static final long SLOW_TICK_MS = 100;
    private static final long[] BUCKET_LIMITS_MS = {50, 100, 250, 1000};
    private static final int KEEP_SLOWEST = 5;

    private static volatile boolean active = false;
    private static final AtomicLong[] buckets = new AtomicLong[BUCKET_LIMITS_MS.length + 1];
    private static final AtomicLong ticks = new AtomicLong();
    private static final AtomicLong totalNanos = new AtomicLong();
    private static final AtomicLong maxNanos = new AtomicLong();
    private static final StackSampler.Profile slowTickProfile = new StackSampler.Profile();
    private static final List<SlowTick> slowest = Collections.synchronizedList(new ArrayList<>());

    private static StackSampler.Watch watch;
    private static long tickStart;

    static {
        for (int i = 0; i < buckets.length; i++) buckets[i] = new AtomicLong();
    }

    private TickProfiler() {
    }

    public static synchronized void start() {
        for (AtomicLong bucket : buckets) bucket.set(0);
        ticks.set(0);
        totalNanos.set(0);
        maxNanos.set(0);
        slowest.clear();
        slowTickProfile.reset();
        active = true;
    }

    public static synchronized void stop() {
        active = false;
        if (watch != null) {
            StackSampler.end(watch);
            watch = null;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static void onTickStart() {
        if (!active) return;
        tickStart = System.nanoTime();
        watch = StackSampler.begin(Thread.currentThread(), "server tick", SLOW_TICK_MS);
    }

    public static void onTickEnd() {
        if (!active || tickStart == 0) return;
        long elapsed = System.nanoTime() - tickStart;
        tickStart = 0;
        StackSampler.Profile profile = StackSampler.end(watch);
        watch = null;

        ticks.incrementAndGet();
        totalNanos.addAndGet(elapsed);
        maxNanos.accumulateAndGet(elapsed, Math::max);
        long ms = elapsed / 1_000_000;
        int bucket = 0;
        while (bucket < BUCKET_LIMITS_MS.length && ms >= BUCKET_LIMITS_MS[bucket]) bucket++;
        buckets[bucket].incrementAndGet();

        if (ms >= SLOW_TICK_MS && profile != null && profile.samples() > 0) {
            slowTickProfile.merge(profile);
            synchronized (slowest) {
                slowest.add(new SlowTick(System.currentTimeMillis(), elapsed, profile));
                slowest.sort((a, b) -> Long.compare(b.nanos(), a.nanos()));
                while (slowest.size() > KEEP_SLOWEST) slowest.remove(slowest.size() - 1);
            }
        }
    }

    public static long getTicks() { return ticks.get(); }
    public static long getTotalNanos() { return totalNanos.get(); }
    public static long getMaxNanos() { return maxNanos.get(); }
    public static long getSlowTickThresholdMs() { return SLOW_TICK_MS; }
    public static long[] getBucketLimitsMs() { return BUCKET_LIMITS_MS.clone(); }
    public static StackSampler.Profile getSlowTickProfile() { return slowTickProfile; }

    public static long[] getBuckets() {
        long[] out = new long[buckets.length];
        for (int i = 0; i < buckets.length; i++) out[i] = buckets[i].get();
        return out;
    }

    public static List<SlowTick> getSlowest() {
        synchronized (slowest) {
            return new ArrayList<>(slowest);
        }
    }

    public record SlowTick(long timestampMs, long nanos, StackSampler.Profile profile) {
    }
}
