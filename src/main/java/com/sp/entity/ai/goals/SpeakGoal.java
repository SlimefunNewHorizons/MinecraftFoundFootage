package com.sp.entity.ai.goals;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.SkinWalkerComponent;
import com.sp.entity.custom.SkinWalkerEntity;
import com.sp.entity.voice.*;
import com.sp.sounds.voicechat.BackroomsVoicechatPlugin;
import com.sp.sounds.voicechat.BackroomsVoicechatPlugin.HeardInput;
import com.voicejavaai.core.RoboticVoice;
import com.voicejavaai.voice.VoiceConfig;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.plugins.impl.ServerLevelImpl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.text.Normalizer;
import java.util.Optional;
import java.util.UUID;

public class SpeakGoal extends Goal {
    /** How far away (in blocks) the SkinWalker can hear players talking or chatting. */
    private static final double HEARING_RANGE = 48.0;
    /** Simple Voice Chat plays 48 kHz mono audio. */
    private static final int VOICECHAT_SAMPLE_RATE = 48000;

    private final Random random = Random.create();
    private final SkinWalkerEntity entity;
    private final SkinWalkerComponent component;
    private final RoboticVoice roboticVoice;
    private final VoiceConfig voiceConfig;
    private int actCooldown;
    private int inputCheckCooldown = 0;
    public ServerLevelImpl serverLevel = null;
    public LocationalAudioChannel audioChannel;
    private RegistryKey<World> audioChannelWorld;
    public AudioPlayer audioPlayer;

    public SpeakGoal(SkinWalkerEntity entity) {
        this.entity = entity;
        this.component = InitializeComponents.SKIN_WALKER.get(entity);
        this.roboticVoice = RoboticVoice.getInstance();
        this.voiceConfig = VoiceConfig.robotic();
        this.voiceConfig.setBasePitch(100 + Math.random() * 40);
    }

    @Override
    public boolean canStart() {
        return this.entity.isAlive();
    }

    @Override
    public void start() {
        this.actCooldown = getTickCount(40);
        this.inputCheckCooldown = 0;
    }

    @Override
    public void stop() {
        if (this.audioPlayer != null) {
            this.audioPlayer.stopPlaying();
        }
    }

    @Override
    public void tick() {
        if (!this.entity.getWorld().isClient) {
            if (this.component.shouldBeginReveal()) {
                if (this.audioPlayer != null) {
                    this.audioPlayer.stopPlaying();
                }
                return;
            }

            ResponseQueue responseQueue = component.getResponseQueue();

            short[] queuedAudio = responseQueue.poll();
            if (queuedAudio != null && queuedAudio.length > 0) {
                playAudio(queuedAudio);
                return;
            }

            // Answering what players say does not wait for the idle chatter cooldown.
            if (this.inputCheckCooldown > 0) {
                this.inputCheckCooldown--;
            } else if (!responseQueue.hasPending()) {
                this.inputCheckCooldown = 10;
                if (checkForPlayerInput()) {
                    this.setRandomActCoolDown();
                    return;
                }
            }

            if (this.actCooldown > 0) {
                this.actCooldown--;
                return;
            }

            if (responseQueue.hasPending()) {
                return;
            }

            this.playRandomPlayerSounds();
            this.setRandomActCoolDown();
        }
    }

