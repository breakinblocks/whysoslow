package com.breakinblocks.whysoslow.profiler;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.ConsumerEventHandler;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ForgeBusContributionTracker {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, String> classToModId = new ConcurrentHashMap<>();
    private static boolean classMapBuilt = false;

    public static void wrapListeners(IEventBus bus, Class<? extends Event> eventClass, String phaseName) {
        try {
            buildClassMapIfNeeded();

            boolean isCancellable = ICancellableEvent.class.isAssignableFrom(eventClass);

            Field listenerListsField = bus.getClass().getDeclaredField("listenerLists");
            listenerListsField.setAccessible(true);
            Object lockHelper = listenerListsField.get(bus);

            Method getMethod = findMethod(lockHelper.getClass(), "get", Object.class);
            if (getMethod == null) return;
            getMethod.setAccessible(true);
            Object listenerList = getMethod.invoke(lockHelper, eventClass);
            if (listenerList == null) return;

            Field prioritiesField = listenerList.getClass().getDeclaredField("priorities");
            prioritiesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<ArrayList<EventListener>> priorities = (ArrayList<ArrayList<EventListener>>) prioritiesField.get(listenerList);

            for (ArrayList<EventListener> priorityList : priorities) {
                if (priorityList == null) continue;
                for (int i = 0; i < priorityList.size(); i++) {
                    EventListener original = priorityList.get(i);
                    if (isAlreadyWrapped(original)) continue;

                    String modId = determineModId(original);
                    EventListener delegate = maybeUnwrap(original, isCancellable);
                    priorityList.set(i, createTimingWrapper(delegate, modId, phaseName));
                }
            }

            Field rebuildField = listenerList.getClass().getDeclaredField("rebuild");
            rebuildField.setAccessible(true);
            rebuildField.setBoolean(listenerList, true);
        } catch (Throwable t) {
            LOGGER.warn("Failed to wrap listeners for {}: {}", eventClass.getSimpleName(), t.toString());
        }
    }

    private static EventListener maybeUnwrap(EventListener listener, boolean isCancellable) {
        if (isCancellable) return listener;
        EventListener current = listener;
        for (int i = 0; i < 5; i++) {
            EventListener unwrapped = tryUnwrapOneLevel(current);
            if (unwrapped == null || unwrapped == current) return current;
            current = unwrapped;
        }
        return current;
    }

    private static EventListener tryUnwrapOneLevel(EventListener listener) {
        try {
            Method getWithoutCheck = findMethod(listener.getClass(), "getWithoutCheck");
            if (getWithoutCheck != null) {
                getWithoutCheck.setAccessible(true);
                Object result = getWithoutCheck.invoke(listener);
                if (result instanceof EventListener el) return el;
            }
        } catch (Throwable ignored) {
        }
        try {
            Field handlerField = findField(listener.getClass(), "handler");
            if (handlerField != null) {
                handlerField.setAccessible(true);
                Object inner = handlerField.get(listener);
                if (inner instanceof EventListener el) return el;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("[a-zA-Z_$][a-zA-Z_$0-9]*(\\.[a-zA-Z_$][a-zA-Z_$0-9]*)+");

    private static String determineModId(EventListener listener) {
        String fromReadable = tryReadableField(listener);
        if (fromReadable != null) return fromReadable;

        Object handler = tryUnwrapField(listener, "handler");
        if (handler != null) {
            String fromHandler = classNameToModId(stripLambdaSuffix(handler.getClass().getName()));
            if (fromHandler != null) return fromHandler;
            String fromHandlerTarget = tryTargetField(handler);
            if (fromHandlerTarget != null) return fromHandlerTarget;
        }

        Object consumer = tryUnwrapField(listener, "consumer");
        if (consumer != null) {
            String fromConsumer = classNameToModId(stripLambdaSuffix(consumer.getClass().getName()));
            if (fromConsumer != null) return fromConsumer;
        }

        Object target = tryUnwrapFieldRaw(listener, "target");
        if (target != null) {
            String targetClassName = (target instanceof Class<?> c) ? c.getName() : target.getClass().getName();
            String fromTarget = classNameToModId(stripLambdaSuffix(targetClassName));
            if (fromTarget != null) return fromTarget;
        }

        return "unknown";
    }

    private static String stripLambdaSuffix(String className) {
        if (className == null) return null;
        int lambdaIdx = className.indexOf("$$Lambda");
        if (lambdaIdx >= 0) return className.substring(0, lambdaIdx);
        int slashIdx = className.indexOf('/');
        if (slashIdx >= 0) className = className.substring(0, slashIdx);
        return className;
    }

    private static String tryReadableField(EventListener listener) {
        try {
            Field readableField = findField(listener.getClass(), "readable");
            if (readableField == null) return null;
            readableField.setAccessible(true);
            Object val = readableField.get(listener);
            if (!(val instanceof String readable)) return null;

            String stripped = readable;
            if (stripped.startsWith("@SubscribeEvent:")) {
                stripped = stripped.substring("@SubscribeEvent:".length()).trim();
            }
            if (stripped.startsWith("class ")) {
                stripped = stripped.substring(6).trim();
            }

            Matcher m = CLASS_NAME_PATTERN.matcher(stripped);
            while (m.find()) {
                String candidate = m.group();
                if (candidate.startsWith("net.neoforged.neoforge.event") ||
                    candidate.startsWith("net.minecraft.")) continue;
                String modId = classNameToModId(candidate);
                if (modId != null) return modId;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object tryUnwrapFieldRaw(Object obj, String fieldName) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String tryTargetField(Object obj) {
        Object target = tryUnwrapFieldRaw(obj, "target");
        if (target == null) return null;
        String targetClassName = (target instanceof Class<?> c) ? c.getName() : target.getClass().getName();
        return classNameToModId(stripLambdaSuffix(targetClassName));
    }

    private static Object tryUnwrapField(Object obj, String fieldName) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String classNameToModId(String className) {
        if (className == null || className.isEmpty()) return null;

        if (className.startsWith("net.neoforged.bus.") ||
            className.startsWith("net.neoforged.fml.")) return null;

        String direct = classToModId.get(className);
        if (direct != null) return direct;

        String probe = className;
        while (true) {
            int dollar = probe.indexOf('$');
            if (dollar < 0) break;
            probe = probe.substring(0, dollar);
            String match = classToModId.get(probe);
            if (match != null) return match;
        }

        int lastDot = className.lastIndexOf('.');
        while (lastDot > 0) {
            String prefix = className.substring(0, lastDot + 1);
            for (var entry : classToModId.entrySet()) {
                if (entry.getKey().startsWith(prefix)) return entry.getValue();
            }
            lastDot = className.lastIndexOf('.', lastDot - 1);
        }

        return null;
    }

    private static Field findField(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static void buildClassMapIfNeeded() {
        if (classMapBuilt) return;
        synchronized (classToModId) {
            if (classMapBuilt) return;
            try {
                for (var modInfo : ModList.get().getMods()) {
                    String modId = modInfo.getModId();
                    var file = modInfo.getOwningFile();
                    if (file == null) continue;
                    try {
                        var scanData = file.getFile().getScanResult();
                        if (scanData == null) continue;
                        for (var cls : scanData.getClasses()) {
                            classToModId.putIfAbsent(cls.clazz().getClassName(), modId);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                LOGGER.warn("Failed to build class → mod map: {}", t.toString());
            }
            classMapBuilt = true;
        }
    }

    private static final Set<EventListener> wrappedListeners = Collections.newSetFromMap(
            Collections.synchronizedMap(new WeakHashMap<>()));

    private static EventListener createTimingWrapper(EventListener delegate, String modId, String phaseName) {
        Consumer<Event> timingConsumer = event -> {
            long start = System.nanoTime();
            try {
                delegate.invoke(event);
            } finally {
                long elapsed = System.nanoTime() - start;
                if (WorldLoadProfiler.isActive()) {
                    WorldLoadProfiler.recordModContribution(modId, phaseName, elapsed);
                }
            }
        };
        EventListener wrapper = new ConsumerEventHandler(timingConsumer);
        wrappedListeners.add(wrapper);
        return wrapper;
    }

    private static boolean isAlreadyWrapped(EventListener listener) {
        return wrappedListeners.contains(listener);
    }
}
