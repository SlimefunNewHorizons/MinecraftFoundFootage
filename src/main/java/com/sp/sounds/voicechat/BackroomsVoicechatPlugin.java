package com.sp.sounds.voicechat;

import com.sp.SPBRevamped;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.*;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.voice.common.Utils;
import de.maxhenkel.voicechat.voice.common.Utils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.random.Random;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class BackroomsVoicechatPlugin implements VoicechatPlugin {
    public static VoicechatServerApi voicechatApi;
    private ConcurrentHashMap<UUID, OpusDecoder> decoders;
    public static ConcurrentHashMap<UUID, Float> speakingTime;
    public static Map<UUID, Vector<short[]>> randomSpeakingList;
    private static Map<UUID, short[]> totalSoundData;
    private static Map<UUID, Integer> ticks;
    private static Map<UUID, Boolean> wasSpeaking;

    public static List<String> lastTranscribedInputs = new CopyOnWriteArrayList<>();
    private static final int MAX_INPUTS = 20;

    @Override
    public String getPluginId() {
        return SPBRevamped.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        decoders = new ConcurrentHashMap<>();
        speakingTime = new ConcurrentHashMap<>();
        randomSpeakingList = new ConcurrentHashMap<>();
        ticks = new ConcurrentHashMap<>();
        totalSoundData = new ConcurrentHashMap<>();
        wasSpeaking = new ConcurrentHashMap<>();
        lastTranscribedInputs = new CopyOnWriteArrayList<>();
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(OpenALSoundEvent.class, this::SkinWalkerVoicesPitchDown);
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, this::processLocationalSound);
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, this::processEntitySound);
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


        if (ticks.containsKey(player.getUuid())) {
            ticks.put(player.getUuid(), ticks.get(player.getUuid()) + 1);
        } else {
            ticks.put(player.getUuid(), 0);
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

                if (!prevWasSpeaking) {
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

            short[] result = new short[totalData.length + data.length];
            System.arraycopy(totalData, 0, result, 0, totalData.length);
            System.arraycopy(data, 0, result, totalData.length, data.length);
            totalData = result;
            totalSoundData.put(player.getUuid(), totalData);

            return;
        }

        wasSpeaking.put(player.getUuid(), false);

        if (decoders.containsKey(player.getUuid())) {
            OpusDecoder decoder = decoders.get(player.getUuid());
            if (decoder != null) {
                decoder.resetState();
            }
        }

        if (ticks.get(player.getUuid()) != null && ticks.get(player.getUuid()) > 40  && ticks.get(player.getUuid()) < 200) {
            Random random = Random.create();

            Vector<short[]> soundList = randomSpeakingList.getOrDefault(player.getUuid(), new Vector<>());

            if (totalSoundData.containsKey(player.getUuid()) && totalSoundData.get(player.getUuid()) != null) {
                if (soundList.size() < 20) {
                    soundList.add(totalSoundData.get(player.getUuid()));
                } else if (soundList.size() > 0) {
                    soundList.set(random.nextBetween(0, soundList.size() - 1), totalSoundData.get(player.getUuid()));
                }

                randomSpeakingList.put(player.getUuid(), soundList);
            }
        }

        ticks.put(player.getUuid(), 0);
        totalSoundData.remove(player.getUuid());
    }

    private void detectVoiceInput(PlayerEntity player) {
        if (lastTranscribedInputs.size() >= MAX_INPUTS) {
            lastTranscribedInputs.remove(0);
        }
        String input = "voice_" + player.getName().getString().toLowerCase();
        lastTranscribedInputs.add(input);
    }

    public static void receiveChatInput(String playerName, String message) {
        if (message == null || message.trim().isEmpty()) return;
        if (lastTranscribedInputs.size() >= MAX_INPUTS) {
            lastTranscribedInputs.remove(0);
        }
        String input = playerName.toLowerCase() + ": " + message.toLowerCase().trim();
        lastTranscribedInputs.add(input);
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
    }

    private void onServerStop(VoicechatServerStoppedEvent voicechatServerStoppedEvent) {
        decoders.forEach(this::removePlayerDecoder);
        speakingTime.clear();
    }

    private void removePlayerDecoder(UUID uuid, OpusDecoder decoder){
        if(decoder != null) {
            decoder.close();
        }
        decoders.remove(uuid);
    }

}