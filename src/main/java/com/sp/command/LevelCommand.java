package com.sp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.world.levels.BackroomsLevel;
import com.sp.world.levels.WorldRepresentingBackroomsLevel;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;

import java.util.Optional;

public class LevelCommand {
    public static void register(CommandDispatcher<ServerCommandSource> serverCommandSourceCommandDispatcher, CommandRegistryAccess commandRegistryAccess, CommandManager.RegistrationEnvironment registrationEnvironment) {
        serverCommandSourceCommandDispatcher.register(
                CommandManager.literal("level")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("level", StringArgumentType.word()).suggests(
                                (context, builder) -> {
                                    for (BackroomsLevel backroomsLevel : BackroomsLevels.BACKROOMS_LEVELS) {
                                        builder.suggest(backroomsLevel.getLevelId());
                                    }
                                    return builder.buildFuture();
                                }
                        ).executes((context -> {
                            String levelId = StringArgumentType.getString(context, "level");
                            Optional<BackroomsLevel> optionalBackroomsLevel = BackroomsLevels.getById(levelId);

                            if (optionalBackroomsLevel.isEmpty()) {
                                context.getSource().sendError(Text.literal("Unknown level: " + levelId));
                                return 0;
                            }

                            BackroomsLevel backroomsLevel = optionalBackroomsLevel.get();
                            Entity entity = context.getSource().getEntityOrThrow();

                            if (!(entity instanceof PlayerEntity player)) {
                                context.getSource().sendError(Text.literal("Only players can be sent to a level."));
                                return 0;
                            }

                            ServerWorld destination = context.getSource().getServer().getWorld(backroomsLevel.getWorldKey());
                            if (destination == null) {
                                context.getSource().sendError(Text.literal("The dimension of " + levelId + " is not loaded."));
                                return 0;
                            }

                            PlayerComponent playerComponent = InitializeComponents.PLAYER.get(player);
                            boolean wasInBackrooms = BackroomsLevels.isInBackrooms(player.getWorld().getRegistryKey());
                            boolean goingToBackrooms = !(backroomsLevel instanceof WorldRepresentingBackroomsLevel);
                            Vec3d pos;

                            if (goingToBackrooms) {
                                // Same as entering through a wall: the overworld inventory is kept safe until the player escapes.
                                if (!wasInBackrooms) {
                                    playerComponent.savePlayerInventory();
                                    player.getInventory().clear();
                                }
                                pos = backroomsLevel.getSafeSpawnPos();
                            } else {
                                if (wasInBackrooms) {
                                    playerComponent.loadPlayerSavedInventory();
                                }
                                BlockPos spawn = destination.getSpawnPos();
                                int y = destination.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
                                pos = new Vec3d(spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
                            }

                            playerComponent.sync();
                            player.fallDistance = 0;
                            FabricDimensions.teleport(player, destination, new TeleportTarget(pos, Vec3d.ZERO, player.getYaw(), player.getPitch()));
                            context.getSource().sendFeedback(() -> Text.literal("Sent " + player.getName().getString() + " to " + backroomsLevel.getLevelId()), true);

                            return 1;
                        })))
        );
    }
}
