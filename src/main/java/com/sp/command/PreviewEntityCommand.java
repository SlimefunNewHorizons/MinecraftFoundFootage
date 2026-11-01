package com.sp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.sp.init.ModEntities;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/**
 * Spawns one of the mod's entities with no AI, so its model can be looked at in game.
 */
public class PreviewEntityCommand {
    //*Entities that are not mobs (the Walker) can't have NoAI, so they check this tag instead
    public static final String PREVIEW_TAG = "spb_preview";

    private static final DynamicCommandExceptionType UNKNOWN_ENTITY = new DynamicCommandExceptionType(name -> new LiteralMessage("Unknown entity: " + name));

    private static final Map<String, EntityType<?>> ENTITIES = Map.of(
            "skinwalker", ModEntities.SKIN_WALKER_ENTITY,
            "smiler", ModEntities.SMILER_ENTITY,
            "walker", ModEntities.WALKER_ENTITY,
            "paper_police", ModEntities.PAPER_POLICE_ENTITY,
            "neighbor", ModEntities.NEIGHBOR_ENTITY
    );

    public static void register(CommandDispatcher<ServerCommandSource> serverCommandSourceCommandDispatcher, CommandRegistryAccess commandRegistryAccess, CommandManager.RegistrationEnvironment registrationEnvironment) {
        serverCommandSourceCommandDispatcher.register(
                CommandManager.literal("preview")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("entity", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(ENTITIES.keySet(), builder))
                                .executes(context -> execute(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "entity")
                                ))
                        )
        );
    }

    private static int execute(ServerCommandSource source, String name) throws CommandSyntaxException {
        EntityType<?> type = ENTITIES.get(name);
        if (type == null) {
            throw UNKNOWN_ENTITY.create(name);
        }

        Entity entity = type.create(source.getWorld());
        if (entity == null) {
            return 0;
        }

        Vec3d pos = source.getPosition();
        entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, source.getRotation().y, 0);
        entity.addCommandTag(PREVIEW_TAG);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        if (entity instanceof MobEntity mob) {
            mob.setAiDisabled(true);
            mob.setPersistent();
        }
        source.getWorld().spawnEntity(entity);

        source.sendFeedback(() -> Text.literal("Spawned a preview of " + name + " (no AI). Use /kill @e[tag=" + PREVIEW_TAG + "] to remove it"), true);
        return 1;
    }
}
