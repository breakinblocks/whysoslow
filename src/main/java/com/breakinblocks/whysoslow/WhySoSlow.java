package com.breakinblocks.whysoslow;

import com.breakinblocks.whysoslow.profiler.ReportWriter;
import com.breakinblocks.whysoslow.profiler.StartupProfiler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

@Mod(WhySoSlow.MOD_ID)
public class WhySoSlow {
    public static final String MOD_ID = "whysoslow";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WhySoSlow(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("WhySoSlow initializing - performance profiling is active");

        modEventBus.addListener(this::onLoadComplete);

        NeoForge.EVENT_BUS.register(com.breakinblocks.whysoslow.events.WhySoSlowEventHandler.class);
        NeoForge.EVENT_BUS.register(com.breakinblocks.whysoslow.commands.WhySoSlowCommand.class);

        installTimingListeners();
    }

    private void installTimingListeners() {
        int registered = 0;
        for (var modInfo : ModList.get().getMods()) {
            String modId = modInfo.getModId();
            var optional = ModList.get().getModContainerById(modId);
            if (optional.isEmpty()) continue;

            ModContainer container = optional.get();
            IEventBus bus = container.getEventBus();
            if (bus == null) continue;

            registerTimingPair(bus, modId, FMLConstructModEvent.class);
            registerTimingPair(bus, modId, RegisterEvent.class);
            registerTimingPair(bus, modId, FMLCommonSetupEvent.class);
            registerTimingPair(bus, modId, FMLLoadCompleteEvent.class);
            registered++;
        }
        LOGGER.info("Installed timing listeners on {} mod event buses", registered);
    }

    private static <T extends Event> void registerTimingPair(IEventBus bus, String modId, Class<T> eventClass) {
        bus.addListener(EventPriority.HIGHEST, false, eventClass, event ->
                StartupProfiler.onModEventStart(modId, event));
        bus.addListener(EventPriority.LOWEST, false, eventClass, event ->
                StartupProfiler.onModEventEnd(modId, event));
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            StartupProfiler.markComplete();
            ReportWriter.writeStartupReport(FMLPaths.GAMEDIR.get());
        });
    }
}
