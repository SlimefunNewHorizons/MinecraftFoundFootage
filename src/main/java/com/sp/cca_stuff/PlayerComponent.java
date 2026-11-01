package com.sp.cca_stuff;

import com.sp.SPBRevamped;
import com.sp.clientWrapper.ClientWrapper;
import com.sp.entity.custom.SmilerEntity;
import com.sp.init.*;
import com.sp.mixininterfaces.ServerPlayNetworkSprint;
import com.sp.entity.voice.SkinWalkerMovement;
import com.sp.sounds.voicechat.BackroomsVoicechatPlugin;
import com.sp.world.levels.BackroomsLevel;
import com.sp.world.levels.BackroomsRoute;
import com.sp.world.generation.chunk_generator.Level94Layout;
import com.sp.world.levels.custom.Level2BackroomsLevel;
import com.sp.world.levels.custom.vanilla_representing.OverworldRepresentingBackroomsLevel;
import com.sp.world.levels.custom.Level324Backroomslevel;
import dev.onyxstudios.cca.api.v3.component.ComponentProvider;
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ClientTickingComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import static com.sp.SPBRevamped.SLOW_SPEED_MODIFIER;

@SuppressWarnings("DataFlowIssue")
public class PlayerComponent implements AutoSyncedComponent, ClientTickingComponent, ServerTickingComponent {
    public final PlayerEntity player;
    private final SimpleInventory playerSavedMainInventory = new SimpleInventory(36);
    private final SimpleInventory playerSavedArmorInventory = new SimpleInventory(4);
    private final SimpleInventory playerSavedOffhandInventory = new SimpleInventory(1);
    private final Random random = new Random();

    private int smilerSpawnDelay = 80;

    private int stamina;
    private boolean tired;

    private int scrollingInInventoryTime;

    private boolean flashLightOn;
    private boolean shouldRender;
    private boolean isDoingCutscene;
    private boolean acceptedAIModels;
    private boolean playingGlitchSound;
    private boolean shouldNoClip;
    private int teleportingTimer;
    private @Nullable GlobalPos checkpoint;
    private boolean isTeleporting;

    public int suffocationTimer;
    private boolean shouldDoStatic;

    private boolean isBeingCaptured;
    public boolean hasBeenCaptured;
    private boolean isBeingReleased;
    //*World seed the level route is shuffled with (see BackroomsRoute), synced so the client agrees on it
    private long routeSeed;
    private Entity targetEntity;
    private int skinWalkerLookDelay;
    private volatile boolean shouldBeMuted;
    private int muteTimer;
    private volatile boolean isSpeaking;
    private int speakingBuffer;
    private float prevSpeakingTime;
    private boolean visibleToEntity;
    private int visibilityTimer;
    private int visibilityTimerCooldown;
    private volatile boolean talkingTooLoud;
    private int talkingTooLoudTimer;
    private GameMode prevGameMode;
    private String skinWalkerLang = "auto";
    //*Set once, the first time this player joins: the mod's language then follows their game language
    private boolean languageInitialized;

    private boolean canSeeActiveSkinWalker;
    private boolean prevFlashLightOn;

    public float glitchTimer;
    private boolean shouldGlitch;
    public int glitchTick;
    public boolean shouldInflictGlitchDamage;

    public BackroomsLevel.LevelTransition currentTransition = null;

    public PlayerComponent(PlayerEntity player){
        this.stamina = 300;
        this.tired = false;
        this.scrollingInInventoryTime = 0;
        this.player = player;
        this.flashLightOn = false;
        this.shouldRender = true;
        this.shouldNoClip = false;
        this.shouldDoStatic = false;

        this.isDoingCutscene = false;
        this.acceptedAIModels = false;

        this.isBeingCaptured = false;
        this.hasBeenCaptured = false;
        this.isBeingReleased = false;
        this.skinWalkerLookDelay = 60;
        this.shouldBeMuted = false;
        this.isSpeaking = false;
        this.speakingBuffer = 80;
        this.prevSpeakingTime = 0;
        this.visibleToEntity = false;
        this.visibilityTimer = 15;
        this.visibilityTimerCooldown = 0;

        this.talkingTooLoud = false;
        this.talkingTooLoudTimer = 20;

        this.suffocationTimer = 0;

        this.canSeeActiveSkinWalker = false;

        this.glitchTimer = 0.0f;
        this.shouldGlitch = false;
        this.glitchTick = 0;

        this.teleportingTimer = -1;
    }

