package com.breakinblocks.whysoslow.profiler;

import net.minecraft.resources.Identifier;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ReportWriter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SEPARATOR = "=".repeat(90);
    private static final String THIN_SEP = "-".repeat(90);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void writeStartupReport(Path gameDir) {
        Path reportFile = gameDir.resolve("logs/whysoslow/startup.log");
        try {
            Files.createDirectories(reportFile.getParent());
        } catch (IOException e) {
            LOGGER.error("Failed to create report directory", e);
            return;
        }

        Map<String, StartupProfiler.ModStartupData> data = StartupProfiler.getModData();
        Map<String, Map<String, Integer>> registryCounts = StartupProfiler.countRegistryEntries();

        List<StartupProfiler.ModStartupData> sorted = data.values().stream()
                .sorted(Comparator.comparingLong(StartupProfiler.ModStartupData::getTotalWallNanos).reversed())
                .toList();

        Map<String, Long> phaseTotals = new LinkedHashMap<>();
        for (StartupProfiler.ModStartupData mod : sorted) {
            for (Map.Entry<String, StartupProfiler.PhaseData> entry : mod.getPhases().entrySet()) {
                phaseTotals.merge(entry.getKey(), entry.getValue().wallNanos.get(), Long::sum);
            }
        }

        long totalStartupNanos = sorted.stream().mapToLong(StartupProfiler.ModStartupData::getTotalWallNanos).sum();
        long jvmToFirstEvent = StartupProfiler.getFirstEventTimeMs() - StartupProfiler.getJvmStartMs();
        long firstToLastEvent = StartupProfiler.getLastEventTimeMs() - StartupProfiler.getFirstEventTimeMs();

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(reportFile))) {
            w.println(SEPARATOR);
            w.println("  WhySoSlow - Startup Performance Report");
            w.println("  Generated: " + LocalDateTime.now().format(TIME_FMT));
            w.printf("  JVM start to first mod event:  %s%n", formatMs(jvmToFirstEvent));
            w.printf("  Mod event processing time:     %s%n", formatNanos(totalStartupNanos));
            w.printf("  Wall clock (first to last):    %s%n", formatMs(firstToLastEvent));
            w.printf("  Total mods profiled:           %d%n", StartupProfiler.getModCount());
            w.println(SEPARATOR);

            w.println();
            w.println("PHASE BREAKDOWN:");
            w.println(THIN_SEP);
            List<Map.Entry<String, Long>> sortedPhases = phaseTotals.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .toList();
            for (Map.Entry<String, Long> entry : sortedPhases) {
                w.printf("  %-30s %s%n", entry.getKey() + ":", formatNanos(entry.getValue()));
            }

            List<StartupProfiler.FrameworkGap> gaps = StartupProfiler.getFrameworkGaps();
            if (!gaps.isEmpty()) {
                w.println();
                w.println("FRAMEWORK WORK BETWEEN MOD LISTENERS (time no mod listener was running, sampled):");
                w.println(THIN_SEP);
                w.println("  This is loader or game work that the per-mod numbers below do not include,");
                w.println("  such as registry freezing and the block state cache rebuild.");
                for (StartupProfiler.FrameworkGap gap : gaps) {
                    w.println();
                    w.printf("  %s busy (%s wall) on %s%n", formatMs(gap.profile().runnableMillis()),
                            formatNanos(gap.wallNanos()), gap.threadName());
                    w.printf("    after:  %s%n", gap.afterLabel());
                    w.printf("    before: %s%n", gap.beforeLabel());
                    writeProfile(w, gap.profile(), "    ", 3);
                }
            }

            w.println();
            w.println("ALL MODS (slowest to fastest by total wall time):");
            w.println(SEPARATOR);

            int rank = 0;
            for (StartupProfiler.ModStartupData mod : sorted) {
                rank++;
                long totalWall = mod.getTotalWallNanos();
                long totalCpu = mod.getTotalCpuNanos();
                long totalMem = mod.getTotalMemDelta();

                w.println();
                w.printf("#%-3d  %s  -  %s total  |  CPU: %s  |  Mem: %s%n",
                        rank, mod.getModId(), formatNanos(totalWall), formatNanos(totalCpu), formatBytes(totalMem));

                List<Map.Entry<String, StartupProfiler.PhaseData>> phases = mod.getPhases().entrySet().stream()
                        .sorted(Comparator.comparingLong((Map.Entry<String, StartupProfiler.PhaseData> e) ->
                                e.getValue().wallNanos.get()).reversed())
                        .toList();

                for (int i = 0; i < phases.size(); i++) {
                    Map.Entry<String, StartupProfiler.PhaseData> pe = phases.get(i);
                    StartupProfiler.PhaseData pd = pe.getValue();
                    String connector = (i == phases.size() - 1) ? "    \\-- " : "    |-- ";
                    w.printf("%s%-22s %8s  |  CPU: %8s  |  Mem: %s%n",
                            connector, pe.getKey() + ":",
                            formatNanos(pd.wallNanos.get()),
                            formatNanos(pd.cpuNanos.get()),
                            formatBytes(pd.memDelta.get()));
                }

                for (Map.Entry<String, StackSampler.Profile> hot : mod.getHotSpots().entrySet()) {
                    w.printf("    Hot spots in %s (%s sampled):%n", hot.getKey(), formatMs(hot.getValue().sampledMillis()));
                    writeProfile(w, hot.getValue(), "      ", 2);
                }

                Map<String, Integer> regCounts = registryCounts.get(mod.getModId());
                if (regCounts != null && !regCounts.isEmpty()) {
                    w.print("    Registered: ");
                    w.println(regCounts.entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .map(e -> e.getValue() + " " + e.getKey())
                            .collect(Collectors.joining(", ")));
                }
            }

            w.println();
            w.println(SEPARATOR);
            w.println("  End of startup report");
            w.println(SEPARATOR);
            w.flush();
        } catch (IOException e) {
            LOGGER.error("Failed to write startup report", e);
        }

        LOGGER.info("Startup report written to {}", reportFile);
    }

    public static void writeWorldLoadReport(Path gameDir) {
        Path reportFile = gameDir.resolve("logs/whysoslow/worldload.log");
        try {
            Files.createDirectories(reportFile.getParent());
        } catch (IOException e) {
            LOGGER.error("Failed to create report directory", e);
            return;
        }

        long totalLoadNanos = WorldLoadProfiler.getWorldLoadEndNanos() - WorldLoadProfiler.getWorldLoadStartNanos();
        long createLevelsNanos = WorldLoadProfiler.getCreateLevelsEndNanos() - WorldLoadProfiler.getCreateLevelsStartNanos();
        long memStart = WorldLoadProfiler.getMemoryAtStart();
        long memEnd = WorldLoadProfiler.getMemoryAtEnd();

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(reportFile))) {
            w.println(SEPARATOR);
            w.println("  WhySoSlow - World Load Performance Report");
            w.println("  Generated: " + LocalDateTime.now().format(TIME_FMT));
            w.printf("  World: %s%n", WorldLoadProfiler.getWorldName());
            w.printf("  Total world load time:     %s%n", formatNanos(totalLoadNanos));
            w.printf("  createLevels() time:       %s%n", formatNanos(createLevelsNanos));
            w.printf("  Memory at start:           %s%n", formatBytes(memStart));
            w.printf("  Memory at end:             %s%n", formatBytes(memEnd));
            w.printf("  Memory delta:              %s%n", formatBytes(memEnd - memStart));
            w.println(SEPARATOR);

            Map<String, WorldLoadProfiler.DimensionLoadData> dimensions = WorldLoadProfiler.getDimensionData();
            if (!dimensions.isEmpty()) {
                w.println();
                w.println("DIMENSION CONSTRUCTION TIMES:");
                w.println(THIN_SEP);
                dimensions.entrySet().stream()
                        .sorted(Comparator.comparingLong((Map.Entry<String, WorldLoadProfiler.DimensionLoadData> e) ->
                                e.getValue().getElapsedNanos()).reversed())
                        .forEach(entry -> {
                            WorldLoadProfiler.DimensionLoadData d = entry.getValue();
                            w.printf("  %-40s %s  |  Mem: %s%n",
                                    entry.getKey(), formatNanos(d.getElapsedNanos()), formatBytes(d.getMemoryDelta()));
                        });
            }

            if (WorldLoadProfiler.getInitialSpawnNanos() > 0) {
                w.println();
                w.println("INITIAL SPAWN SEARCH:");
                w.println(THIN_SEP);
                w.printf("  %s in %s (vanilla generates the spawn area here, once per new world)%n",
                        formatNanos(WorldLoadProfiler.getInitialSpawnNanos()), WorldLoadProfiler.getInitialSpawnDimension());
                writePipeline(w, WorldLoadProfiler.getSpawnSession());
                WorldGenProfiler.Capture capture = WorldLoadProfiler.getSpawnCapture();
                if (capture != null) {
                    writeCaptureTop(w, "Slowest structure starts (layout and jigsaw assembly)", capture.structureStarts());
                    writeCaptureTop(w, "Slowest structure placement", capture.structures());
                    writeCaptureTop(w, "Slowest features during the spawn search", capture.features());
                    writeCaptureTop(w, "Slowest carvers during the spawn search", capture.carvers());
                }
            }

            List<WorldLoadProfiler.LoadMilestone> milestones = WorldLoadProfiler.getMilestones();
            if (!milestones.isEmpty()) {
                w.println();
                w.println("LOADING TIMELINE:");
                w.println(THIN_SEP);
                long baseNano = WorldLoadProfiler.getWorldLoadStartNanos();
                for (WorldLoadProfiler.LoadMilestone m : milestones) {
                    long offsetNanos = m.nanoTime - baseNano;
                    w.printf("  [+%s]  %s%n", formatNanos(offsetNanos), m.description);
                }
            }

            Map<String, WorldLoadProfiler.ModWorldLoadData> modData = WorldLoadProfiler.getModContributions();
            if (!modData.isEmpty()) {
                w.println();
                w.println("MOD CONTRIBUTIONS DURING WORLD LOAD (sorted by time):");
                w.println(SEPARATOR);

                List<Map.Entry<String, WorldLoadProfiler.ModWorldLoadData>> sorted = modData.entrySet().stream()
                        .sorted(Comparator.comparingLong((Map.Entry<String, WorldLoadProfiler.ModWorldLoadData> e) ->
                                e.getValue().totalNanos.get()).reversed())
                        .toList();

                int rank = 0;
                for (Map.Entry<String, WorldLoadProfiler.ModWorldLoadData> entry : sorted) {
                    rank++;
                    WorldLoadProfiler.ModWorldLoadData mod = entry.getValue();
                    w.println();
                    w.printf("#%-3d  %s  -  %s total%n", rank, entry.getKey(), formatNanos(mod.totalNanos.get()));

                    List<Map.Entry<String, Long>> activities = mod.activities.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .toList();

                    for (int i = 0; i < activities.size(); i++) {
                        Map.Entry<String, Long> act = activities.get(i);
                        String connector = (i == activities.size() - 1) ? "    \\-- " : "    |-- ";
                        w.printf("%s%-30s %s%n", connector, act.getKey() + ":", formatNanos(act.getValue()));
                    }
                }
            }

            w.println();
            w.println(SEPARATOR);
            w.println("  End of world load report");
            w.println(SEPARATOR);
            w.flush();
        } catch (IOException e) {
            LOGGER.error("Failed to write world load report", e);
        }

        LOGGER.info("World load report written to {}", reportFile);
    }

    public static void writeWorldGenReport(Path gameDir) {
        Path reportFile = gameDir.resolve("logs/whysoslow/worldgen.log");
        try {
            Files.createDirectories(reportFile.getParent());
        } catch (IOException e) {
            LOGGER.error("Failed to create report directory", e);
            return;
        }

        long durationMs = WorldGenProfiler.getProfilingEndMs() - WorldGenProfiler.getProfilingStartMs();
        long totalChunks = WorldGenProfiler.getTotalChunksProfiled();

        long noiseNanos = WorldGenProfiler.getTotalNoiseFillNanos();
        long biomeNanos = WorldGenProfiler.getTotalBiomeFillNanos();
        long surfaceNanos = WorldGenProfiler.getTotalSurfaceBuildNanos();
        long decoNanos = WorldGenProfiler.getTotalBiomeDecorationNanos();

        Map<Identifier, WorldGenProfiler.TimingEntry> features = WorldGenProfiler.getFeatureTimings();
        Map<Identifier, WorldGenProfiler.TimingEntry> structures = WorldGenProfiler.getStructureTimings();
        Map<Identifier, WorldGenProfiler.TimingEntry> carvers = WorldGenProfiler.getCarverTimings();

        long totalFeatureNanos = features.values().stream().mapToLong(WorldGenProfiler.TimingEntry::getTotalNanos).sum();
        long totalStructureNanos = structures.values().stream().mapToLong(WorldGenProfiler.TimingEntry::getTotalNanos).sum();
        long totalCarverNanos = carvers.values().stream().mapToLong(WorldGenProfiler.TimingEntry::getTotalNanos).sum();
        long grandTotal = noiseNanos + biomeNanos + surfaceNanos + totalFeatureNanos + totalStructureNanos + totalCarverNanos;

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(reportFile))) {
            w.println(SEPARATOR);
            w.println("  WhySoSlow - World Generation Performance Report");
            w.println("  Generated: " + LocalDateTime.now().format(TIME_FMT));
            w.printf("  Profiling duration:     %s%n", formatMs(durationMs));
            w.printf("  Chunks profiled:        %d%n", totalChunks);
            if (totalChunks > 0) {
                w.printf("  Avg decoration/chunk:   %s%n", formatNanos(decoNanos / Math.max(1, totalChunks)));
            }
            w.println(SEPARATOR);

            w.println();
            w.println("OVERALL CATEGORY BREAKDOWN:");
            w.println(THIN_SEP);
            if (grandTotal > 0) {
                printCategory(w, "Noise Generation", noiseNanos, grandTotal, WorldGenProfiler.getNoiseFillCount());
                printCategory(w, "Biome Generation", biomeNanos, grandTotal, WorldGenProfiler.getBiomeFillCount());
                printCategory(w, "Surface Building", surfaceNanos, grandTotal, WorldGenProfiler.getSurfaceBuildCount());
                printCategory(w, "Feature Placement", totalFeatureNanos, grandTotal, features.values().stream().mapToLong(WorldGenProfiler.TimingEntry::getCount).sum());
                printCategory(w, "Structure Generation", totalStructureNanos, grandTotal, structures.values().stream().mapToLong(WorldGenProfiler.TimingEntry::getCount).sum());
                printCategory(w, "Carvers", totalCarverNanos, grandTotal, carvers.values().stream().mapToLong(WorldGenProfiler.TimingEntry::getCount).sum());
            } else {
                w.println("  No worldgen data collected.");
            }

            ChunkPipelineProfiler.Session pipeline = WorldGenProfiler.getPipelineSession();
            if (pipeline != null) {
                w.println();
                w.println("CHUNK PIPELINE (synchronous time per generation step, all threads):");
                w.println(THIN_SEP);
                writePipeline(w, pipeline);
            }

            List<WorldGenProfiler.ThreadGroupCpu> cpu = WorldGenProfiler.getThreadCpu();
            if (!cpu.isEmpty()) {
                w.println();
                w.println("CPU TIME BY THREAD GROUP DURING PROFILING:");
                w.println(THIN_SEP);
                w.println("  Includes mod-owned worker pools that the per-feature numbers cannot see.");
                cpu.stream().limit(20).forEach(g ->
                        w.printf("  %-45s %10s  (%d thread%s)%n", g.group(), formatNanos(g.cpuNanos()),
                                g.threads(), g.threads() == 1 ? "" : "s"));
            }

            writeTicks(w);

            if (!features.isEmpty()) {
                w.println();
                w.println("ALL FEATURES (slowest to fastest by total time):");
                w.println(SEPARATOR);
                writeTimingEntries(w, features);

                w.println();
                w.println("FEATURES BY MOD:");
                w.println(SEPARATOR);
                writeGroupedByMod(w, features);
            }

            Map<Identifier, WorldGenProfiler.TimingEntry> structureStarts = WorldGenProfiler.getStructureStartTimings();
            if (!structureStarts.isEmpty()) {
                w.println();
                w.println("STRUCTURE STARTS (layout and jigsaw assembly, slowest to fastest by total time):");
                w.println(SEPARATOR);
                writeTimingEntries(w, structureStarts);
            }

            if (!structures.isEmpty()) {
                w.println();
                w.println("ALL STRUCTURES (block placement, slowest to fastest by total time):");
                w.println(SEPARATOR);
                writeTimingEntries(w, structures);
            }

            if (!carvers.isEmpty()) {
                w.println();
                w.println("ALL CARVERS (slowest to fastest by total time):");
                w.println(SEPARATOR);
                writeTimingEntries(w, carvers);
            }

            w.println();
            w.println(SEPARATOR);
            w.println("  End of worldgen report");
            w.println(SEPARATOR);
            w.flush();
        } catch (IOException e) {
            LOGGER.error("Failed to write worldgen report", e);
        }

        LOGGER.info("WorldGen report written to {}", reportFile);
    }

    public static void writeResourceReport(Path gameDir) {
        Path reportFile = gameDir.resolve("logs/whysoslow/resources.log");
        try {
            Files.createDirectories(reportFile.getParent());
        } catch (IOException e) {
            LOGGER.error("Failed to create report directory", e);
            return;
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(reportFile))) {
            w.println(SEPARATOR);
            w.println("  WhySoSlow - Resource Reload Report");
            w.println("  Generated: " + LocalDateTime.now().format(TIME_FMT));
            w.println("  Covers client resource reloads and server data pack reloads since launch.");
            w.println("  Listener times are task time summed across threads. Preparation runs in parallel,");
            w.println("  so a listener's prepare time can exceed the reload's wall time.");
            w.println(SEPARATOR);

            List<ResourceReloadProfiler.Atlas> atlases = ResourceReloadProfiler.getAtlases();
            if (!atlases.isEmpty()) {
                w.println();
                w.println("TEXTURE ATLASES (largest first):");
                w.println(THIN_SEP);
                for (ResourceReloadProfiler.Atlas atlas : atlases) {
                    w.printf("  %-50s %5dx%-5d  mip %d/%d  %5d sprites  ~%s%n", atlas.name(), atlas.width(),
                            atlas.height(), atlas.mipLevel(), Math.max(0, atlas.requestedMip()), atlas.sprites(),
                            formatBytes(atlas.estimatedBytes()));
                }
                for (ResourceReloadProfiler.Atlas atlas : atlases) {
                    if (atlas.limiters().isEmpty() || atlas.mipLevel() >= atlas.requestedMip()) continue;
                    w.println();
                    w.printf("  %s lost mipmaps (%d -> %d). Textures that limit it:%n", atlas.name(),
                            atlas.requestedMip(), atlas.mipLevel());
                    atlas.limiters().stream().limit(60).forEach(l ->
                            w.printf("    %-70s %4dx%-4d  allows mip %d%n", l.sprite(), l.width(), l.height(), l.limitedTo()));
                    if (atlas.limiters().size() > 60) {
                        w.printf("    ... and %d more%n", atlas.limiters().size() - 60);
                    }
                }
            }

            int index = 0;
            for (ResourceReloadProfiler.Reload reload : ResourceReloadProfiler.getReloads()) {
                index++;
                w.println();
                w.printf("RELOAD #%d at %s  -  %s total%n", index,
                        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(reload.timestampMs()),
                                java.time.ZoneId.systemDefault()).format(TIME_FMT), formatNanos(reload.totalNanos()));
                w.println(THIN_SEP);
                reload.listeners().stream().limit(30).forEach(l ->
                        w.printf("  %-60s prepare %10s  |  apply %10s%n", trim(l.name(), 60),
                                formatNanos(l.prepareNanos()), formatNanos(l.applyNanos())));
            }

            w.println();
            w.println(SEPARATOR);
            w.println("  End of resource reload report");
            w.println(SEPARATOR);
        } catch (IOException e) {
            LOGGER.error("Failed to write resource report", e);
        }
    }

    private static void writeTicks(PrintWriter w) {
        long ticks = TickProfiler.getTicks();
        if (ticks == 0) return;
        w.println();
        w.println("SERVER TICKS DURING PROFILING:");
        w.println(THIN_SEP);
        w.printf("  %d ticks, average %s, max %s%n", ticks, formatNanos(TickProfiler.getTotalNanos() / ticks),
                formatNanos(TickProfiler.getMaxNanos()));
        long[] limits = TickProfiler.getBucketLimitsMs();
        long[] buckets = TickProfiler.getBuckets();
        for (int i = 0; i < buckets.length; i++) {
            String label = i == 0 ? "< " + limits[0] + "ms"
                    : i == limits.length ? ">= " + limits[limits.length - 1] + "ms"
                    : limits[i - 1] + "-" + limits[i] + "ms";
            w.printf("    %-12s %d%n", label, buckets[i]);
        }
        StackSampler.Profile slow = TickProfiler.getSlowTickProfile();
        if (slow.samples() > 0) {
            w.println();
            w.printf("  Where the server thread was during ticks over %dms (%s sampled):%n",
                    TickProfiler.getSlowTickThresholdMs(), formatMs(slow.sampledMillis()));
            writeProfile(w, slow, "    ", 4);
            int rank = 0;
            for (TickProfiler.SlowTick tick : TickProfiler.getSlowest()) {
                rank++;
                w.println();
                w.printf("  Slow tick #%d: %s at %s%n", rank, formatNanos(tick.nanos()),
                        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(tick.timestampMs()),
                                java.time.ZoneId.systemDefault()).format(TIME_FMT));
                writeProfile(w, tick.profile(), "    ", 1);
            }
        }
    }

    private static void writePipeline(PrintWriter w, ChunkPipelineProfiler.Session session) {
        if (session == null) return;
        session.stages().forEach((stage, t) ->
                w.printf("  %-24s %10s total  |  avg %9s  |  max %9s  |  %d calls%n", stage,
                        formatNanos(t.totalNanos()), formatNanos((long) t.avgNanos()), formatNanos(t.maxNanos()), t.count()));
        ChunkPipelineProfiler.Timing noise = session.noise();
        ChunkPipelineProfiler.Timing biomes = session.biomes();
        if (noise.count() > 0) {
            w.printf("  %-24s %10s total  |  avg %9s  |  max %9s  |  %d chunks (async noise fill)%n", "noise fill",
                    formatNanos(noise.totalNanos()), formatNanos((long) noise.avgNanos()), formatNanos(noise.maxNanos()), noise.count());
        }
        if (biomes.count() > 0) {
            w.printf("  %-24s %10s total  |  avg %9s  |  max %9s  |  %d chunks (async biome fill)%n", "biome fill",
                    formatNanos(biomes.totalNanos()), formatNanos((long) biomes.avgNanos()), formatNanos(biomes.maxNanos()), biomes.count());
        }
        ChunkPipelineProfiler.LatencyHistogram latency = session.latency();
        if (latency.count() > 0) {
            w.printf("  Chunk latency, first step to fully generated (%d chunks): p50 %s  |  p90 %s  |  p99 %s  |  max %s%n",
                    latency.count(), formatNanos(latency.percentile(0.5)), formatNanos(latency.percentile(0.9)),
                    formatNanos(latency.percentile(0.99)), formatNanos(latency.percentile(1.0)));
        }
    }

    private static void writeProfile(PrintWriter w, StackSampler.Profile profile, String indent, int stacks) {
        long total = Math.max(1, profile.samples());
        String mods = profile.topMods(6).stream()
                .map(e -> String.format("%s %.0f%%", e.getKey(), e.getValue() * 100.0 / total))
                .collect(Collectors.joining(", "));
        w.printf("%sby mod: %s%n", indent, mods);
        String states = profile.states().stream()
                .map(e -> String.format("%s %.0f%%", e.getKey(), e.getValue() * 100.0 / total))
                .collect(Collectors.joining(", "));
        w.printf("%sthread state: %s%n", indent, states);
        w.printf("%shot methods:%n", indent);
        profile.topLeaves(5).forEach(e ->
                w.printf("%s  %5.1f%%  %s%n", indent, e.getValue() * 100.0 / total, e.getKey()));
        int rank = 0;
        for (Map.Entry<String, Long> stack : profile.topStacks(stacks)) {
            rank++;
            w.printf("%sstack #%d (%.1f%%):%n", indent, rank, stack.getValue() * 100.0 / total);
            for (String frame : stack.getKey().split("\n")) {
                w.printf("%s    %s%n", indent, frame);
            }
        }
    }

    private static String trim(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length - 3) + "...";
    }

    private static void printCategory(PrintWriter w, String name, long nanos, long total, long count) {
        double pct = total > 0 ? (double) nanos / total * 100.0 : 0;
        w.printf("  %-25s %6.1f%%   %s   (%d operations)%n", name + ":", pct, formatNanos(nanos), count);
    }

    private static void writeTimingEntries(PrintWriter w, Map<Identifier, WorldGenProfiler.TimingEntry> entries) {
        List<Map.Entry<Identifier, WorldGenProfiler.TimingEntry>> sorted = entries.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<Identifier, WorldGenProfiler.TimingEntry> e) ->
                        e.getValue().getTotalNanos()).reversed())
                .toList();

        int rank = 0;
        for (Map.Entry<Identifier, WorldGenProfiler.TimingEntry> entry : sorted) {
            rank++;
            WorldGenProfiler.TimingEntry t = entry.getValue();
            w.printf("  #%-4d %-50s %s total  |  avg %s  |  max %s  |  %d calls%n",
                    rank, entry.getKey(),
                    formatNanos(t.getTotalNanos()),
                    formatNanos((long) t.getAvgNanos()),
                    formatNanos(t.getMaxNanos()),
                    t.getCount());
        }
    }

    private static void writeGroupedByMod(PrintWriter w, Map<Identifier, WorldGenProfiler.TimingEntry> entries) {
        Map<String, List<Map.Entry<Identifier, WorldGenProfiler.TimingEntry>>> byMod = entries.entrySet().stream()
                .collect(Collectors.groupingBy(e -> e.getKey().getNamespace()));

        List<Map.Entry<String, List<Map.Entry<Identifier, WorldGenProfiler.TimingEntry>>>> sortedMods =
                byMod.entrySet().stream()
                        .sorted(Comparator.comparingLong((Map.Entry<String, List<Map.Entry<Identifier, WorldGenProfiler.TimingEntry>>> e) ->
                                e.getValue().stream().mapToLong(f -> f.getValue().getTotalNanos()).sum()).reversed())
                        .toList();

        int rank = 0;
        for (var modEntry : sortedMods) {
            rank++;
            String modId = modEntry.getKey();
            var modFeatures = modEntry.getValue();
            long modTotal = modFeatures.stream().mapToLong(e -> e.getValue().getTotalNanos()).sum();
            long modCalls = modFeatures.stream().mapToLong(e -> e.getValue().getCount()).sum();

            w.println();
            w.printf("#%-3d  %s  -  %s total across %d features (%d placements)%n",
                    rank, modId, formatNanos(modTotal), modFeatures.size(), modCalls);

            modFeatures.stream()
                    .sorted(Comparator.comparingLong((Map.Entry<Identifier, WorldGenProfiler.TimingEntry> e) ->
                            e.getValue().getTotalNanos()).reversed())
                    .forEach(featureEntry -> {
                        WorldGenProfiler.TimingEntry t = featureEntry.getValue();
                        w.printf("    |-- %-44s %s  (%d calls, avg %s)%n",
                                featureEntry.getKey().getPath(),
                                formatNanos(t.getTotalNanos()),
                                t.getCount(),
                                formatNanos((long) t.getAvgNanos()));
                    });
        }
    }

    private static String formatNanos(long nanos) {
        if (nanos < 0) return "-" + formatNanos(-nanos);
        if (nanos < 1_000) return nanos + "ns";
        if (nanos < 1_000_000) return String.format("%.1fus", nanos / 1_000.0);
        if (nanos < 1_000_000_000) return String.format("%.2fms", nanos / 1_000_000.0);
        return String.format("%.3fs", nanos / 1_000_000_000.0);
    }

    private static String formatMs(long ms) {
        if (ms < 1_000) return ms + "ms";
        if (ms < 60_000) return String.format("%.2fs", ms / 1000.0);
        long minutes = ms / 60_000;
        long seconds = (ms % 60_000) / 1000;
        return String.format("%dm %ds", minutes, seconds);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "-" + formatBytes(-bytes);
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static void writeCaptureTop(PrintWriter w, String title,
                                        Map<net.minecraft.resources.Identifier, WorldGenProfiler.TimingEntry> timings) {
        if (timings.isEmpty()) return;
        w.println();
        w.println("  " + title + ":");
        timings.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<net.minecraft.resources.Identifier, WorldGenProfiler.TimingEntry> e) ->
                        e.getValue().getTotalNanos()).reversed())
                .limit(8)
                .forEach(e -> w.printf("    %-50s %10s total  |  max %10s  |  %d calls%n", e.getKey(),
                        formatNanos(e.getValue().getTotalNanos()), formatNanos(e.getValue().getMaxNanos()),
                        e.getValue().getCount()));
    }
}
