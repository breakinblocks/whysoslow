package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.ResourceReloadProfiler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SimpleReloadInstance.class)
public abstract class MixinSimpleReloadInstance {

    @Unique
    private static final String CREATE_STATE =
            "Lnet/minecraft/server/packs/resources/SimpleReloadInstance$StateFactory;create(Lnet/minecraft/server/packs/resources/PreparableReloadListener$SharedState;Lnet/minecraft/server/packs/resources/PreparableReloadListener$PreparationBarrier;Lnet/minecraft/server/packs/resources/PreparableReloadListener;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;";

    @Unique
    private ResourceReloadProfiler.Tracker whysoslow$tracker;

    @Inject(method = "prepareTasks", at = @At("HEAD"))
    private void whysoslow$onPrepareTasks(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        whysoslow$tracker = ResourceReloadProfiler.newTracker();
    }

    @ModifyArg(method = "prepareTasks", at = @At(value = "INVOKE", target = CREATE_STATE), index = 3)
    private Executor whysoslow$timePrepare(Executor executor, @Local PreparableReloadListener listener) {
        return whysoslow$tracker.prepareExecutor(whysoslow$name(listener), executor);
    }

    @ModifyArg(method = "prepareTasks", at = @At(value = "INVOKE", target = CREATE_STATE), index = 4)
    private Executor whysoslow$timeApply(Executor executor, @Local PreparableReloadListener listener) {
        return whysoslow$tracker.applyExecutor(whysoslow$name(listener), executor);
    }

    @ModifyExpressionValue(method = "prepareTasks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Util;sequenceFailFast(Ljava/util/List;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<List<?>> whysoslow$onAllDone(CompletableFuture<List<?>> allDone) {
        ResourceReloadProfiler.Tracker tracker = whysoslow$tracker;
        allDone.whenComplete((result, error) -> tracker.finish(error == null));
        return allDone;
    }

    @Unique
    private static String whysoslow$name(PreparableReloadListener listener) {
        String name = listener.getName();
        if (name == null || name.isBlank()) name = listener.getClass().getName();
        int lambda = name.indexOf("$$Lambda");
        return lambda > 0 ? name.substring(0, lambda) : name;
    }
}