    public void savePlayerInventory() {
        PlayerInventory inventory = this.player.getInventory();
        DefaultedList<ItemStack> mainInventory = inventory.main;
        DefaultedList<ItemStack> armorInventory = inventory.armor;
        DefaultedList<ItemStack> offHand = inventory.offHand;

        this.saveInventory(mainInventory, this.playerSavedMainInventory);
        this.saveInventory(armorInventory, this.playerSavedArmorInventory);
        this.saveInventory(offHand, this.playerSavedOffhandInventory);
    }

    public boolean hasSavedInventory() {
        return !this.playerSavedMainInventory.isEmpty() || !this.playerSavedArmorInventory.isEmpty() || !this.playerSavedOffhandInventory.isEmpty();
    }

    /**
     * Gives the player back the inventory saved by {@link #savePlayerInventory()}.
     * Does nothing if there is nothing saved, so calling it twice (e.g. from a transition callback and from
     * {@code transitionIn}) can no longer wipe the restored items.
     */
    public void loadPlayerSavedInventory() {
        if (!this.hasSavedInventory()) {
            return;
        }

        PlayerInventory inventory = this.player.getInventory();
        //*The camcorder stays with the player: it is not part of the inventory they left behind
        ItemStack camera = ItemStack.EMPTY;
        for (int i = 0; i < inventory.size() && camera.isEmpty(); i++) {
            if (inventory.getStack(i).isOf(ModItems.CAMERA)) {
                camera = inventory.getStack(i).copy();
            }
        }

        inventory.clear();
        DefaultedList<ItemStack> mainInventory = inventory.main;
        DefaultedList<ItemStack> armorInventory = inventory.armor;
        DefaultedList<ItemStack> offHand = inventory.offHand;

        this.loadInventory(mainInventory, this.playerSavedMainInventory);
        this.loadInventory(armorInventory, this.playerSavedArmorInventory);
        this.loadInventory(offHand, this.playerSavedOffhandInventory);

        this.playerSavedMainInventory.clear();
        this.playerSavedArmorInventory.clear();
        this.playerSavedOffhandInventory.clear();

        if (!camera.isEmpty() && !inventory.containsAny(Set.of(ModItems.CAMERA)) && !inventory.insertStack(camera)) {
            this.player.dropItem(camera, false);
        }
    }

    private void saveInventory(DefaultedList<ItemStack> inventory1, Inventory inventory2){
        inventory2.clear();
        for (int i = 0; i < inventory1.size(); i++) {
            ItemStack itemStack = inventory1.get(i);
            if (!itemStack.isEmpty()) {
                inventory2.setStack(i, itemStack);
            }
        }
    }

    private void loadInventory(DefaultedList<ItemStack> inventory1, Inventory inventory2){
        for (int i = 0; i < inventory2.size(); i++) {
            ItemStack itemStack = inventory2.getStack(i);
            if (!itemStack.isEmpty()) {
                inventory1.set(i, itemStack);
            }
        }
    }

    public int getTeleportingTimer() {
        return teleportingTimer;
    }

    public void setTeleportingTimer(int teleportingTimer) {
        this.teleportingTimer = teleportingTimer;
        this.justChanged();
    }

    public @Nullable GlobalPos getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(GlobalPos checkpoint) {
        this.justChanged();
        this.checkpoint = checkpoint;
    }

    public boolean isTeleporting() {
        return isTeleporting;
    }
    public void setTeleporting(boolean teleporting) {
        isTeleporting = teleporting;
    }

    public int getStamina() {
        return stamina;
    }
    public void setStamina(int stamina) {
        this.stamina = stamina;
    }

    public boolean isTired() {
        return tired;
    }
    public void setTired(boolean tired) {
        this.tired = tired;
    }

    public int getScrollingInInventoryTime() {
        return scrollingInInventoryTime;
    }
    public void setScrollingInInventoryTime(int scrollingInInventoryTime) {
        this.scrollingInInventoryTime = scrollingInInventoryTime;
    }

