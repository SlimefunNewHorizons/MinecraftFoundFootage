package com.sp.mixin;

import com.sp.cca_stuff.InitializeComponents;
import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client tells the server its game language when it joins; the first time, it becomes the mod's language.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ClientLanguageMixin {
    @Inject(method = "setClientSettings", at = @At("HEAD"))
    private void pickModLanguage(ClientSettingsC2SPacket packet, CallbackInfo ci) {
        InitializeComponents.PLAYER.get((ServerPlayerEntity) (Object) this).onClientLanguage(packet.language());
    }
}
