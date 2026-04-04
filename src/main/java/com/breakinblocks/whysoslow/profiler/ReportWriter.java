package com.breakinblocks.whysoslow.profiler;

import net.minecraft.resources.ResourceLocation;
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
                w.println("DIMENSION LOAD TIMES:");
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
        long surfaceNanos = WorldGenProfiler.getTotalSurfaceBuildNanos();
        long decoNanos = WorldGenProfiler.getTotalBiomeDecorationNanos();

        Map<ResourceLocation, WorldGenProfiler.TimingEntry> features = WorldGenProfiler.getFeatureTimings();
        Map<ResourceLocation, WorldGenProfiler.TimingEntry> structures = WorldGenProfiler.getStructureTimings();
        Map<ResourceLocation, WorldGenProfiler.TimingEntry> carvers = WorldGenProfiler.getCarverTimings();

        long totalFeatureNanos = features.values().stream().mapToLong(WorldGenProfiler.TimingEntry::getTotalNanos).sum();
        long totalStructureNanos = structures.values().stream().mapToLong(WorldGenProfiler.TimingEntry::getTotalNanos).sum();
        long totalCarverNanos = carvers.values().stream().mapToLong(WorldGenProfiler.TimingEntry::getTotalNanos).sum();
        long grandTotal = noiseNanos + surfaceNanos + totalFeatureNanos + totalStructureNanos + totalCarverNanos;

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
                printCategory(w, "Surface Building", surfaceNanos, grandTotal, WorldGenProfiler.getSurfaceBuildCount());
                printCategory(w, "Feature Placement", totalFeatureNanos, grandTotal, features.values().stream().mapToLong(WorldGenProfiler.TimingEntry::getCount).sum());
                printCategory(w, "Structure Generation", totalStructureNanos, grandTotal, structures.values().stream().mapToLong(WorldGenProfiler.TimingEntry::getCount).sum());
                printCategory(w, "Carvers", totalCarverNanos, grandTotal, carvers.values().stream().mapToLong(WorldGenProfiler.TimingEntry::getCount).sum());
            } else {
                w.println("  No worldgen data collected.");
            }

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

            if (!structures.isEmpty()) {
                w.println();
                w.println("ALL STRUCTURES (slowest to fastest by total time):");
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

    private static void printCategory(PrintWriter w, String name, long nanos, long total, long count) {
        double pct = total > 0 ? (double) nanos / total * 100.0 : 0;
        w.printf("  %-25s %6.1f%%   %s   (%d operations)%n", name + ":", pct, formatNanos(nanos), count);
    }

    private static void writeTimingEntries(PrintWriter w, Map<ResourceLocation, WorldGenProfiler.TimingEntry> entries) {
        List<Map.Entry<ResourceLocation, WorldGenProfiler.TimingEntry>> sorted = entries.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<ResourceLocation, WorldGenProfiler.TimingEntry> e) ->
                        e.getValue().getTotalNanos()).reversed())
                .toList();

        int rank = 0;
        for (Map.Entry<ResourceLocation, WorldGenProfiler.TimingEntry> entry : sorted) {
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

    private static void writeGroupedByMod(PrintWriter w, Map<ResourceLocation, WorldGenProfiler.TimingEntry> entries) {
        Map<String, List<Map.Entry<ResourceLocation, WorldGenProfiler.TimingEntry>>> byMod = entries.entrySet().stream()
                .collect(Collectors.groupingBy(e -> e.getKey().getNamespace()));

        List<Map.Entry<String, List<Map.Entry<ResourceLocation, WorldGenProfiler.TimingEntry>>>> sortedMods =
                byMod.entrySet().stream()
                        .sorted(Comparator.comparingLong((Map.Entry<String, List<Map.Entry<ResourceLocation, WorldGenProfiler.TimingEntry>>> e) ->
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
                    .sorted(Comparator.comparingLong((Map.Entry<ResourceLocation, WorldGenProfiler.TimingEntry> e) ->
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
}