    public boolean isShouldRender() {
        return shouldRender;
    }
    public void setShouldRender(boolean shouldRender) {
        this.shouldRender = shouldRender;
    }

    public void setFlashLightOn(boolean set){
        this.flashLightOn = set;
    }
    public boolean isFlashLightOn() {
        return flashLightOn;
    }

    public boolean isDoingCutscene() {
        return isDoingCutscene;
    }
    public void setDoingCutscene(boolean doingCutscene) {
        isDoingCutscene = doingCutscene;
    }

    public boolean hasAcceptedAIModels() {
        return acceptedAIModels;
    }
    public void setAcceptedAIModels(boolean acceptedAIModels) {
        this.acceptedAIModels = acceptedAIModels;
    }

    public boolean isTeleportingToPoolrooms() {
        BackroomsLevel level = BackroomsLevels.getLevel(this.player.getWorld()).orElse(BackroomsLevels.OVERWORLD_REPRESENTING_BACKROOMS_LEVEL);
        return (level instanceof Level2BackroomsLevel || level instanceof Level324Backroomslevel) && this.teleportingTimer > 0;
    }

    public boolean shouldNoClip() {
        return shouldNoClip;
    }
    public void setShouldNoClip(boolean shouldNoClip) {
        this.shouldNoClip = shouldNoClip;
    }

    public boolean isShouldDoStatic() {
        return shouldDoStatic;
    }
    public void setShouldDoStatic(boolean shouldDoStatic) {
        this.shouldDoStatic = shouldDoStatic;
    }

    public boolean isBeingCaptured() {return isBeingCaptured;}
    public void setBeingCaptured(boolean beingCaptured) {isBeingCaptured = beingCaptured;}

    public boolean hasBeenCaptured() {return hasBeenCaptured;}
    public void setHasBeenCaptured(boolean hasBeenCaptured) {this.hasBeenCaptured = hasBeenCaptured;}

    public boolean isBeingReleased() {
        return isBeingReleased;
    }

    public long getRouteSeed() {
        return routeSeed;
    }
    public void setBeingReleased(boolean beingReleased) {
        isBeingReleased = beingReleased;
    }

    public Entity getTargetEntity() {return targetEntity;}
    public void setTargetEntity(Entity targetEntity) {
        this.targetEntity = targetEntity;
    }

    public int getSkinWalkerLookDelay() {
        return skinWalkerLookDelay;
    }
    public void setSkinWalkerLookDelay(int skinWalkerLookDelay) {
        this.skinWalkerLookDelay = skinWalkerLookDelay;
    }
    public void subtractSkinWalkerLookDelay() {
        this.skinWalkerLookDelay -= 1;
    }

    public boolean shouldBeMuted() {return shouldBeMuted;}
    public void setShouldBeMuted(boolean shouldStayUnmuted) {this.shouldBeMuted = shouldStayUnmuted;}
    public void setMuteTimer(int ticks) {
        this.muteTimer = ticks;
        this.setShouldBeMuted(true);
        this.sync();
    }
    public int getMuteTimer() {
        return this.muteTimer;
    }

    public boolean isSpeaking() {
        return isSpeaking;
    }
    public void setSpeaking(boolean speaking) {
        isSpeaking = speaking;
    }

    public boolean isVisibleToEntity() {return visibleToEntity;}
    public void setVisibleToEntity(boolean visibleToEntity) {this.visibleToEntity = visibleToEntity;}

    public boolean isTalkingTooLoud() {
        return talkingTooLoud;
    }
    public void setTalkingTooLoud(boolean talkingTooLoud) {
        this.talkingTooLoud = talkingTooLoud;
    }

    public void resetTalkingTooLoudTimer(){
        this.talkingTooLoudTimer = 20;
    }

    public GameMode getPrevGameMode() {
        return prevGameMode;
    }
    public void setPrevGameMode(GameMode prevGameMode) {
        this.prevGameMode = prevGameMode;
    }

    public String getSkinWalkerLang() { return skinWalkerLang; }
    public void setSkinWalkerLang(String lang) { this.skinWalkerLang = lang; }

    /**
     * Languages the mod is fully translated to and the entities fully speak. Code -> English name.
     */
    public static final Map<String, String> FULL_LANGUAGES = Map.of("en", "English", "es", "Spanish");