    /**
     * Takes the oldest thing a nearby player said or wrote and queues a spoken answer.
     *
     * @return true if an answer was queued.
     */
    private boolean checkForPlayerInput() {
        RegistryKey<World> world = this.entity.getWorld().getRegistryKey();
        double rangeSquared = HEARING_RANGE * HEARING_RANGE;

        Optional<HeardInput> heard = BackroomsVoicechatPlugin.claimInput(input ->
                input.world().equals(world) && input.pos().squaredDistanceTo(this.entity.getPos()) <= rangeSquared);

        if (heard.isEmpty()) {
            return false;
        }

        HeardInput input = heard.get();
        String responseInput = input.asResponseInput();
        component.setLastHeardInput(responseInput);

        // Speech-to-text output is always in the model's language; plain "someone talked" falls back to it too.
        String languageHint = input.language() != null ? input.language()
                : input.isVoiceOnly() ? VoiceTranscriber.modelLanguage() : null;

        ResponseGenerator.ResponseResult result = component.getResponseGenerator().generateResponse(responseInput, this.entity, languageHint);
        if (result == null) {
            return false;
        }

        String responseText = result.text;
        if (input.isVoiceOnly() && component.isInTrueForm()) {
            responseText = getTrueFormVoiceResponse(Math.random(), result.language);
        }

        String latencyInput = input.isVoiceOnly() ? "voice" : input.text();
        int baseLatency = new LatencyCalculator().calculateLatency(latencyInput, component.isInTrueForm(), 0);
        int latency = Math.max(100, Math.min(8000, baseLatency + result.latencyModifier));

        short[] responseAudio = synthesize(responseText);
        if (responseAudio.length == 0) {
            return false;
        }

        component.getResponseQueue().enqueue(responseAudio, latency / 50);
        return true;
    }

    /**
     * Speaks the text with the robotic voice and converts it to the format Simple Voice Chat plays.
     */
    private short[] synthesize(String text) {
        short[] audio = this.roboticVoice.speak(toSpeakableText(text), this.voiceConfig);
        if (audio == null || audio.length == 0) {
            return new short[0];
        }
        // The synthesizer works at 22.05 kHz; played as 48 kHz it would sound twice as fast and high pitched.
        return resample(audio, this.voiceConfig.getSampleRate(), VOICECHAT_SAMPLE_RATE);
    }

