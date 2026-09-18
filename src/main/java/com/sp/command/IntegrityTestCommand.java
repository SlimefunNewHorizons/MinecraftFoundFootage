package com.sp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.init.ModEntities;
import com.sp.world.levels.BackroomsLevel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Diagnostic in-game command to verify mod integrity:
 * - Backrooms levels and safe spawn coordinates
 * - Entity registrations
 * - Player component state
 * - Voice chat integration
 *
 * Usage: {@code /integritytest}
 */
public class IntegrityTestCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("integritytest")
                        .requires(source -> source.hasPermissionLevel(0))
                        .executes(context -> {
                            Entity entity = context.getSource().getEntityOrThrow();
                            if (entity instanceof ServerPlayerEntity player) {
                                return runDiagnostic(player);
                            }
                            return 0;
                        })
        );
    }

    private static int runDiagnostic(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§e=== [MFF Integrity Diagnostic] ==="), false);
        int passed = 0;
        int total = 0;

        // 1. Backrooms Levels and Safe Spawns
        total++;
        boolean levelsOk = true;
        StringBuilder levelDetails = new StringBuilder();
        if (BackroomsLevels.BACKROOMS_LEVELS.isEmpty()) {
            levelsOk = false;
        } else {
            for (BackroomsLevel level : BackroomsLevels.BACKROOMS_LEVELS) {
                Vec3d safePos = level.getSafeSpawnPos();
                if (safePos == null) {
                    levelsOk = false;
                    levelDetails.append("§c- ").append(level.getLevelId()).append(" (NULL spawn!)\n");
                } else {
                    levelDetails.append("§7- ").append(level.getLevelId())
                            .append(" safePos: [").append(String.format("%.1f, %.1f, %.1f", safePos.x, safePos.y, safePos.z))
                            .append("]\n");
                }
            }
        }
        if (levelsOk) {
            passed++;
            player.sendMessage(Text.literal("§a[PASS] §fNiveles registrados (" + BackroomsLevels.BACKROOMS_LEVELS.size() + ") con SafeSpawn válido:"), false);
            player.sendMessage(Text.literal(levelDetails.toString()), false);
        } else {
            player.sendMessage(Text.literal("§c[FAIL] §fFallo en niveles de Backrooms:\n" + levelDetails), false);
        }

        // 2. Entity Registrations
        total++;
        boolean entitiesOk = ModEntities.SKIN_WALKER_ENTITY != null
                && ModEntities.SMILER_ENTITY != null
                && ModEntities.PAPER_POLICE_ENTITY != null;
        if (entitiesOk) {
            passed++;
            player.sendMessage(Text.literal("§a[PASS] §fEntidades registradas (SkinWalker, Smiler, PaperPolice)"), false);
        } else {
            player.sendMessage(Text.literal("§c[FAIL] §fUna o más entidades no se registraron."), false);
        }

        // 3. PlayerComponent State
        total++;
        try {
            PlayerComponent comp = InitializeComponents.PLAYER.get(player);
            if (comp != null) {
                passed++;
                player.sendMessage(Text.literal("§a[PASS] §fPlayerComponent adjunto. MuteTimer: "
                        + comp.getMuteTimer() + ", Speaking: " + comp.isSpeaking()
                        + ", Loud: " + comp.isTalkingTooLoud()), false);
            } else {
                player.sendMessage(Text.literal("§c[FAIL] §fPlayerComponent no encontrado en el jugador."), false);
            }
        } catch (Exception e) {
            player.sendMessage(Text.literal("§c[FAIL] §fExcepción accediendo a PlayerComponent: " + e.getMessage()), false);
        }

        // 4. Voice Chat Integration
        total++;
        boolean voiceLoaded = FabricLoader.getInstance().isModLoaded("voicechat");
        passed++;
        if (voiceLoaded) {
            player.sendMessage(Text.literal("§a[PASS] §fSimple Voice Chat detectado y activo."), false);
        } else {
            player.sendMessage(Text.literal("§e[WARN] §fSimple Voice Chat no instalado (modo opcional)."), false);
        }

        player.sendMessage(Text.literal("§e=== Resultado: " + passed + "/" + total + " pruebas pasadas ==="), false);
        return passed;
    }
}
