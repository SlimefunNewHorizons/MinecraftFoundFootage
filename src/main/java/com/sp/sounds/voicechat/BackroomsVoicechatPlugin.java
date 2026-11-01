package com.sp.sounds.voicechat;

import com.sp.SPBRevamped;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.compat.modmenu.ConfigStuff;
import com.sp.entity.voice.VictimPhrases;
import com.sp.entity.voice.VoiceCloner;
import com.sp.entity.voice.VoiceTranscriber;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.*;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.voice.common.Utils;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class BackroomsVoicechatPlugin implements VoicechatPlugin {
    public static VoicechatServerApi voicechatApi;
    private ConcurrentHashMap<UUID, OpusDecoder> decoders;
    public static ConcurrentHashMap<UUID, Float> speakingTime;
    public static Map<UUID, Vector<short[]>> randomSpeakingList;
    private static Map<UUID, short[]> totalSoundData;
    private static Map<UUID, Boolean> wasSpeaking;

    private static final int MIN_VOICE_SAMPLE_SAMPLES = 48000;

    /**
     * Something a player said (voice) or wrote (chat) that a SkinWalker may hear.
     *
     * @param text     what was said, or null if the SkinWalker only knows the player talked (no transcription).
     * @param language language of the text if known for sure (speech-to-text model language), otherwise null.
     */
    public record HeardInput(UUID playerUuid, String playerName, RegistryKey<World> world, Vec3d pos, String text, String language, long timeMs) {
        public boolean isVoiceOnly() {
            return text == null;
        }

        /**
         * The input in the format the {@link com.sp.entity.voice.ResponseGenerator} expects.
         */
        public String asResponseInput() {
            return isVoiceOnly() ? "voice_" + playerName.toLowerCase(Locale.ROOT) : text;
        }
    }

    private static final List<HeardInput> heardInputs = new ArrayList<>();
    private static final int MAX_INPUTS = 20;
    private static final long INPUT_LIFETIME_MS = 20_000;

    @Override
    public String getPluginId() {
        return SPBRevamped.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        decoders = new ConcurrentHashMap<>();
        speakingTime = new ConcurrentHashMap<>();
        randomSpeakingList = new ConcurrentHashMap<>();
        totalSoundData = new ConcurrentHashMap<>();
        wasSpeaking = new ConcurrentHashMap<>();
        clearInputs();
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            registration.registerEvent(OpenALSoundEvent.class, this::SkinWalkerVoicesPitchDown);
            registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, this::processLocationalSound);
            registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, this::processEntitySound);
        }
        registration.registerEvent(MicrophonePacketEvent.class, this::recordPlayersTalking);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onServerStop);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::playerDisconnect);
        registration.registerEvent(PlayerConnectedEvent.class, this::playerConnect);
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStart);
    }

    private void SkinWalkerVoicesPitchDown(OpenALSoundEvent openALSoundEvent) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null) {
            return;
        }

        if (client.player.getWorld() == null) {
            return;
        }

        UUID channelID = openALSoundEvent.getChannelId();

        if (channelID == null) {
            return;
        }

        try {
            int source = openALSoundEvent.getSource();
            applyRoboticVoiceEffect(source);
        } catch (Exception e) {
            SPBRevamped.LOGGER.error("Error applying voice effect: {}", String.valueOf(e));
        }
    }

    private void applyRoboticVoiceEffect(int source) {
        AL10.alSourcef(source, AL10.AL_PITCH, 0.78f);

        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_LOWPASS);
        AL10.alSourcef(source, EXTEfx.AL_LOWPASS_GAIN, 0.6f);
        AL10.alSourcef(source, EXTEfx.AL_LOWPASS_GAINHF, 0.25f);

        AL10.alSourcef(source, AL10.AL_GAIN, 1.0f);
    }

    private void processLocationalSound(ClientReceiveSoundEvent.LocationalSound event) {
        short[] audio = event.getRawAudio();
        if (audio == null || audio.length == 0) return;
        event.setRawAudio(processAudioRobotic(audio));
    }

    private void processEntitySound(ClientReceiveSoundEvent.EntitySound event) {
        short[] audio = event.getRawAudio();
        if (audio == null || audio.length == 0) return;
        event.setRawAudio(processAudioRobotic(audio));
    }

    private short[] processAudioRobotic(short[] audio) {
        short[] processed = new short[audio.length];
        double pitchFactor = 0.78;
        int sampleRate = 48000;
        double pitchShift = pitchFactor;

        for (int i = 0; i < audio.length; i++) {
            double sample = audio[i] / 32768.0;

            sample *= 0.95;

            double quantize = 0.3;
            double levels = 1.0 / (quantize + 0.01);
            sample = Math.round(sample * levels) / levels;

            sample = Math.max(-1.0, Math.min(1.0, sample));

            processed[i] = (short)(sample * 32767);
        }

        return processed;
    }

    private void recordPlayersTalking(MicrophonePacketEvent microphonePacketEvent) {
        VoicechatConnection senderConnection = microphonePacketEvent.getSenderConnection();
        if (senderConnection == null) {
            return;
        }

        if (!(senderConnection.getPlayer().getPlayer() instanceof PlayerEntity player)) {
            return;
        }

        byte[] encodedData = microphonePacketEvent.getPacket().getOpusEncodedData();

        byte[] copyData = encodedData.clone();

        if (copyData.length != 0) {
            if (!decoders.containsKey(player.getUuid())) {
                decoders.put(player.getUuid(), microphonePacketEvent.getVoicechat().createDecoder());
            }

            OpusDecoder decoder = decoders.get(player.getUuid());

            short[] data = decoder.decode(encodedData);

            PlayerComponent component = InitializeComponents.PLAYER.get(player);
            if(!component.shouldBeMuted()) {
                component.setSpeaking(true);

                boolean prevWasSpeaking = wasSpeaking.getOrDefault(player.getUuid(), false);
                wasSpeaking.put(player.getUuid(), true);

                if (!prevWasSpeaking && !ConfigStuff.sttEnabled) {
                    detectVoiceInput(player);
                }

                //Update the amount of time that the players are talking for the skinstealer to determine who to take
                if(!player.isSpectator() && !player.isCreative()) {
                    if (!speakingTime.containsKey(player.getUuid())) {
                        speakingTime.put(player.getUuid(), 0.0f);
                    }

                    speakingTime.put(player.getUuid(), speakingTime.get(player.getUuid()) + 0.0001f);

                    //If the player is talking too loud, make them visible to the skinwalker
                    if (!component.isVisibleToEntity()) {
                        double volume = Utils.dbToPerc(Utils.getHighestAudioLevel(data));

                        if (volume >= 0.8) {
                            component.setTalkingTooLoud(true);
                            component.resetTalkingTooLoudTimer();
                        }
                    }
                } else if(!component.isBeingCaptured() && !component.hasBeenCaptured()) {
                    speakingTime.remove(player.getUuid());
                }
            } else {
                microphonePacketEvent.cancel();
                return;
            }

            short[] totalData;
            if (totalSoundData.containsKey(player.getUuid())) {
                totalData = totalSoundData.get(player.getUuid());
            } else {
                totalData = new short[0];
            }

            int maxSamples = 48000 * 10; // Cap at 10 seconds to prevent unbounded heap growth
            if (totalData.length < maxSamples) {
                int toCopy = Math.min(data.length, maxSamples - totalData.length);
                short[] result = new short[totalData.length + toCopy];
                System.arraycopy(totalData, 0, result, 0, totalData.length);
                System.arraycopy(data, 0, result, totalData.length, toCopy);
                totalSoundData.put(player.getUuid(), result);
            }

            return;
        }

        wasSpeaking.put(player.getUuid(), false);

        if (decoders.containsKey(player.getUuid())) {
            OpusDecoder decoder = decoders.get(player.getUuid());
            if (decoder != null) {
                decoder.resetState();
            }
        }

        short[] recorded = totalSoundData.get(player.getUuid());
        if (recorded != null && recorded.length >= MIN_VOICE_SAMPLE_SAMPLES) {
            Random random = Random.create();

            Vector<short[]> soundList = randomSpeakingList.getOrDefault(player.getUuid(), new Vector<>());

            if (soundList.size() < 20) {
                soundList.add(recorded);
            } else if (soundList.size() > 0) {
                soundList.set(random.nextBetween(0, soundList.size() - 1), recorded);
            }

            randomSpeakingList.put(player.getUuid(), soundList);
        }

        if (ConfigStuff.sttEnabled && recorded != null) {
            if (recorded.length >= MIN_VOICE_SAMPLE_SAMPLES) {
                VoiceTranscriber.transcribeAsync(player, recorded, 48000);
            } else if (recorded.length > 0) {
                pushVoiceInput(player);
            }
        }

        totalSoundData.remove(player.getUuid());
    }

    /**
     * The player talked, but there is no transcription of what they said.
     */
    public static void pushVoiceInput(PlayerEntity player) {
        pushInput(player, null, null);
    }

    private static void detectVoiceInput(PlayerEntity player) {
        pushVoiceInput(player);
    }

    /**
     * The player said (transcribed voice) or wrote (chat) the given text.
     *
     * @param language the language of the text if known for sure, null to detect it from the text.
     */
    public static void receiveChatInput(PlayerEntity player, String message, String language) {
        if (message == null || message.trim().isEmpty()) return;
        pushInput(player, message.toLowerCase(Locale.ROOT).trim(), language);
    }

    public static void receiveChatInput(PlayerEntity player, String message) {
        receiveChatInput(player, message, null);
    }

    private static synchronized void pushInput(PlayerEntity player, String text, String language) {
        if (player == null) return;
        // The player's position is read off the game thread, a slightly stale value is fine here.
        HeardInput input = new HeardInput(player.getUuid(), player.getName().getString(), player.getWorld().getRegistryKey(),
                player.getPos(), text, language, System.currentTimeMillis());

        if (heardInputs.size() >= MAX_INPUTS) {
            heardInputs.remove(0);
        }
        heardInputs.add(input);
    }

    /**
     * Takes the oldest input that matches the filter out of the queue, so only one SkinWalker answers it.
     * Inputs older than {@link #INPUT_LIFETIME_MS} are thrown away.
     */
    public static synchronized Optional<HeardInput> claimInput(Predicate<HeardInput> filter) {
        long now = System.currentTimeMillis();
        heardInputs.removeIf(input -> now - input.timeMs() > INPUT_LIFETIME_MS);

        Iterator<HeardInput> iterator = heardInputs.iterator();
        while (iterator.hasNext()) {
            HeardInput input = iterator.next();
            if (filter.test(input)) {
                iterator.remove();
                return Optional.of(input);
            }
        }
        return Optional.empty();
    }

    public static synchronized void clearInputs() {
        heardInputs.clear();
    }

    private void onServerStart(VoicechatServerStartedEvent voicechatServerStartedEvent) {
        voicechatApi = voicechatServerStartedEvent.getVoicechat();
    }

    private void playerConnect(PlayerConnectedEvent playerConnectedEvent) {
        PlayerEntity player = (PlayerEntity) playerConnectedEvent.getConnection().getPlayer().getPlayer();
        if(!player.isSpectator() && !player.isCreative()) {
            speakingTime.put(playerConnectedEvent.getConnection().getPlayer().getUuid(), 0.0f);
        }
    }

    private void playerDisconnect(PlayerDisconnectedEvent playerDisconnectedEvent) {
        UUID playerUUID = playerDisconnectedEvent.getPlayerUuid();
        this.removePlayerDecoder(playerUUID, decoders.get(playerUUID));
        speakingTime.remove(playerUUID);
        totalSoundData.remove(playerUUID);
        wasSpeaking.remove(playerUUID);
        randomSpeakingList.remove(playerUUID);
        VictimPhrases.forget(playerUUID);
        VoiceCloner.forget(playerUUID);
    }

    private void onServerStop(VoicechatServerStoppedEvent voicechatServerStoppedEvent) {
        if (decoders != null) {
            decoders.forEach((uuid, decoder) -> this.removePlayerDecoder(uuid, decoder));
        }
        if (speakingTime != null) {
            speakingTime.clear();
        }
        VoiceTranscriber.shutdown();
    }

    private void removePlayerDecoder(UUID uuid, OpusDecoder decoder){
        if(decoder != null) {
            decoder.close();
        }
        decoders.remove(uuid);
    }

}