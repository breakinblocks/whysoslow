package com.breakinblocks.whysoslow.profiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public final class ResourceReloadProfiler {
    private static final List<Reload> reloads = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, Atlas> atlases = new ConcurrentHashMap<>();

    private ResourceReloadProfiler() {
    }

    public static void recordReload(long totalNanos, List<ListenerTiming> listeners) {
        List<ListenerTiming> sorted = new ArrayList<>(listeners);
        sorted.sort((a, b) -> Long.compare(b.prepareNanos() + b.applyNanos(), a.prepareNanos() + a.applyNanos()));
        reloads.add(new Reload(System.currentTimeMillis(), totalNanos, sorted));
        ReportWriter.writeResourceReport(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get());
    }

    public static Tracker newTracker() {
        return new Tracker();
    }

    public static void recordAtlasLimiters(String atlas, int requestedMip, List<MipLimiter> limiters) {
        atlases.computeIfAbsent(atlas, Atlas::new).setLimiters(requestedMip, limiters);
    }

    public static void recordAtlasResult(String atlas, int width, int height, int mipLevel, int sprites) {
        atlases.computeIfAbsent(atlas, Atlas::new).setResult(width, height, mipLevel, sprites);
    }

    public static List<Reload> getReloads() {
        synchronized (reloads) {
            return new ArrayList<>(reloads);
        }
    }

    public static List<Atlas> getAtlases() {
        List<Atlas> list = new ArrayList<>(atlases.values());
        list.sort((a, b) -> Long.compare((long) b.width * b.height, (long) a.width * a.height));
        return list;
    }

    public static final class Tracker {
        private final long startNanos = System.nanoTime();
        private final Map<String, AtomicLong[]> timings = Collections.synchronizedMap(new java.util.LinkedHashMap<>());

        private Tracker() {
        }

        public Executor prepareExecutor(String name, Executor executor) {
            return timed(slot(name)[0], executor);
        }

        public Executor applyExecutor(String name, Executor executor) {
            return timed(slot(name)[1], executor);
        }

        public void finish(boolean success) {
            if (!success) return;
            try {
                List<ListenerTiming> list = new ArrayList<>();
                synchronized (timings) {
                    timings.forEach((name, slot) -> list.add(new ListenerTiming(name, slot[0].get(), slot[1].get())));
                }
                recordReload(System.nanoTime() - startNanos, list);
            } catch (Throwable t) {
                com.mojang.logging.LogUtils.getLogger().warn("Failed to record resource reload timings", t);
            }
        }

        private AtomicLong[] slot(String name) {
            return timings.computeIfAbsent(name, k -> new AtomicLong[] {new AtomicLong(), new AtomicLong()});
        }

        private static Executor timed(AtomicLong accumulator, Executor executor) {
            return task -> executor.execute(() -> {
                long start = System.nanoTime();
                try {
                    task.run();
                } finally {
                    accumulator.addAndGet(System.nanoTime() - start);
                }
            });
        }
    }

    public record ListenerTiming(String name, long prepareNanos, long applyNanos) {
    }

    public record Reload(long timestampMs, long totalNanos, List<ListenerTiming> listeners) {
    }

    public record MipLimiter(String sprite, int width, int height, int limitedTo) {
    }

    public static final class Atlas {
        private final String name;
        private volatile int width;
        private volatile int height;
        private volatile int mipLevel;
        private volatile int requestedMip = -1;
        private volatile int sprites;
        private volatile List<MipLimiter> limiters = List.of();

        private Atlas(String name) {
            this.name = name;
        }

        private void setLimiters(int requestedMip, List<MipLimiter> limiters) {
            this.requestedMip = requestedMip;
            this.limiters = List.copyOf(limiters);
        }

        private void setResult(int width, int height, int mipLevel, int sprites) {
            this.width = width;
            this.height = height;
            this.mipLevel = mipLevel;
            this.sprites = sprites;
        }

        public String name() { return name; }
        public int width() { return width; }
        public int height() { return height; }
        public int mipLevel() { return mipLevel; }
        public int requestedMip() { return requestedMip; }
        public int sprites() { return sprites; }
        public List<MipLimiter> limiters() { return limiters; }

        public long estimatedBytes() {
            long base = (long) width * height * 4L;
            long total = base;
            for (int level = 1; level <= mipLevel; level++) {
                total += base >> (2 * level);
            }
            return total;
        }
    }
}