    /**
     * The mod language for a game language such as "es_mx": its two letter code if fully supported, else English.
     */
    public static String modLanguageFor(String gameLanguage) {
        String code = gameLanguage == null ? "" : gameLanguage.toLowerCase(Locale.ROOT).split("_")[0];
        return FULL_LANGUAGES.containsKey(code) ? code : "en";
    }

    /**
     * Called with the game language the client reports. The first time ever, it picks the mod's language from it and
     * tells the player (in English, whatever their language) how to change it.
     */
    public void onClientLanguage(String gameLanguage) {
        if (this.languageInitialized || !(this.player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        this.languageInitialized = true;
        this.skinWalkerLang = modLanguageFor(gameLanguage);
        this.sync();

        String available = FULL_LANGUAGES.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")").collect(Collectors.joining(", "));
        serverPlayer.sendMessage(Text.literal("[Minecraft Found Footage] ").formatted(Formatting.DARK_RED)
                .append(Text.literal("The entities will talk to you in " + FULL_LANGUAGES.get(this.skinWalkerLang)
                        + ", the language of your game.").formatted(Formatting.GRAY)));
        serverPlayer.sendMessage(Text.literal("Change it any time with ").formatted(Formatting.GRAY)
                .append(Text.literal("/skinwalkerlang <language>").formatted(Formatting.YELLOW))
                .append(Text.literal(". Fully supported: " + available + ". Or ").formatted(Formatting.GRAY))
                .append(Text.literal("auto").formatted(Formatting.YELLOW))
                .append(Text.literal(" to answer in the language you speak.").formatted(Formatting.GRAY)));
    }

    public boolean canSeeActiveSkinWalkerTarget() {return canSeeActiveSkinWalker;}
    public void setCanSeeActiveSkinWalkerTarget(boolean canSeeActiveSkinWalker) {this.canSeeActiveSkinWalker = canSeeActiveSkinWalker;}

    public float getGlitchTimer() {
        return glitchTimer;
    }

    public boolean shouldGlitch() {
        return shouldGlitch;
    }
    public void setShouldGlitch(boolean shouldGlitch) {
        this.shouldGlitch = shouldGlitch;
    }

    public void setShouldInflictGlitchDamage(boolean shouldInflictGlitchDamage) {
        this.shouldInflictGlitchDamage = shouldInflictGlitchDamage;
    }

    @Override
    public void readFromNbt(NbtCompound tag) {
        this.stamina = tag.getInt("stamina");
        this.flashLightOn = tag.getBoolean("flashLightOn");
        this.shouldRender = tag.getBoolean("shouldRender");
        this.isDoingCutscene = tag.getBoolean("isDoingCutscene");
        this.acceptedAIModels = tag.getBoolean("acceptedAIModels");
        this.playingGlitchSound = tag.getBoolean("playingGlitchSound");
        this.shouldNoClip = tag.getBoolean("shouldNoClip");
        this.shouldDoStatic = tag.getBoolean("shouldDoStatic");
        this.isBeingCaptured = tag.getBoolean("isBeingCaptured");
        this.hasBeenCaptured = tag.getBoolean("hasBeenCaptured");
        this.isBeingReleased = tag.getBoolean("isBeingReleased");
        this.shouldBeMuted = tag.getBoolean("shouldBeMuted");
        this.muteTimer = tag.getInt("muteTimer");
        this.shouldGlitch = tag.getBoolean("shouldGlitch");
        this.shouldInflictGlitchDamage = tag.getBoolean("shouldInflictGlitchDamage");
        this.teleportingTimer = tag.getInt("teleportingTimer");
        this.routeSeed = tag.getLong("routeSeed");

        if (tag.contains("checkpoint")) {
            NbtCompound checkpointNbt = tag.getCompound("checkpoint");
            this.checkpoint = GlobalPos.create(
                RegistryKey.of(RegistryKeys.WORLD, new Identifier(checkpointNbt.getString("dimension"))),
                new BlockPos(checkpointNbt.getInt("x"), checkpointNbt.getInt("y"), checkpointNbt.getInt("z")));
        }
        this.skinWalkerLang = tag.contains("skinWalkerLang") ? tag.getString("skinWalkerLang") : "auto";
        this.languageInitialized = tag.getBoolean("languageInitialized");

        this.playerSavedMainInventory.readNbtList(tag.getList("inventory", NbtElement.COMPOUND_TYPE));
        this.playerSavedOffhandInventory.readNbtList(tag.getList("inventoryOffHand", NbtElement.COMPOUND_TYPE));
        this.playerSavedArmorInventory.readNbtList(tag.getList("inventoryArmor", NbtElement.COMPOUND_TYPE));
    }

    @Override
    public void writeToNbt(NbtCompound tag) {
        tag.putInt("stamina", this.stamina);
        tag.putBoolean("flashLightOn", this.flashLightOn);
        tag.putBoolean("shouldRender", this.shouldRender);
        tag.putBoolean("isDoingCutscene", this.isDoingCutscene);
        tag.putBoolean("acceptedAIModels", this.acceptedAIModels);
        tag.putBoolean("playingGlitchSound", this.playingGlitchSound);
        tag.putBoolean("shouldNoClip", this.shouldNoClip);
        tag.putBoolean("shouldDoStatic", this.shouldDoStatic);
        tag.putBoolean("isBeingCaptured", this.isBeingCaptured);
        tag.putBoolean("hasBeenCaptured", this.hasBeenCaptured);
        tag.putBoolean("isBeingReleased", this.isBeingReleased);
        tag.putBoolean("shouldBeMuted", this.shouldBeMuted);
        tag.putInt("muteTimer", this.muteTimer);
        tag.putBoolean("shouldGlitch", this.shouldGlitch);
        tag.putBoolean("shouldInflictGlitchDamage", this.shouldInflictGlitchDamage);
        tag.putInt("teleportingTimer", this.teleportingTimer);
        tag.putLong("routeSeed", this.routeSeed);
        tag.putString("skinWalkerLang", this.skinWalkerLang);
        tag.putBoolean("languageInitialized", this.languageInitialized);

        if (this.checkpoint != null) {
            NbtCompound checkpointNbt = new NbtCompound();
            checkpointNbt.putString("dimension", this.checkpoint.getDimension().getValue().toString());
            checkpointNbt.putInt("x", this.checkpoint.getPos().getX());
            checkpointNbt.putInt("y", this.checkpoint.getPos().getY());
            checkpointNbt.putInt("z", this.checkpoint.getPos().getZ());
            tag.put("checkpoint", checkpointNbt);
        }

        if (BackroomsLevels.isInBackrooms(this.player.getWorld().getRegistryKey())) {
            tag.put("inventory", this.playerSavedMainInventory.toNbtList());
            tag.put("inventoryOffHand", this.playerSavedOffhandInventory.toNbtList());
            tag.put("inventoryArmor", this.playerSavedArmorInventory.toNbtList());
        }
    }

    public void sync() {
        InitializeComponents.PLAYER.sync(this.player);
    }

    @Override
    public void clientTick() {
        // 0 means not synced yet: in singleplayer the server and this client share the layout, never reset it
        if (this.routeSeed != 0) {
            Level94Layout.useSeed(this.routeSeed);
        }
        ClientWrapper.tickClientPlayerComponent(this);
    }

    @Override
    public void serverTick() {
        getPrevSettings();

        long worldSeed = ((ServerWorld) this.player.getWorld()).getServer().getOverworld().getSeed();
        if (this.routeSeed != worldSeed) {
            this.routeSeed = worldSeed;
            this.sync();
        }

        //*Update Mute Timer
        if (this.muteTimer > 0) {
            this.muteTimer--;
            if (this.muteTimer <= 0 && !this.isBeingCaptured && !this.hasBeenCaptured) {
                this.setShouldBeMuted(false);
                this.sync();
            }
        }

        //*Update Stamina
        updateStamina();

        //*Damage if glitched enough from smilers
        if(this.shouldInflictGlitchDamage){
            this.player.damage(ModDamageTypes.of(this.player.getWorld(), ModDamageTypes.SMILER), 1.0f);
        }

        //*Teach the SkinWalker how this player moves while they still control their own body
        if (!this.player.isSpectator() && !this.player.getAbilities().flying && !this.hasBeenCaptured) {
            SkinWalkerMovement.recordPlayer(this.player);
        }

        //*Is speaking
        if(BackroomsVoicechatPlugin.speakingTime.containsKey(this.player.getUuid()) && BackroomsVoicechatPlugin.speakingTime.get(this.player.getUuid()) == this.prevSpeakingTime) {
            if(this.isSpeaking()) {
                this.speakingBuffer--;
                if (this.speakingBuffer <= 0) {
                    this.setSpeaking(false);
                    this.speakingBuffer = 80;
                }
            }
        } else {
            this.speakingBuffer = 80;
        }

        //*Cast him to the Backrooms
        if (checkBackroomsTeleport()) return;

        Optional<BackroomsLevel> backroomsLevel = BackroomsLevels.getLevel(this.player.getWorld());

        if (backroomsLevel.isPresent()) {
            BackroomsLevel level = backroomsLevel.get();

            if (currentTransition == null) {
                List<BackroomsLevel.LevelTransition> teleports = level.checkForTransition(this, this.player.getWorld());

                if (!teleports.isEmpty()) {
                    currentTransition = teleports.get(0);
                }
            }

            if (level == BackroomsLevels.LEVEL324_BACKROOMS_LEVEL && this.player.getWorld().getBlockState(this.player.getBlockPos().offset(Direction.DOWN, 3)).isOf(Blocks.RED_WOOL)) {
                this.player.teleport(this.player.getX(), this.player.getY() - 64, this.player.getZ());
            }

            if (level == BackroomsLevels.LEVEL324_BACKROOMS_LEVEL && this.player.getWorld().getBlockState(this.player.getBlockPos().offset(Direction.DOWN, 3)).isOf(Blocks.YELLOW_WOOL)) {
                this.player.teleport(this.player.getX(), this.player.getY() + 64, this.player.getZ());
            }

            //*Falling out of a level is one more exit: it follows the route too, so no level is visited twice
            BackroomsLevel routeNext = BackroomsRoute.next(level, this.routeSeed);
            if (this.player.getY() < level.getFallOutY() && (routeNext != null || level.getFallbackLevel() != null) && currentTransition == null) {
                BackroomsLevel fallback = routeNext != null ? routeNext : level.getFallbackLevel();
                currentTransition = new BackroomsLevel.LevelTransition(
                    40,
                    (teleport, tick) -> {
                        if (!teleport.playerComponent().player.getWorld().isClient() && tick == 20) {
                            SPBRevamped.sendBlackScreenPacket((ServerPlayerEntity) teleport.playerComponent().player, 20, true, false);
                        }
                    },
                    new BackroomsLevel.CrossDimensionTeleport(this, fallback == BackroomsLevels.OVERWORLD_REPRESENTING_BACKROOMS_LEVEL
                            ? OverworldRepresentingBackroomsLevel.returnPosition(this) : fallback.getSafeSpawnPos(), level, fallback),
                    (teleport, tick) -> {});
            }

            BlockPos checkpointPos = level.findCheckpoint(this.player.getPos());
            if (checkpointPos != null) {
                GlobalPos candidate = GlobalPos.create(this.player.getWorld().getRegistryKey(), checkpointPos);
                if (!candidate.equals(this.checkpoint)) {
                    this.setCheckpoint(candidate);
                }
            }
        }

        // ������ Why is the � a question mark for me?

        if (currentTransition != null) {
            if (teleportingTimer == -1) {
                this.setTeleportingTimer(currentTransition.duration());
            }

            if (teleportingTimer == 0) {
                ServerWorld destination = this.player.getWorld().getServer().getWorld(currentTransition.teleport().to().getWorldKey());
                TeleportTarget target = new TeleportTarget(currentTransition.teleport().pos(), currentTransition.teleport().playerComponent().player.getVelocity(), currentTransition.teleport().playerComponent().player.getYaw(), currentTransition.teleport().playerComponent().player.getPitch());

                if (currentTransition.teleport().to() == BackroomsLevels.OVERWORLD_REPRESENTING_BACKROOMS_LEVEL) {
                    currentTransition.teleport().playerComponent().sync();

                    if (this.player.getWorld().getGameRules().getBoolean(ModGamerules.STUCK_IN_BACKROOMS)) {
                        destination = this.player.getWorld().getServer().getWorld(BackroomsLevels.LEVEL0_BACKROOMS_LEVEL.getWorldKey());
                        target = new TeleportTarget(BackroomsLevels.LEVEL0_BACKROOMS_LEVEL.getSpawnPos(), currentTransition.teleport().playerComponent().player.getVelocity(), currentTransition.teleport().playerComponent().player.getYaw(), currentTransition.teleport().playerComponent().player.getPitch());
                    }
                }

                currentTransition.teleport().from().transitionOut(currentTransition.teleport());
                FabricDimensions.teleport(currentTransition.teleport().playerComponent().player, destination, target);
                currentTransition.teleport().to().transitionIn(currentTransition.teleport());

                currentTransition = null;
            }
        }

        if (teleportingTimer >= 0) {
            if (currentTransition != null) {
                currentTransition.callback().tick(currentTransition.teleport(), teleportingTimer);
            }
            this.setTeleportingTimer(teleportingTimer - 1);
        }

        if (BackroomsLevels.isInBackroomsLevel(player.getWorld(), BackroomsLevels.LEVEL324_BACKROOMS_LEVEL) && player.getPos().subtract(0, 64, 0).lengthSquared() > 10000 && player.getPos().y > 60) {
            summonSmilers();
        }

        //*Update Entity Visibility
        updateEntityVisibility();

        if(BackroomsVoicechatPlugin.speakingTime.containsKey(this.player.getUuid())) {
            this.prevSpeakingTime = BackroomsVoicechatPlugin.speakingTime.get(this.player.getUuid());
        }
        
        shouldSync();
    }

    private void summonSmilers() {
        if (this.smilerSpawnDelay < 0) {
            SmilerEntity smiler = ModEntities.SMILER_ENTITY.create(this.player.getWorld());

            BlockPos.Mutable mutable = new BlockPos.Mutable();
            float randomAngle = random.nextFloat() * 360.0f;
            Vec3d spawnPos = new Vec3d(0, 0, 15).rotateY(randomAngle).add(player.getPos());
            if (!this.player.getWorld().getBlockState(mutable.set(spawnPos.x, spawnPos.y, spawnPos.z)).blocksMovement()) {
                smiler.refreshPositionAndAngles(Math.floor(spawnPos.x) + 0.5f, spawnPos.y, Math.floor(spawnPos.z) + 0.5f, 0.0f, 0.0f);
                this.player.getWorld().spawnEntity(smiler);
                smilerSpawnDelay = 80;
            }
        }

        smilerSpawnDelay--;
    }

    private void updateStamina() {
        EntityAttributeInstance attributeInstance = this.player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if(attributeInstance != null) {
            int prevStamina = this.stamina;
            if(!this.player.isCreative() && !this.player.isSpectator()){
                if(this.player.isSprinting()) {
                    this.stamina--;
                } else {
                    this.stamina = Math.min(this.stamina + 1, 300);
                }
            } else {
                this.stamina = 300;
            }

            if(this.stamina <= 0){
                this.stamina = 0;
                this.setTired(true);
            }

            if(this.isTired()){
                this.player.setSprinting(false);
                this.player.addExhaustion(0.05f);
                if(this.stamina > 200){
                    this.setTired(false);
                }
            } else if(!((ServerPlayNetworkSprint)((ServerPlayerEntity)this.player).networkHandler).getShouldStopSprinting()){
                this.player.setSprinting(true);
            }

            if ((!player.isSneaking() && !player.isSprinting()) || this.isTired()) {
                if(!attributeInstance.hasModifier(SLOW_SPEED_MODIFIER)) {
                    attributeInstance.addTemporaryModifier(SLOW_SPEED_MODIFIER);
                }
            } else if(attributeInstance.hasModifier(SLOW_SPEED_MODIFIER)) {
                attributeInstance.removeModifier(SLOW_SPEED_MODIFIER);
            }
            //*Mod by 20 to reduce packet count
            if(prevStamina != this.stamina && this.stamina % 20 == 0){
                //*Only sync with the specific player since other players don't need to know your stamina
                InitializeComponents.PLAYER.syncWith((ServerPlayerEntity) this.player, (ComponentProvider) this.player);
            }

        }
    }

    private boolean checkBackroomsTeleport() {
        if (this.player.isInsideWall()) {
            if (this.player.getWorld().getRegistryKey() == World.OVERWORLD && !this.isDoingCutscene()) {
                suffocationTimer++;
                if (suffocationTimer == 1) {
                    SPBRevamped.sendPersonalPlaySoundPacket((ServerPlayerEntity) this.player, ModSounds.GLITCH, 1.0f, 1.0f);
                    this.playingGlitchSound = true;
                }

                if (suffocationTimer >= 40) {
                    if (!this.acceptedAIModels) {
                        //The player has not accepted the download of the mod's voice and AI models yet.
                        //Ask the client to show the consent GUI instead of teleporting into the backrooms.
                        if (suffocationTimer == 40) {
                            SPBRevamped.sendConsentScreenPacket((ServerPlayerEntity) this.player);
                        }
                        return false;
                    }

                    RegistryKey<World> registryKey = BackroomsLevels.LEVEL0_WORLD_KEY;
                    ServerWorld backrooms = this.player.getWorld().getServer().getWorld(registryKey);
                    if (backrooms == null) {
                        return true;
                    }

                    // Everyone falls into Level 0 with a camcorder. One they already carry comes along instead of
                    // being stored with the overworld inventory, so there is only ever one.
                    ItemStack camera = new ItemStack(ModItems.CAMERA);
                    for (int i = 0; i < this.player.getInventory().size(); i++) {
                        if (this.player.getInventory().getStack(i).isOf(ModItems.CAMERA)) {
                            camera = this.player.getInventory().getStack(i).copy();
                            break;
                        }
                    }
                    this.player.getInventory().remove(stack -> stack.isOf(ModItems.CAMERA), -1, this.player.playerScreenHandler.getCraftingInput());

                    this.savePlayerInventory();
                    this.sync();
                    this.player.getInventory().clear();
                    this.player.getInventory().insertStack(camera);
                    TeleportTarget target = new TeleportTarget(new Vec3d(1.5, 22, 1.5), Vec3d.ZERO, this.player.getYaw(), this.player.getPitch());
                    FabricDimensions.teleport(this.player, backrooms, target);
                    this.setDoingCutscene(true);
                    this.sync();
                    suffocationTimer = 0;
                }
            }
        } else {
            if (this.playingGlitchSound) {
                StopSoundS2CPacket stopSoundS2CPacket = new StopSoundS2CPacket(new Identifier(SPBRevamped.MOD_ID, "glitch"), null);
                ((ServerPlayerEntity) this.player).networkHandler.sendPacket(stopSoundS2CPacket);
            }
            this.playingGlitchSound = false;
            suffocationTimer = 0;
        }
        return false;
    }

    private void updateEntityVisibility() {
        if(this.isTalkingTooLoud() && this.talkingTooLoudTimer >= 0){
            this.setVisibleToEntity(true);
            this.talkingTooLoudTimer--;
        } else if(this.talkingTooLoudTimer <= 0){
            this.setVisibleToEntity(false);
            this.setTalkingTooLoud(false);
            this.resetTalkingTooLoudTimer();
        }

        if(!this.isVisibleToEntity()) {
            float speed = this.player.horizontalSpeed - this.player.prevHorizontalSpeed;

            if (!this.player.isSneaking() && speed != 0) {
                this.visibilityTimer--;
            }
            if (this.player.isSprinting()) {
                this.visibilityTimer--;
            }

            //reset timer if the player is not moving
            if(speed == 0){
                this.visibilityTimer = 15;
            }

            if (this.visibilityTimer <= 0) {
                this.visibilityTimerCooldown = 20;
                this.setVisibleToEntity(true);
            }
        } else {
            if(this.visibilityTimerCooldown > 0){
                this.visibilityTimerCooldown--;
            } else {
                this.visibilityTimer = 15;
                if(!this.isTalkingTooLoud()) {
                    this.setVisibleToEntity(false);
                }
            }
        }
    }

    public void justChanged() {
        this.sync();
    }

    private void shouldSync() {
        boolean sync = this.prevFlashLightOn != this.flashLightOn;

        if (sync) {
            this.sync();
        }
    }

    private void getPrevSettings() {
        this.prevFlashLightOn = this.flashLightOn;
    }
}
