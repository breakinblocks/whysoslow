package com.breakinblocks.whysoslow.profiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ChunkPipelineProfiler {
    private static final AtomicReference<Session> current = new AtomicReference<>();
    private static final ThreadLocal<Long> stageStart = new ThreadLocal<>();
    private static final ThreadLocal<Long> noiseStart = new ThreadLocal<>();
    private static final ThreadLocal<Long> biomeStart = new ThreadLocal<>();

    private ChunkPipelineProfiler() {
    }

    public static Session begin(String name) {
        Session session = new Session(name);
        current.set(session);
        return session;
    }

    public static Session end(Session session) {
        current.compareAndSet(session, null);
        return session;
    }

    public static boolean isRecording() {
        return current.get() != null;
    }

    public static void onStageStart() {
        if (current.get() == null) return;
        stageStart.set(System.nanoTime());
    }

    public static void onStageEnd(String stage) {
        Session session = current.get();
        Long start = stageStart.get();
        if (session == null || start == null) return;
        stageStart.remove();
        session.stages.computeIfAbsent(stage, k -> new Timing()).record(System.nanoTime() - start);
    }

    public static void onChunkStarted(String dimension, long pos) {
        Session session = current.get();
        if (session == null) return;
        session.pending.putIfAbsent(dimension + "@" + pos, System.nanoTime());
    }

    public static void onChunkCompleted(String dimension, long pos) {
        Session session = current.get();
        if (session == null) return;
        Long start = session.pending.remove(dimension + "@" + pos);
        if (start != null) session.latency.record(System.nanoTime() - start);
    }

    public static void onNoiseStart() {
        if (current.get() != null) noiseStart.set(System.nanoTime());
    }

    public static void onNoiseEnd() {
        Session session = current.get();
        Long start = noiseStart.get();
        if (session == null || start == null) return;
        noiseStart.remove();
        session.noise.record(System.nanoTime() - start);
    }

    public static void onBiomesStart() {
        if (current.get() != null) biomeStart.set(System.nanoTime());
    }

    public static void onBiomesEnd() {
        Session session = current.get();
        Long start = biomeStart.get();
        if (session == null || start == null) return;
        biomeStart.remove();
        session.biomes.record(System.nanoTime() - start);
    }

    public static final class Session {
        private final String name;
        private final long startNanos = System.nanoTime();
        private final Map<String, Timing> stages = new ConcurrentHashMap<>();
        private final Map<String, Long> pending = new ConcurrentHashMap<>();
        private final Timing noise = new Timing();
        private final Timing biomes = new Timing();
        private final LatencyHistogram latency = new LatencyHistogram();

        private Session(String name) {
            this.name = name;
        }

        public String name() { return name; }
        public long startNanos() { return startNanos; }
        public Timing noise() { return noise; }
        public Timing biomes() { return biomes; }
        public LatencyHistogram latency() { return latency; }

        public Map<String, Timing> stages() {
            List<Map.Entry<String, Timing>> list = new ArrayList<>(stages.entrySet());
            list.sort((a, b) -> Long.compare(b.getValue().totalNanos(), a.getValue().totalNanos()));
            Map<String, Timing> ordered = new LinkedHashMap<>();
            list.forEach(e -> ordered.put(e.getKey(), e.getValue()));
            return Collections.unmodifiableMap(ordered);
        }
    }

    public static final class Timing {
        private final AtomicLong total = new AtomicLong();
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong max = new AtomicLong();

        void record(long nanos) {
            total.addAndGet(nanos);
            count.incrementAndGet();
            max.accumulateAndGet(nanos, Math::max);
        }

        public long totalNanos() { return total.get(); }
        public long count() { return count.get(); }
        public long maxNanos() { return max.get(); }
        public double avgNanos() { return count.get() > 0 ? (double) total.get() / count.get() : 0; }
    }

    public static final class LatencyHistogram {
        private final List<Long> samples = Collections.synchronizedList(new ArrayList<>());

        void record(long nanos) {
            samples.add(nanos);
        }

        public int count() {
            return samples.size();
        }

        public long percentile(double p) {
            long[] sorted;
            synchronized (samples) {
                sorted = samples.stream().mapToLong(Long::longValue).toArray();
            }
            if (sorted.length == 0) return 0;
            Arrays.sort(sorted);
            int index = (int) Math.min(sorted.length - 1, Math.round(p * (sorted.length - 1)));
            return sorted[index];
        }
    }
}
