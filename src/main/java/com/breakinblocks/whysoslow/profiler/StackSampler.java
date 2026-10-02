package com.breakinblocks.whysoslow.profiler;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class StackSampler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadMXBean THREAD_MX = ManagementFactory.getThreadMXBean();
    private static final long INTERVAL_MS = 20;
    private static final long MAX_SAMPLING_NANOS = 120_000_000_000L;
    private static final int MAX_DEPTH = 64;
    private static final int STACK_KEY_FRAMES = 8;

    private static final Set<String> FRAMEWORK_MODULES = Set.of(
            "minecraft", "neoforge", "fml_loader", "loader", "whysoslow", "mixinextras.neoforge",
            "org.spongepowered.mixin", "com.google.common", "com.google.gson", "datafixerupper",
            "brigadier", "authlib", "it.unimi.dsi.fastutil", "org.slf4j", "org.apache.logging.log4j.core",
            "org.apache.logging.log4j", "net.neoforged.bus", "bus", "coremods", "org.lwjgl", "org.lwjgl.glfw",
            "org.lwjgl.opengl", "blaze3d", "com.mojang.blaze3d");

    private static final Set<Watch> WATCHES = ConcurrentHashMap.newKeySet();
    private static volatile Thread samplerThread;

    private StackSampler() {
    }

    public static Watch begin(Thread thread, String label, long thresholdMs) {
        Watch watch = new Watch(thread, label, thresholdMs * 1_000_000L);
        WATCHES.add(watch);
        ensureRunning();
        return watch;
    }

    public static Profile end(Watch watch) {
        if (watch == null) return null;
        WATCHES.remove(watch);
        watch.endNanos = System.nanoTime();
        return watch.profile;
    }

    private static void ensureRunning() {
        if (samplerThread != null) return;
        synchronized (StackSampler.class) {
            if (samplerThread != null) return;
            Thread thread = new Thread(StackSampler::run, "whysoslow-sampler");
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY);
            samplerThread = thread;
            thread.start();
        }
    }

    private static void run() {
        while (true) {
            try {
                Thread.sleep(INTERVAL_MS);
                long now = System.nanoTime();
                for (Watch watch : WATCHES) {
                    long age = now - watch.startNanos;
                    if (age < watch.thresholdNanos) continue;
                    if (age - watch.thresholdNanos > MAX_SAMPLING_NANOS || !watch.thread.isAlive()) {
                        WATCHES.remove(watch);
                        continue;
                    }
                    ThreadInfo info = THREAD_MX.getThreadInfo(watch.thread.threadId(), MAX_DEPTH);
                    if (info == null) continue;
                    watch.profile.add(info, INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                LOGGER.debug("Stack sampler error", t);
            }
        }
    }

    public static String attribute(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            String module = frame.getModuleName();
            if (module == null) continue;
            if (module.startsWith("java.") || module.startsWith("jdk.")) continue;
            if (FRAMEWORK_MODULES.contains(module)) continue;
            return module;
        }
        for (StackTraceElement frame : stack) {
            String module = frame.getModuleName();
            if ("minecraft".equals(module) || "neoforge".equals(module)) return module;
        }
        return "(jdk/framework)";
    }

    public static String frameName(StackTraceElement frame) {
        String cls = frame.getClassName();
        int dot = cls.lastIndexOf('.');
        String simple = dot >= 0 ? cls.substring(dot + 1) : cls;
        String module = frame.getModuleName();
        return (module != null ? module + "/" : "") + simple + "." + frame.getMethodName();
    }

    public static final class Watch {
        private final Thread thread;
        private final String label;
        private final long thresholdNanos;
        private final long startNanos = System.nanoTime();
        private volatile long endNanos;
        private final Profile profile = new Profile();

        private Watch(Thread thread, String label, long thresholdNanos) {
            this.thread = thread;
            this.label = label;
            this.thresholdNanos = thresholdNanos;
        }

        public String label() {
            return label;
        }

        public long elapsedNanos() {
            return (endNanos > 0 ? endNanos : System.nanoTime()) - startNanos;
        }
    }

    public static final class Profile {
        private final AtomicLong samples = new AtomicLong();
        private final AtomicLong runnableSamples = new AtomicLong();
        private final Map<String, AtomicLong> byMod = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> byLeaf = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> byStack = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> byState = new ConcurrentHashMap<>();

        void add(ThreadInfo info, long intervalMs) {
            StackTraceElement[] stack = info.getStackTrace();
            if (stack.length == 0) return;
            samples.incrementAndGet();
            Thread.State state = info.getThreadState();
            byState.computeIfAbsent(state.name(), k -> new AtomicLong()).incrementAndGet();
            if (state == Thread.State.RUNNABLE) runnableSamples.incrementAndGet();
            byMod.computeIfAbsent(attribute(stack), k -> new AtomicLong()).incrementAndGet();
            byLeaf.computeIfAbsent(frameName(stack[0]), k -> new AtomicLong()).incrementAndGet();
            StringBuilder key = new StringBuilder();
            for (int i = 0; i < Math.min(STACK_KEY_FRAMES, stack.length); i++) {
                if (i > 0) key.append("\n");
                key.append(frameName(stack[i]));
            }
            byStack.computeIfAbsent(key.toString(), k -> new AtomicLong()).incrementAndGet();
        }

        public void reset() {
            samples.set(0);
            runnableSamples.set(0);
            byMod.clear();
            byLeaf.clear();
            byStack.clear();
            byState.clear();
        }

        public void merge(Profile other) {
            if (other == null) return;
            samples.addAndGet(other.samples.get());
            runnableSamples.addAndGet(other.runnableSamples.get());
            mergeMap(byMod, other.byMod);
            mergeMap(byLeaf, other.byLeaf);
            mergeMap(byStack, other.byStack);
            mergeMap(byState, other.byState);
        }

        private static void mergeMap(Map<String, AtomicLong> into, Map<String, AtomicLong> from) {
            from.forEach((k, v) -> into.computeIfAbsent(k, x -> new AtomicLong()).addAndGet(v.get()));
        }

        public long samples() {
            return samples.get();
        }

        public long runnableSamples() {
            return runnableSamples.get();
        }

        public long sampledMillis() {
            return samples.get() * INTERVAL_MS;
        }

        public long runnableMillis() {
            return runnableSamples.get() * INTERVAL_MS;
        }

        public static long intervalMillis() {
            return INTERVAL_MS;
        }

        public List<Map.Entry<String, Long>> topMods(int limit) {
            return top(byMod, limit);
        }

        public List<Map.Entry<String, Long>> topLeaves(int limit) {
            return top(byLeaf, limit);
        }

        public List<Map.Entry<String, Long>> topStacks(int limit) {
            return top(byStack, limit);
        }

        public List<Map.Entry<String, Long>> states() {
            return top(byState, 10);
        }

        private static List<Map.Entry<String, Long>> top(Map<String, AtomicLong> map, int limit) {
            Map<String, Long> copy = new HashMap<>();
            map.forEach((k, v) -> copy.put(k, v.get()));
            List<Map.Entry<String, Long>> list = new ArrayList<>(copy.entrySet());
            list.sort(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()));
            return list.subList(0, Math.min(limit, list.size()));
        }
    }
}