    /**
     * The phoneme mapper only knows the letters a-z and drops everything else, so "más" would become "ms".
     * Accents are stripped instead and special letters spelled out.
     */
    private static String toSpeakableText(String text) {
        String lower = text.toLowerCase()
                .replace("ß", "ss")
                .replace("œ", "oe")
                .replace("æ", "ae");
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static short[] resample(short[] input, int fromRate, int toRate) {
        if (fromRate == toRate || input.length == 0) {
            return input;
        }

        int outputLength = (int) ((long) input.length * toRate / fromRate);
        short[] output = new short[outputLength];
        double step = (double) fromRate / toRate;

        for (int i = 0; i < outputLength; i++) {
            double position = i * step;
            int index = (int) position;
            double fraction = position - index;
            short a = input[Math.min(index, input.length - 1)];
            short b = input[Math.min(index + 1, input.length - 1)];
            output[i] = (short) Math.round(a + (b - a) * fraction);
        }

        return output;
    }

    private String getTrueFormVoiceResponse(double roll, String lang) {
        switch (lang) {
            case "en":
                if (roll < 0.15) return "i hear you";
                if (roll < 0.25) return "keep talking";
                if (roll < 0.35) return "i can hear you";
                if (roll < 0.45) return "your voice sounds familiar";
                if (roll < 0.55) return "don't stop talking";
                if (roll < 0.65) return "i like your voice";
                if (roll < 0.75) return "speak louder";
                if (roll < 0.85) return "i can't hear you well";
                return "stay there";
            case "de":
                if (roll < 0.15) return "ich höre dich";
                if (roll < 0.25) return "sprich weiter";
                if (roll < 0.35) return "ich kann dich hören";
                if (roll < 0.45) return "deine stimme klingt vertraut";
                if (roll < 0.55) return "hör nicht auf zu reden";
                if (roll < 0.65) return "deine stimme gefällt mir";
                if (roll < 0.75) return "sprich lauter";
                if (roll < 0.85) return "ich höre dich nicht gut";
                return "bleib da";
            case "fr":
                if (roll < 0.15) return "je t'entends";
                if (roll < 0.25) return "continue à parler";
                if (roll < 0.35) return "je peux t'entendre";
                if (roll < 0.45) return "ta voix m'est familière";
                if (roll < 0.55) return "arrête pas de parler";
                if (roll < 0.65) return "j'aime ta voix";
                if (roll < 0.75) return "parle plus fort";
                if (roll < 0.85) return "je t'entends pas bien";
                return "reste là";
            case "it":
                if (roll < 0.15) return "ti sento";
                if (roll < 0.25) return "continua a parlare";
                if (roll < 0.35) return "posso sentirti";
                if (roll < 0.45) return "la tua voce sembra familiare";
                if (roll < 0.55) return "non smettere di parlare";
                if (roll < 0.65) return "mi piace la tua voce";
                if (roll < 0.75) return "parla più forte";
                if (roll < 0.85) return "non ti sento bene";
                return "rimani lì";
            case "pt":
                if (roll < 0.15) return "eu te ouço";
                if (roll < 0.25) return "continue falando";
                if (roll < 0.35) return "posso te ouvir";
                if (roll < 0.45) return "sua voz parece familiar";
                if (roll < 0.55) return "não pare de falar";
                if (roll < 0.65) return "gosto da sua voz";
                if (roll < 0.75) return "fale mais alto";
                if (roll < 0.85) return "não te ouço bem";
                return "fique aí";
            default:
                if (roll < 0.15) return "te escucho";
                if (roll < 0.25) return "sigue hablando";
                if (roll < 0.35) return "puedo oírte";
                if (roll < 0.45) return "tu voz suena familiar";
                if (roll < 0.55) return "no pares de hablar";
                if (roll < 0.65) return "me gusta tu voz";
                if (roll < 0.75) return "habla más fuerte";
                if (roll < 0.85) return "no te escucho bien";
                return "quédate ahí";
        }
    }

    private void setRandomActCoolDown(){
        int base = component.isInTrueForm() ? 100 : 200;
        int variance = component.isInTrueForm() ? 200 : 400;
        int ticks = base + (int)(Math.random() * variance);
        this.actCooldown = getTickCount(ticks);
    }

    private void playRandomPlayerSounds() {
        if (this.entity.getServer() == null) return;

        VoicechatServerApi api = BackroomsVoicechatPlugin.voicechatApi;
        if (api == null) return;

        UUID targetUUID = this.component.getTargetPlayerUUID();
        if (targetUUID == null) return;

        var clips = BackroomsVoicechatPlugin.randomSpeakingList.get(targetUUID);
        if (clips == null || clips.isEmpty()) return;

        short[] data = clips.get(random.nextBetween(0, clips.size() - 1));
        playAudio(data);
    }

    private void playAudio(short[] data) {
        if (data == null || data.length == 0) return;

        VoicechatServerApi api = BackroomsVoicechatPlugin.voicechatApi;
        if (api == null || this.entity.getServer() == null) return;

        // (Re)create the channel when the SkinWalker is in a different world than the channel was made for.
        RegistryKey<World> worldKey = this.entity.getWorld().getRegistryKey();
        if (this.audioChannel == null || !worldKey.equals(this.audioChannelWorld)) {
            ServerWorld world = this.entity.getServer().getWorld(worldKey);
            if (world == null) return;

            if (this.audioPlayer != null) {
                this.audioPlayer.stopPlaying();
                this.audioPlayer = null;
            }

            this.serverLevel = new ServerLevelImpl(world);
            this.audioChannel = api.createLocationalAudioChannel(
                this.entity.getUuid(),
                this.serverLevel,
                api.createPosition(this.entity.getX(), this.entity.getY(), this.entity.getZ())
            );
            this.audioChannelWorld = worldKey;
        }

        if (this.audioChannel == null) return;

        this.audioChannel.updateLocation(
            api.createPosition(this.entity.getX(), this.entity.getY(), this.entity.getZ())
        );

        if (this.audioPlayer != null && this.audioPlayer.isPlaying()) {
            this.audioPlayer.stopPlaying();
        }

        this.audioPlayer = api.createAudioPlayer(this.audioChannel, api.createEncoder(), data);
        this.audioPlayer.startPlaying();
    }
}
