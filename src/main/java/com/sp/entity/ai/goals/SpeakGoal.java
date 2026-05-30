package com.sp.entity.ai.goals;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.cca_stuff.SkinWalkerComponent;
import com.sp.entity.custom.SkinWalkerEntity;
import com.sp.entity.voice.*;
import com.sp.sounds.voicechat.BackroomsVoicechatPlugin;
import com.javachatai.core.ChatAgent;
import com.voicejavaai.core.RoboticVoice;
import com.voicejavaai.voice.VoiceConfig;
import net.minecraft.entity.player.PlayerEntity;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.plugins.impl.ServerLevelImpl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.random.Random;

import java.util.UUID;

public class SpeakGoal extends Goal {
    private final Random random = Random.create();
    private final SkinWalkerEntity entity;
    private final SkinWalkerComponent component;
    private final RoboticVoice roboticVoice;
    private final VoiceConfig voiceConfig;
    private final ChatAgent chatAgent;
    private int actCooldown;
    private int responseCheckCooldown = 0;
    public ServerLevelImpl serverLevel = null;
    public LocationalAudioChannel audioChannel;
    public AudioPlayer audioPlayer;
    private boolean learningTriggered = false;

    public SpeakGoal(SkinWalkerEntity entity) {
        this.entity = entity;
        this.component = InitializeComponents.SKIN_WALKER.get(entity);
        this.roboticVoice = RoboticVoice.getInstance();
        this.voiceConfig = VoiceConfig.robotic();
        this.voiceConfig.setBasePitch(100 + Math.random() * 40);
        this.chatAgent = ChatAgent.getInstance();
    }

    @Override
    public boolean canStart() {
        return this.entity.isAlive();
    }

