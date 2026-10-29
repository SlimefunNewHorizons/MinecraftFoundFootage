package com.sp.networking.S2C;

import com.sp.SPBRevampedClient;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundCategory;

import com.sp.util.TickScheduler;

public class LevelTransitionLightsOut {

    public static void receive(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        int time = buf.readInt();

        client.execute(()->{
            if(client.player != null) {
                PlayerComponent playerComponent = InitializeComponents.PLAYER.get(client.player);

                //Turn off the lights
                playerComponent.player.playSound(ModSounds.LIGHTS_OUT, SoundCategory.AMBIENT, 1, 1);
                SPBRevampedClient.getCutsceneManager().blackScreen.showBlackScreen(time, false, false);

                //PlaySound after black screen is over
                TickScheduler.CLIENT.schedule(time, () -> {
                    if (client.player == null) return;
                    client.player.playSound(ModSounds.LIGHTS_ON, SoundCategory.AMBIENT, 1, 1);
                    SPBRevampedClient.sendComponentSyncPacket(false, "teleporting");
                });
            }
        });
    }

}
