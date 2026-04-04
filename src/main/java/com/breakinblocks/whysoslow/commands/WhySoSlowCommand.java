package com.breakinblocks.whysoslow.commands;

import com.breakinblocks.whysoslow.profiler.ReportWriter;
import com.breakinblocks.whysoslow.profiler.WorldGenProfiler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;

public class WhySoSlowCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("whysoslow")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(context -> startProfiling(context.getSource()))
                )
                .then(Commands.literal("stop")
                        .executes(context -> stopProfiling(context.getSource()))
                )
                .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource()))
                )
        );
    }

    private static int startProfiling(CommandSourceStack source) {
        if (WorldGenProfiler.isActive()) {
            source.sendFailure(Component.literal("WorldGen profiling is already active!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        WorldGenProfiler.start(source.getServer());

        source.sendSuccess(() -> Component.literal("WorldGen profiling started!")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("\nExplore the world to generate chunks and collect data.")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\nRun ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/whysoslow stop")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" when done.")
                        .withStyle(ChatFormatting.GRAY)), true);

        return 1;
    }

    private static int stopProfiling(CommandSourceStack source) {
        if (!WorldGenProfiler.isActive()) {
            source.sendFailure(Component.literal("WorldGen profiling is not currently active.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        WorldGenProfiler.stop();

        if (!WorldGenProfiler.hasData()) {
            source.sendSuccess(() -> Component.literal("WorldGen profiling stopped but no data was collected.")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("\nTry exploring more chunks before stopping next time.")
                            .withStyle(ChatFormatting.GRAY)), true);
            return 0;
        }

        ReportWriter.writeWorldGenReport(FMLPaths.GAMEDIR.get());

        long chunks = WorldGenProfiler.getTotalChunksProfiled();
        long features = WorldGenProfiler.getFeatureTimings().size();
        long structures = WorldGenProfiler.getStructureTimings().size();

        source.sendSuccess(() -> Component.literal("WorldGen profiling stopped!")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(String.format(
                                "\nProfiled %d chunks, %d unique features, %d unique structures.",
                                chunks, features, structures))
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\nReport written to: ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("logs/whysoslow/worldgen.log")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true))), true);

        return 1;
    }

    private static int showStatus(CommandSourceStack source) {
        if (WorldGenProfiler.isActive()) {
            long chunks = WorldGenProfiler.getTotalChunksProfiled();
            long durationMs = System.currentTimeMillis() - WorldGenProfiler.getProfilingStartMs();
            long durationSec = durationMs / 1000;

            source.sendSuccess(() -> Component.literal("WorldGen profiling is ACTIVE")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(String.format(
                                    "\nRunning for: %dm %ds", durationSec / 60, durationSec % 60))
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format(
                                    "\nChunks profiled: %d", chunks))
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format(
                                    "\nUnique features tracked: %d", WorldGenProfiler.getFeatureTimings().size()))
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format(
                                    "\nUnique structures tracked: %d", WorldGenProfiler.getStructureTimings().size()))
                            .withStyle(ChatFormatting.GRAY)), false);
        } else {
            source.sendSuccess(() -> Component.literal("WorldGen profiling is INACTIVE")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("\nRun /whysoslow start to begin profiling.")
                            .withStyle(ChatFormatting.GRAY)), false);
        }

        return 1;
    }
}
