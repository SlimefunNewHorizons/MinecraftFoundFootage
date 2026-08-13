package com.sp.networking.S2C;

import com.sp.render.gui.AcceptAIModelsScreen;
import com.sp.util.AIConsentStore;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;

public class InvokeConsentScreenPacket {

    public static void receive(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender){
        client.execute(()->{
            if (client.player != null && client.currentScreen == null && !AIConsentStore.hasAccepted()) {
                client.setScreen(new AcceptAIModelsScreen());
            }
        });
    }

}