    @Override
    public void start() {
        this.actCooldown = getTickCount(40);
        this.responseCheckCooldown = 0;
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

            if (responseCheckCooldown > 0) {
                responseCheckCooldown--;
            }

            if (this.actCooldown > 0) {
                this.actCooldown--;
                return;
            }

            checkForPlayerInput();

            if (responseQueue.hasPending()) {
                return;
            }

            this.playRandomPlayerSounds();
            this.setRandomActCoolDown();
        }
    }

    private void checkForPlayerInput() {
        if (!BackroomsVoicechatPlugin.lastTranscribedInputs.isEmpty()) {
            String input = BackroomsVoicechatPlugin.lastTranscribedInputs.remove(0);
            if (input == null || input.trim().isEmpty()) return;

            component.setLastHeardInput(input);

            String responseText;
            String lang = detectLanguageFromInput(input);

            if (input.startsWith("voice_")) {
                String playerName = input.substring(6);
                responseText = generateVoiceResponse(playerName, lang);
            } else {
                String inputText = input.contains(":") ? input.substring(input.indexOf(":") + 1).trim() : input;
                chatAgent.setLanguage(lang);
                responseText = chatAgent.respond(entity.getUuid(), inputText);
            }

            if (responseText == null || responseText.isEmpty()) {
                responseText = lang.equals("es") ? "hmm" : "hmm";
            }

            String latencyInput = input.startsWith("voice_") ? "voice" : input;
            int baseLatency = new LatencyCalculator().calculateLatency(
                latencyInput,
                component.isInTrueForm(),
                0
            );

            int latency = Math.max(100, Math.min(8000, baseLatency));

            short[] responseAudio = roboticVoice.speak(responseText, voiceConfig);

            if (responseAudio != null && responseAudio.length > 0) {
                int delayTicks = latency / 50;
                component.getResponseQueue().enqueue(responseAudio, delayTicks);
            }
        }
    }

    private String detectLanguageFromInput(String input) {
        if (input.startsWith("voice_")) {
            UUID targetUUID = component.getTargetPlayerUUID();
            if (targetUUID != null) {
                PlayerEntity target = entity.getWorld().getPlayerByUuid(targetUUID);
                if (target != null) {
                    PlayerComponent playerComp = InitializeComponents.PLAYER.get(target);
                    String setting = playerComp.getSkinWalkerLang();
                    if (setting != null && !setting.equals("auto")) {
                        return setting;
                    }
                }
            }
            return "es";
        }

        String text = input.contains(":") ? input.substring(input.indexOf(":") + 1).trim() : input;
        String lower = text.toLowerCase();

        if (lower.matches(".*\\b(hola|que|como|donde|quien|por que|si|no|buenas|gracias)\\b.*")) return "es";
        if (lower.matches(".*\\b(hello|hey|what|how|where|who|why|yes|no|please|thanks)\\b.*")) return "en";
        if (lower.matches(".*\\b(hallo|wie|wo|wer|was|warum|ja|nein|danke|bitte)\\b.*")) return "de";
        if (lower.matches(".*\\b(bonjour|salut|comment|pourquoi|oui|merci|s'il|je|tu)\\b.*")) return "fr";
        if (lower.matches(".*\\b(ciao|come|dove|chi|cosa|perché|si|no|grazie|per favore)\\b.*")) return "it";
        if (lower.matches(".*\\b(ola|como|onde|quem|o que|por que|sim|nao|obrigado|por favor)\\b.*")) return "pt";

        return "es";
    }

    private String generateVoiceResponse(String playerName, String lang) {
        double roll = Math.random();
        boolean inTrueForm = component.isInTrueForm();

        if (inTrueForm) {
            return getTrueFormVoiceResponse(roll, lang);
        }

        int recentCount = component.getResponseQueue().hasPending() ? 1 : 0;
        if (recentCount > 0) {
            return getBusyVoiceResponse(roll, lang);
        }

        return getNormalVoiceResponse(roll, lang);
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
                if (roll < 0.45) return "ta voix me familiar";
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
                if (roll < 0.35) return "puedo oirte";
                if (roll < 0.45) return "tu voz suena familiar";
                if (roll < 0.55) return "no pares de hablar";
                if (roll < 0.65) return "me gusta tu voz";
                if (roll < 0.75) return "hables mas fuerte";
                if (roll < 0.85) return "no te escucho bien";
                return "sigue ahi";
        }
    }

    private String getBusyVoiceResponse(double roll, String lang) {
        switch (lang) {
            case "en":
                if (roll < 0.3) return "what";
                if (roll < 0.5) return "tell me";
                if (roll < 0.7) return "yes";
                return "whats up";
            case "de":
                if (roll < 0.3) return "was";
                if (roll < 0.5) return "sag";
                if (roll < 0.7) return "ja";
                return "was gibt's";
            case "fr":
                if (roll < 0.3) return "quoi";
                if (roll < 0.5) return "dis-moi";
                if (roll < 0.7) return "oui";
                return "ça va";
            case "it":
                if (roll < 0.3) return "cosa";
                if (roll < 0.5) return "dimmi";
                if (roll < 0.7) return "sì";
                return "come va";
            case "pt":
                if (roll < 0.3) return "que";
                if (roll < 0.5) return "fala";
                if (roll < 0.7) return "sim";
                return "e ai";
            default:
                if (roll < 0.3) return "que";
                if (roll < 0.5) return "dime";
                if (roll < 0.7) return "si";
                return "que onda";
        }
    }

    private String getNormalVoiceResponse(double roll, String lang) {
        switch (lang) {
            case "en":
                if (roll < 0.1) return "hey";
                if (roll < 0.2) return "whats up";
                if (roll < 0.3) return "sup";
                if (roll < 0.4) return "yo";
                if (roll < 0.5) return "hi";
                if (roll < 0.6) return "yeah";
                if (roll < 0.7) return "what";
                if (roll < 0.8) return "hello";
                if (roll < 0.9) return "ok";
                return "hey there";
            case "de":
                if (roll < 0.1) return "hallo";
                if (roll < 0.2) return "was gibt's";
                if (roll < 0.3) return "hey";
                if (roll < 0.4) return "ja";
                if (roll < 0.5) return "hi";
                if (roll < 0.6) return "na";
                if (roll < 0.7) return "was";
                if (roll < 0.8) return "moin";
                if (roll < 0.9) return "ok";
                return "hey hey";
            case "fr":
                if (roll < 0.1) return "salut";
                if (roll < 0.2) return "ça va";
                if (roll < 0.3) return "hey";
                if (roll < 0.4) return "oui";
                if (roll < 0.5) return "coucou";
                if (roll < 0.6) return "quoi";
                if (roll < 0.7) return "bonjour";
                if (roll < 0.8) return "yo";
                if (roll < 0.9) return "ok";
                return "salut salut";
            case "it":
                if (roll < 0.1) return "ciao";
                if (roll < 0.2) return "come va";
                if (roll < 0.3) return "hey";
                if (roll < 0.4) return "sì";
                if (roll < 0.5) return "salve";
                if (roll < 0.6) return "cosa";
                if (roll < 0.7) return "buongiorno";
                if (roll < 0.8) return "yo";
                if (roll < 0.9) return "ok";
                return "ciao ciao";
            case "pt":
                if (roll < 0.1) return "oi";
                if (roll < 0.2) return "e ai";
                if (roll < 0.3) return "hey";
                if (roll < 0.4) return "sim";
                if (roll < 0.5) return "ola";
                if (roll < 0.6) return "que";
                if (roll < 0.7) return "bom dia";
                if (roll < 0.8) return "yo";
                if (roll < 0.9) return "ok";
                return "oi oi";
            default:
                if (roll < 0.1) return "hey";
                if (roll < 0.2) return "que onda";
                if (roll < 0.3) return "que pasó";
                if (roll < 0.4) return "dime";
                if (roll < 0.5) return "que hay";
                if (roll < 0.6) return "hola";
                if (roll < 0.7) return "que quieres";
                if (roll < 0.8) return "si";
                if (roll < 0.9) return "que fue";
                return "hey que onda";
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

        if (this.serverLevel == null) {
            this.serverLevel = new ServerLevelImpl(this.entity.getServer().getWorld(this.entity.getWorld().getRegistryKey()));
        }

        if (!this.serverLevel.getServerLevel().equals(this.entity.getServer().getWorld(this.entity.getWorld().getRegistryKey()))) {
            this.serverLevel = new ServerLevelImpl(this.entity.getServer().getWorld(this.entity.getWorld().getRegistryKey()));
        }

        VoicechatServerApi api = BackroomsVoicechatPlugin.voicechatApi;
        if (api == null) return;

        UUID targetUUID = this.component.getTargetPlayerUUID();
        if (targetUUID == null) return;
        if (BackroomsVoicechatPlugin.randomSpeakingList.isEmpty()) return;
        if (!BackroomsVoicechatPlugin.randomSpeakingList.containsKey(targetUUID)) return;
        if (BackroomsVoicechatPlugin.randomSpeakingList.get(targetUUID) == null) return;
        if (BackroomsVoicechatPlugin.randomSpeakingList.get(targetUUID).isEmpty()) return;

        short[] data = BackroomsVoicechatPlugin.randomSpeakingList.get(targetUUID)
            .get(random.nextBetween(0, BackroomsVoicechatPlugin.randomSpeakingList.get(targetUUID).size() - 1));

        playAudio(data);
    }

    private void playAudio(short[] data) {
        if (data == null || data.length == 0) return;

        VoicechatServerApi api = BackroomsVoicechatPlugin.voicechatApi;
        if (api == null) return;

        if (this.serverLevel == null) {
            if (this.entity.getServer() == null) return;
            this.serverLevel = new ServerLevelImpl(this.entity.getServer().getWorld(this.entity.getWorld().getRegistryKey()));
        }

        if (this.audioChannel == null) {
            this.audioChannel = api.createLocationalAudioChannel(
                this.entity.getUuid(),
                this.serverLevel,
                api.createPosition(this.entity.getX(), this.entity.getY(), this.entity.getZ())
            );
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
