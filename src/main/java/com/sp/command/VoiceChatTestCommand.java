package com.sp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.sp.init.ModSounds;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;

/**
 * Command to test that the voice chat pipeline works in singleplayer.
 * <p>
 * Usage: {@code /voicetest}
 * <p>
 * Checks whether the Simple Voice Chat mod is loaded and plays a short test
 * sound so the player can confirm audio output is working.
 */
public class VoiceChatTestCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("voicetest")
                        .requires(source -> source.hasPermissionLevel(0))
                        .executes(context -> {
                            Entity entity = context.getSource().getEntityOrThrow();

                            if (entity instanceof ServerPlayerEntity player) {
                                return runTest(player);
                            }

                            return 0;
                        })
        );
    }

    private static int runTest(ServerPlayerEntity player) {
        boolean voicechatLoaded = FabricLoader.getInstance().isModLoaded("voicechat");

        if (voicechatLoaded) {
            player.sendMessage(
                    Text.literal("§a[Voice Chat] §fSimple Voice Chat mod detected. Playing test sound..."),
                    false
            );
        } else {
            player.sendMessage(
                    Text.literal("§c[Voice Chat] §fSimple Voice Chat mod is NOT installed. Voice chat features will not work."),
                    false
            );
            player.sendMessage(
                    Text.literal("§7Playing a fallback test tone to verify game audio..."),
                    false
            );
        }

        // Play a test sound so the player can confirm audio output works
        player.networkHandler.sendPacket(
                new PlaySoundS2CPacket(
                        RegistryEntry.of(ModSounds.TEST_VOICE),
                        SoundCategory.MASTER,
                        player.getX(), player.getY(), player.getZ(),
                        1.0f, 1.0f,
                        player.getRandom().nextLong()
                )
        );

        player.sendMessage(
                Text.literal("§a[Voice Chat] §fTest sound sent. If you heard it, audio is working correctly."),
                false
        );

        return 1;
    }
}
