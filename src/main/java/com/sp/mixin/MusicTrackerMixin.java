package com.sp.mixin;

import com.sp.init.BackroomsLevels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No vanilla music in any Backrooms level: the levels use the overworld dimension effects, so the game would
 * otherwise play its regular overworld songs there. The levels play their own music (see LevelMusic).
 */
@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
    @Shadow
    public abstract void stop();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void noVanillaMusicInTheBackrooms(CallbackInfo ci) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world != null && BackroomsLevels.isInBackrooms(world.getRegistryKey())) {
            this.stop();
            ci.cancel();
        }
    }
}
