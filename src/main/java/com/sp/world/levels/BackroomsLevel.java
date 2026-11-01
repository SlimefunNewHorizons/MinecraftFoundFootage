package com.sp.world.levels;

import com.mojang.serialization.Codec;
import com.sp.SPBRevamped;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.world.events.AbstractEvent;
import com.sp.world.events.EmptyEvent;
import com.sp.world.levels.custom.vanilla_representing.OverworldRepresentingBackroomsLevel;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public abstract class BackroomsLevel {
    private final String levelId;
    private final String modId;
    private final RoomCount roomCount;
    private final Codec<? extends ChunkGenerator> chunkGeneratorCodec;
    private final RegistryKey<World> worldKey;
    private final Vec3d spawnPos;
    public Random random = new Random();
    private boolean shouldSync = false;
    private final HashMap<String, Supplier<AbstractEvent>> events = new HashMap<>();
    private final HashMap<String, LevelTransitionCriteriaCallback> transitions = new HashMap<>();
    private final Map<BackroomsLevel, RouteType> connections = new LinkedHashMap<>();
    private BackroomsLevel fallbackBackroomsLevel;

    public BackroomsLevel(String levelId, Codec<? extends ChunkGenerator> chunkGenerator, Vec3d spawnPos, RegistryKey<World> worldKey) {
        this(levelId, chunkGenerator, null, spawnPos, worldKey, SPBRevamped.MOD_ID);
    }

    public BackroomsLevel(String levelId, Codec<? extends ChunkGenerator> chunkGenerator, RoomCount roomCount, Vec3d spawnPos, RegistryKey<World> worldKey) {
        this(levelId, chunkGenerator, roomCount, spawnPos, worldKey, SPBRevamped.MOD_ID);
    }

    public BackroomsLevel(String levelId, Codec<? extends ChunkGenerator> chunkGenerator, Vec3d spawnPos, RegistryKey<World> worldKey, String modId) {
        this(levelId, chunkGenerator, null, spawnPos, worldKey, modId);
    }

    public BackroomsLevel(String levelId, Codec<? extends ChunkGenerator> chunkGenerator, @Nullable RoomCount roomCount, Vec3d spawnPos, RegistryKey<World> worldKey, String modId) {
        this.levelId = levelId;
        this.chunkGeneratorCodec = chunkGenerator;
        this.spawnPos = spawnPos;
        this.worldKey = worldKey;
        this.modId = modId;

        this.roomCount = roomCount;
    }

    public void register() {
        Registry.register(Registries.CHUNK_GENERATOR, new Identifier(modId, levelId + "_chunk_generator"), chunkGeneratorCodec);
    }

    public String getLevelId() {
        return levelId;
    }

    public String getModId() {
        return modId;
    }

    public RegistryKey<World> getWorldKey() {
        return worldKey;
    }

    public Vec3d getSpawnPos() {
        return spawnPos;
    }

    /**
     * Returns a safe spawn position slightly above the default spawn to avoid
     * immediate damage (suffocation, collision) after a level transition.
     */
    public Vec3d getSafeSpawnPos() {
        return new Vec3d(spawnPos.x, spawnPos.y + 2, spawnPos.z);
    }

    public Optional<RoomCount> getRoomCount() {
        return Optional.ofNullable(roomCount);
    }

    /**
     * If the level allows the torch to be used.
     * @return a BoolTextPair containing the value and a message to display to the player.
     */
    public BoolTextPair allowsTorch() {
        return new BoolTextPair(true, Text.literal("Flashlight is allowed in this level."));
    }

    /**
     * If the level renders the sky.
     * Also look at {@link #rendersClouds()}.
     * @return if the sky renders.
     */
    public boolean rendersSky() {
        return true;
    }

    /**
     * If the level renders the sky.
     * Also look at {@link #rendersSky()}.
     * @return if the sky renders.
     */
    public boolean rendersClouds() {
        return true;
    }

    /**
     * @return If the level has vanilla lighting.
     * Without vanilla lighting, the level will be completely dark without light sources like the torch.
     */
    public boolean hasVanillaLighting() {
        return false;
    }

    public record BoolTextPair(boolean value, MutableText string) {}

    public AbstractEvent getRandomEvent(World world) {
        if (this.events.isEmpty()) {
            return new EmptyEvent();
        }

        List<AbstractEvent> eventList = new ArrayList<>();

        this.events.forEach((key, value) -> {
            AbstractEvent event = value.get();
            eventList.add(event);
        });

        return eventList.get(random.nextInt(eventList.size()));
    }

    /**
     * The exits the player is triggering right now. On the route (see {@link BackroomsRoute}) every exit leads to
     * the next level of this world, whatever destination the exit was written with.
     */
    public List<LevelTransition> checkForTransition(PlayerComponent playerComponent, World world) {
        List<LevelTransition> possibleTransitions = new ArrayList<>();
        BackroomsLevel next = BackroomsRoute.next(this, playerComponent.getRouteSeed());
        this.transitions.forEach((key, value) -> {
            List<LevelTransition> foundTransitions = value.predicate(world, playerComponent, this);
            if (foundTransitions.isEmpty()) {
                return;
            }
            LevelTransition found = foundTransitions.get(0);
            if (next != null) {
                possibleTransitions.add(reroute(found, next, playerComponent));
            } else if (isConnectedTo(found.teleport().to())) {
                possibleTransitions.add(found);
            }
        });

        return possibleTransitions;
    }

    private LevelTransition reroute(LevelTransition transition, BackroomsLevel next, PlayerComponent playerComponent) {
        if (transition.teleport().to() == next) {
            return transition;
        }

        boolean home = next == BackroomsLevels.OVERWORLD_REPRESENTING_BACKROOMS_LEVEL;
        Vec3d arrival = home ? OverworldRepresentingBackroomsLevel.returnPosition(playerComponent) : next.getSafeSpawnPos();
        //*The exit's own effect stays, unless it was written for going home (it hands back the saved inventory)
        TransitionTickCallback callback = transition.teleport().to() == BackroomsLevels.OVERWORLD_REPRESENTING_BACKROOMS_LEVEL
                ? (teleport, tick) -> {
                    if (!teleport.playerComponent().player.getWorld().isClient() && tick == transition.duration()) {
                        SPBRevamped.sendBlackScreenPacket((ServerPlayerEntity) teleport.playerComponent().player, 40, true, false);
                    }
                }
                : transition.callback();
        return new LevelTransition(transition.duration(), callback, new CrossDimensionTeleport(playerComponent, arrival, this, next), transition.cancel());
    }

    public void justChanged() {
        this.shouldSync = true;
    }

    public abstract int nextEventDelay();

    /**
     * Called when the level needs to be synced or saved to disk.
     * Here you can put anything you want to save to the NBT.
     * You do <b>not</b> needing to make a NbtCompound first. That is handled by the WorldEvents class.
     * <b>NOTE</b>: You should not only save data here which you want to <b>save</b> to disk but also which you want to <b>sync</b>.
     * @param nbt the NbtCompound to save in, assigned by the WorldEvents class.
     */
    public abstract void writeToNbt(NbtCompound nbt);

    /**
     * Called when the level is loaded from disk.
     * Here you can read anything you want to load from the NBT into your level.
     * You do <b>not</b> needing to step down into an NbtCompound first. That is handled by the WorldEvents class.
     * @param nbt the NbtCompound to load in, assigned by the WorldEvents class.
     */
    public abstract void readFromNbt(NbtCompound nbt);

    public boolean shouldSync() {
        boolean shouldSync = this.shouldSync;
        this.shouldSync = false;
        return shouldSync;
    }

    /**
     * Called when transitioning out of this level.
     * This method is called on the same tick the player gets teleported. (Same on the client and server)
     */
    public abstract void transitionOut(CrossDimensionTeleport crossDimensionTeleport);

    /**
     * Called when transitioning in to this level.
     * It is called first on client at <code>teleportingTimer == 1</code>,
     * then the method will be called on the server later when <code>teleportingTimer == 0</code>.
     * <b>Note:</b> this may lead to the method being called multiple times, so be careful with the code you put in here.
     */
    public abstract void transitionIn(CrossDimensionTeleport crossDimensionTeleport);

    public void registerTransition(LevelTransitionCriteriaCallback transition, String name) {
        this.transitions.put(name, transition);
    }

    public void unregisterTransition(String name) {
        this.transitions.remove(name);
    }

    /**
     * Declares a route from this level to another one in the level graph.
     * The {@link com.sp.world.levels.BackroomsLevel.LevelTransition} entries whose destination is not
     * registered as a connection are ignored by {@link #checkForTransition(PlayerComponent, World)}.
     * <p>
     * Mirrors the Eufonia Backrooms "wiki_levels" approach: every level declares its routes as
     * {@link RouteType#MAIN} (story progression) or {@link RouteType#SECONDARY} (optional branch).
     */
    public void registerConnection(BackroomsLevel to, RouteType type) {
        this.connections.put(to, type);
    }

    public Map<BackroomsLevel, RouteType> getConnections() {
        return Collections.unmodifiableMap(this.connections);
    }

    public boolean isConnectedTo(BackroomsLevel to) {
        return this.connections.containsKey(to);
    }

    public Optional<RouteType> getRouteTypeTo(BackroomsLevel to) {
        return Optional.ofNullable(this.connections.get(to));
    }

    /**
     * If the player falls below {@link #getFallOutY()} this level, they are teleported back to the
     * {@code fallback} level's spawn (Eufonia's "fallbackLevelId" concept) instead of dying in the void.
     */
    public void setFallbackLevel(BackroomsLevel fallbackBackroomsLevel) {
        this.fallbackBackroomsLevel = fallbackBackroomsLevel;
    }

    public BackroomsLevel getFallbackLevel() {
        return this.fallbackBackroomsLevel;
    }

    /**
     * The y value below which the player is considered to have fallen out of this level.
     */
    public int getFallOutY() {
        return -40;
    }

    /**
     * Deterministic position of the checkpoint this level generated for the player position.
     * Safe-rooms/respawn anchors placed by the chunk generator. Returns {@code null} if the level
     * has no checkpoints.
     */
    public BlockPos findCheckpoint(Vec3d pos) {
        return null;
    }

    public enum RouteType {
        MAIN,
        SECONDARY
    }


    public void registerEvent(String name, Supplier<AbstractEvent> event) {
        this.events.put(name, event);
    }

    public void unregisterEvents(String name) {
        this.events.remove(name);
    }

    /**
     * @param duration How many ticks the transition should take.
     * @param callback The callback to call every tick during the transition. <b>Note:</b> The last tick which is called is 1. Not 0. The teleport happens on 0.
     * @param teleport The teleport to perform when the transition is done.
     * @param cancel The callback to call when the transition is cancelled. <b>Note:</b> This is currently not used as canceling transitions has proven very buggy.
     */
    public record LevelTransition(int duration, TransitionTickCallback callback, CrossDimensionTeleport teleport, TransitionCancelCallback cancel) {}

    public interface TransitionTickCallback {
        void tick(CrossDimensionTeleport teleport, int tick);
    }

    public interface TransitionCancelCallback {
        void cancel(CrossDimensionTeleport teleport, int tick);
    }

    public record CrossDimensionTeleport(PlayerComponent playerComponent, Vec3d pos, BackroomsLevel from, BackroomsLevel to) {}

    public interface LevelTransitionCriteriaCallback {
        List<LevelTransition> predicate(World world, PlayerComponent playerComponent, BackroomsLevel from);
    }

    public record RoomCount(int aRoomCount, int bRoomCount, int cRoomCount, int dRoomCount, int eRoomCount) {
        public RoomCount(int i) {
            this(i, i, i, i, i);
        }
    }
}
