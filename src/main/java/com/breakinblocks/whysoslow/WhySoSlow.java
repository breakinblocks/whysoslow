package com.breakinblocks.whysoslow;

import com.breakinblocks.whysoslow.commands.WhySoSlowCommand;
import com.breakinblocks.whysoslow.events.WhySoSlowEventHandler;
import com.breakinblocks.whysoslow.profiler.ReportWriter;
import com.breakinblocks.whysoslow.profiler.StartupProfiler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.*;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

@Mod(WhySoSlow.MOD_ID)
public class WhySoSlow {
    public static final String MOD_ID = "whysoslow";
    public static final Logger LOGGER = LogManager.getLogger();

    public static WhySoSlow instance;

    public WhySoSlow() {
        instance = this;
        LOGGER.info("WhySoSlow initializing - performance profiling is active");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onLoadComplete);

        MinecraftForge.EVENT_BUS.register(WhySoSlowEventHandler.class);
        MinecraftForge.EVENT_BUS.register(WhySoSlowCommand.class);

        installTimingListeners();
    }

    private void installTimingListeners() {
        int registered = 0;
        for (var modInfo : ModList.get().getMods()) {
            String modId = modInfo.getModId();
            var optional = ModList.get().getModContainerById(modId);
            if (optional.isEmpty()) continue;

            ModContainer container = optional.get();
            IEventBus bus = extractEventBus(container);
            if (bus == null) continue;

            registerTimingPair(bus, modId, FMLConstructModEvent.class);
            registerTimingPair(bus, modId, RegisterEvent.class);
            registerTimingPair(bus, modId, FMLCommonSetupEvent.class);
            registerTimingPair(bus, modId, FMLClientSetupEvent.class);
            registerTimingPair(bus, modId, FMLLoadCompleteEvent.class);
            registerTimingPair(bus, modId, InterModEnqueueEvent.class);
            registerTimingPair(bus, modId, InterModProcessEvent.class);
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

    private static IEventBus extractEventBus(ModContainer container) {
        try {
            Field busField = container.getClass().getDeclaredField("eventBus");
            busField.setAccessible(true);
            Object bus = busField.get(container);
            return bus instanceof IEventBus eventBus ? eventBus : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            StartupProfiler.markComplete();
            ReportWriter.writeStartupReport(FMLPaths.GAMEDIR.get());
        });
    }
}
