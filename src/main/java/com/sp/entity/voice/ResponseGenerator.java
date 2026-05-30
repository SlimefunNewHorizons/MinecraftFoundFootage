package com.sp.entity.voice;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.entity.custom.SkinWalkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.random.Random;

import java.util.*;

public class ResponseGenerator {
    private final Random random = Random.create();
    private final Map<String, Map<String, TemplateCategory>> templatesByLang = new HashMap<>();
    public int repetitionCount = 0;
    private String lastHeardInput = "";

    public ResponseGenerator() {
        initializeSpanishTemplates();
        initializeEnglishTemplates();
        initializeUniversalTemplates();
        initializeGermanTemplates();
        initializeFrenchTemplates();
        initializeItalianTemplates();
        initializePortugueseTemplates();
    }

    private void initializeSpanishTemplates() {
        Map<String, TemplateCategory> templates = new HashMap<>();

        templates.put("greeting", new TemplateCategory(
            Arrays.asList("hola", "hello", "hey", "hi", "oye", "eh", "buenas", "que tal", "buenos", "buenas"),
            Arrays.asList("hola", "hey", "que tal", "buenas", "que onda", "hey que pasó", "holi", "que hubo", "hi", "buenas noches"),
            -200
        ));

        templates.put("identity", new TemplateCategory(
            Arrays.asList("quien", "eres", "quién", "que eres", "qué eres", "que haces", "que onda"),
            Arrays.asList("soy yo", "un jugador", "no se", "que quieres", "estoy explorando", "nada solo caminando", "estoy perdido", "soy nuevo aqui", "ando viendo que hay", "jugando nomas"),
            0
        ));

        templates.put("location", new TemplateCategory(
            Arrays.asList("donde", "dónde", "esta", "estas", "está", "estás", "aqui", "aquí", "pa", "lugar"),
            Arrays.asList("aqui", "por aca", "cerca", "ahi mero", "nomas viendo", "en un pasillo", "no se donde estoy", "perdido", "por algun lugar", "explorando"),
            300
        ));

        templates.put("fear", new TemplateCategory(
            Arrays.asList("miedo", "ayuda", "socorro", "temor", "asustado", "scared", "help", "que fue eso", "que paso", "que te asusto"),
            Arrays.asList("que paso", "que te pasa", "tranquilo", "calmate", "que fue eso", "no seas miedoso", "estas bien?", "que te asusto", "no pasa nada", "relax"),
            800
        ));

        templates.put("hostile", new TemplateCategory(
            Arrays.asList("muere", "kill", "die", "mata", "vete", "sal de aqui", "aléjate", "fuera", "aléjate"),
            Arrays.asList("que te pasa", "por que?", "que hice", "tranquilo", "que onda", "por que gritas", "calmate wey", "que te pasa bro", "no te he hecho nada", "que pedo"),
            200
        ));

        templates.put("question", new TemplateCategory(
            Arrays.asList("que", "como", "cuando", "cuanto", "quien", "donde", "por que", "para que", "cuales", "?"),
            Arrays.asList("no se", "ni idea", "que se yo", "quien sabe", "ni pta idea", "como que?", "que dices?", "no entiendo", "explícate mejor", "que quieres decir", "como asi?", "no cacho"),
            500
        ));

        templates.put("voice_detected", new TemplateCategory(
            Arrays.asList("voice_"),
            Arrays.asList("que onda", "que pasó", "hey", "que", "dime", "que hay", "hola", "que quieres", "si", "que fue", "hey que onda", "que pasa", "holi", "que hubo"),
            100
        ));

        templates.put("generic", new TemplateCategory(
            new ArrayList<>(),
            Arrays.asList("si", "no", "tal vez", "ni idea", "nomas viendo", "explorando", "puede ser", "yo que se", "ajá", "va", "va que va", "dale", "ok", "bien", "mal", "mas o menos", "ahi vamos", "nada", "aquí ando", "nomas"),
            1000
        ));

        templatesByLang.put("es", templates);
    }

    private void initializeEnglishTemplates() {
        Map<String, TemplateCategory> templates = new HashMap<>();

        templates.put("greeting", new TemplateCategory(
            Arrays.asList("hello", "hey", "hi", "yo", "sup", "whats up", "howdy", "greetings"),
            Arrays.asList("hey", "hi", "whats up", "yo", "hello", "sup", "how's it going", "hey there", "hiya", "what's good"),
            -200
        ));

        templates.put("identity", new TemplateCategory(
            Arrays.asList("who", "are you", "what are you", "what do you do", "whats your name"),
            Arrays.asList("just a player", "i'm nobody", "exploring", "just wandering around", "lost tbh", "new here", "nothing much", "who knows", "just vibing", "figuring things out"),
            0
        ));

        templates.put("location", new TemplateCategory(
            Arrays.asList("where", "where are you", "location", "here", "there"),
            Arrays.asList("here", "nearby", "around", "not sure", "somewhere around", "in a hallway", "exploring", "lost", "wandering", "just here"),
            300
        ));

        templates.put("fear", new TemplateCategory(
            Arrays.asList("scared", "help", "afraid", "spooked", "what was that", "oh god", "nope"),
            Arrays.asList("what happened", "you okay?", "chill", "calm down", "what was that", "don't be scared", "you good?", "relax", "it's fine", "nothing"),
            800
        ));

        templates.put("hostile", new TemplateCategory(
            Arrays.asList("go away", "leave", "get out", "die", "kill", "fight me"),
            Arrays.asList("what's your problem?", "why?", "what did i do?", "chill", "what's wrong", "why are you like this", "calm down bro", "i didn't do anything", "what's going on", "woah"),
            200
        ));

        templates.put("question", new TemplateCategory(
            Arrays.asList("what", "how", "when", "where", "who", "why", "which", "?"),
            Arrays.asList("no idea", "dunno", "idk", "who knows", "not sure", "what do you mean?", "huh?", "can you repeat that?", "not following", "what are you saying", "say that again", "what?"),
            500
        ));

        templates.put("voice_detected", new TemplateCategory(
            Arrays.asList("voice_"),
            Arrays.asList("hey", "sup", "what", "yo", "yeah?", "hello", "what's up", "hi", "what do you need", "yeah", "what's going on", "hey there", "what's good", "hiya", "what's happening"),
            100
        ));

        templates.put("generic", new TemplateCategory(
            new ArrayList<>(),
            Arrays.asList("yeah", "no", "maybe", "idk", "exploring", "wandering", "could be", "who knows", "uh huh", "sure", "alright", "cool", "ok", "good", "bad", "kinda", "getting there", "nothing", "just here", "nah"),
            1000
        ));

        templatesByLang.put("en", templates);
    }

    private void initializeUniversalTemplates() {
        Map<String, TemplateCategory> templates = new HashMap<>();

        templates.put("greeting", new TemplateCategory(
            Arrays.asList("hello", "hey", "hi", "hola", "salut", "ciao", "hallo", "ola", "привет", "你好", "안녕"),
            Arrays.asList("hey", "hi", "hello", "yo", "sup", "hello friend", "hey there", "hiya"),
            -200
        ));

        templates.put("identity", new TemplateCategory(
            Arrays.asList("who", "what", "quien", "que", "wer", "was", "qui", "quest", "quem", "кто", "谁"),
            Arrays.asList("just exploring", "a player", "nobody", "wandering around", "lost", "just here", "nothing"),
            0
        ));

        templates.put("location", new TemplateCategory(
            Arrays.asList("where", "donde", "dove", "wo", "où", "onde", "где", "哪里", "어디"),
            Arrays.asList("here", "nearby", "around", "lost", "somewhere", "exploring", "not sure"),
            300
        ));

        templates.put("fear", new TemplateCategory(
            Arrays.asList("help", "scared", "ayuda", "miedo", "hilfe", "peur", "aiuto", "ajuda", "помощь", "帮助", "도움"),
            Arrays.asList("what happened", "you okay", "chill", "calm down", "relax", "it's fine", "nothing"),
            800
        ));

        templates.put("hostile", new TemplateCategory(
            Arrays.asList("go away", "leave", "vete", "aléjate", "geh weg", "va-t'en", "vai via", "vá embora", "уходи", "走开", "가"),
            Arrays.asList("what's wrong", "why", "chill", "calm down", "what did i do", "woah", "easy"),
            200
        ));

        templates.put("question", new TemplateCategory(
            Arrays.asList("what", "how", "where", "who", "why", "que", "como", "donde", "quien", "por que", "was", "wie", "wo", "wer", "warum", "comment", "pourquoi", "quoi", "как", "что", "为什么"),
            Arrays.asList("no idea", "dunno", "idk", "who knows", "not sure", "huh", "what", "say again", "not following"),
            500
        ));

        templates.put("voice_detected", new TemplateCategory(
            Arrays.asList("voice_"),
            Arrays.asList("hey", "sup", "yo", "what", "hello", "yeah", "hi", "what's up", "hey there"),
            100
        ));

        templates.put("generic", new TemplateCategory(
            new ArrayList<>(),
            Arrays.asList("yeah", "no", "maybe", "ok", "sure", "alright", "cool", "hmm", "uh huh", "right", "well", "ah", "oh", "nice", "interesting"),
            1000
        ));

        templatesByLang.put("universal", templates);
    }

    private void initializeGermanTemplates() {
        Map<String, TemplateCategory> templates = new HashMap<>();

        templates.put("greeting", new TemplateCategory(
            Arrays.asList("hallo", "hey", "hi", "moin", "servus", "grüß", "tag"),
            Arrays.asList("hallo", "hey", "hi", "moin", "servus", "was geht", "hi da", "na"),
            -200
        ));

        templates.put("identity", new TemplateCategory(
            Arrays.asList("wer", "bist", "was machst", "was tust"),
            Arrays.asList("ich erkunde", "nur ein spieler", "weiss nicht", "spaziere rum", "verloren", "neu hier", "nichts besonderes"),
            0
        ));

        templates.put("location", new TemplateCategory(
            Arrays.asList("wo", "wo bist", "hier", "dort"),
            Arrays.asList("hier", "in der nähe", "irgendwo", "verloren", "erkunde gerade", "in einem gang", "weiss nicht"),
            300
        ));

        templates.put("voice_detected", new TemplateCategory(
            Arrays.asList("voice_"),
            Arrays.asList("hey", "was", "ja", "hallo", "hi", "was gibt's", "sag", "ja?"),
            100
        ));

        templates.put("generic", new TemplateCategory(
            new ArrayList<>(),
            Arrays.asList("ja", "nein", "vielleicht", "weiss nicht", "ok", "klar", "stimmt", "genau", "hm", "na gut", "toll", "interessant"),
            1000
        ));

        templatesByLang.put("de", templates);
    }

    private void initializeFrenchTemplates() {
        Map<String, TemplateCategory> templates = new HashMap<>();

        templates.put("greeting", new TemplateCategory(
            Arrays.asList("bonjour", "salut", "hey", "coucou", "bonsoir", "allô"),
            Arrays.asList("salut", "hey", "coucou", "bonjour", "bonsoir", "ça va", "wesh", "yo"),
            -200
        ));

        templates.put("identity", new TemplateCategory(
            Arrays.asList("qui", "es-tu", "que fais-tu", "c'est qui"),
            Arrays.asList("je explore", "un joueur", "je ne sais pas", "je me balade", "perdu", "nouveau ici", "rien"),
            0
        ));

        templates.put("location", new TemplateCategory(
            Arrays.asList("où", "tu es", "là-bas", "ici"),
            Arrays.asList("ici", "pas loin", "autour", "perdu", "je explore", "dans un couloir", "je ne sais pas"),
            300
        ));

        templates.put("voice_detected", new TemplateCategory(
            Arrays.asList("voice_"),
            Arrays.asList("hey", "oui", "quoi", "salut", "oui?", "dis-moi", "yo", "ça va"),
            100
        ));

        templates.put("generic", new TemplateCategory(
            new ArrayList<>(),
            Arrays.asList("oui", "non", "peut-être", "je ne sais pas", "d'accord", "ok", "bien", "mouais", "hmm", "voilà", "intéressant", "ah"),
            1000
        ));

        templatesByLang.put("fr", templates);
    }

    private void initializeItalianTemplates() {
        Map<String, TemplateCategory> templates = new HashMap<>();

        templates.put("greeting", new TemplateCategory(
            Arrays.asList("ciao", "hey", "salve", "buongiorno", "buonasera"),
            Arrays.asList("ciao", "hey", "salve", "buongiorno", "buonasera", "hey ciao", "yo", "come stai"),
            -200
        ));

        templates.put("identity", new TemplateCategory(
            Arrays.asList("chi", "sei", "che fai", "come ti chiami"),
            Arrays.asList("sto esplorando", "un giocatore", "non lo so", "cammino", "perso", "nuovo qui", "niente"),
            0
        ));

        templates.put("location", new TemplateCategory(
            Arrays.asList("dove", "sei", "qua", "là"),
            Arrays.asList("qui", "vicino", "in giro", "perso", "sto esplorando", "in un corridoio", "non lo so"),
            300
        ));

        templates.put("voice_detected", new TemplateCategory(
            Arrays.asList("voice_"),
            Arrays.asList("hey", "cosa", "sì", "ciao", "dimmi", "yo", "come va"),
            100
        ));

        templates.put("generic", new TemplateCategory(
            new ArrayList<>(),
            Arrays.asList("sì", "no", "forse", "non lo so", "ok", "bene", "male", "ahimè", "hm", "va bene", "interessante", "ah"),
            1000
        ));

        templatesByLang.put("it", templates);
    }

    private void initializePortugueseTemplates() {
        Map<String, TemplateCategory> templates = new HashMap<>();

        templates.put("greeting", new TemplateCategory(
            Arrays.asList("ola", "oi", "hey", "bom dia", "boa noite", "e aí"),
            Arrays.asList("oi", "ola", "hey", "e aí", "bom dia", "boa noite", "salve", "fala"),
            -200
        ));

        templates.put("identity", new TemplateCategory(
            Arrays.asList("quem", "é voce", "o que voce faz", "como se chama"),
            Arrays.asList("to explorando", "um jogador", "nao sei", "andando por ai", "perdido", "novo aqui", "nada"),
            0
        ));

        templates.put("location", new TemplateCategory(
            Arrays.asList("onde", "esta", "aqui", "la"),
            Arrays.asList("aqui", "perto", "por ai", "perdido", "explorando", "num corredor", "nao sei"),
            300
        ));

        templates.put("voice_detected", new TemplateCategory(
            Arrays.asList("voice_"),
            Arrays.asList("oi", "e ai", "fala", "hey", "sim", "oi?", "tudo bem", "opa"),
            100
        ));

        templates.put("generic", new TemplateCategory(
            new ArrayList<>(),
            Arrays.asList("sim", "nao", "talvez", "nao sei", "ok", "beleza", "legal", "hm", "entendi", "ah", "interessante", "bom"),
            1000
        ));

        templatesByLang.put("pt", templates);
    }

    public ResponseResult generateResponse(String heardInput, SkinWalkerEntity entity) {
        if (heardInput == null || heardInput.isEmpty()) {
            return null;
        }

        String lower = heardInput.toLowerCase().trim();

        if (lower.equals(lastHeardInput)) {
            repetitionCount++;
        } else {
            repetitionCount = 0;
        }
        lastHeardInput = lower;

        String lang = detectLanguage(entity, lower);
        Map<String, TemplateCategory> templates = templatesByLang.getOrDefault(lang, templatesByLang.get("en"));

        if (lower.startsWith("voice_")) {
            TemplateCategory voiceTemplate = templates.get("voice_detected");
            String responseText = voiceTemplate.responses.get(
                random.nextBetween(0, voiceTemplate.responses.size() - 1));
            int latency = voiceTemplate.latencyModifier + (repetitionCount * 300);
            return new ResponseResult(responseText, latency, "voice_", lang);
        }

        TemplateCategory selected = null;
        String matchedKeyword = null;

        boolean hasQuestionWord = lower.contains("que") || lower.contains("como") ||
            lower.contains("quien") || lower.contains("donde") || lower.contains("cuando") ||
            lower.contains("cuanto") || lower.contains("por que") || lower.contains("para que") ||
            lower.contains("cuales") || lower.contains("?") ||
            lower.contains("what") || lower.contains("how") || lower.contains("when") ||
            lower.contains("where") || lower.contains("who") || lower.contains("why") ||
            lower.contains("which");

        if (hasQuestionWord) {
            selected = templates.get("question");
            matchedKeyword = "question";
        }

        if (selected == null) {
            for (Map.Entry<String, TemplateCategory> entry : templates.entrySet()) {
                if (entry.getKey().equals("voice_detected") || entry.getKey().equals("question")) continue;
                for (String keyword : entry.getValue().keywords) {
                    if (lower.contains(keyword)) {
                        selected = entry.getValue();
                        matchedKeyword = keyword;
                        break;
                    }
                }
                if (selected != null) break;
            }
        }

        if (selected == null) {
            selected = templates.get("generic");
        }

        String responseText = selected.responses.get(random.nextBetween(0, selected.responses.size() - 1));

        UUID targetUUID = InitializeComponents.SKIN_WALKER.get(entity).getTargetPlayerUUID();
        if (targetUUID != null) {
            PlayerEntity target = entity.getWorld().getPlayerByUuid(targetUUID);
            if (target != null) {
                responseText = responseText.replace("{player_name}", target.getName().getString());
            }
        }

        int latencyModifier = selected.latencyModifier + (repetitionCount * 500);

        return new ResponseResult(responseText, latencyModifier, matchedKeyword, lang);
    }

    private String detectLanguage(SkinWalkerEntity entity, String input) {
        UUID targetUUID = InitializeComponents.SKIN_WALKER.get(entity).getTargetPlayerUUID();
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

        if (input.matches(".*[\\u0400-\\u04FF].*")) return "ru";
        if (input.matches(".*[\\u0600-\\u06FF].*")) return "ar";
        if (input.matches(".*[\\u4E00-\\u9FFF].*")) return "zh";
        if (input.matches(".*[\\u3040-\\u309F\\u30A0-\\u30FF].*")) return "ja";
        if (input.matches(".*[\\uAC00-\\uD7AF].*")) return "ko";
        if (input.matches(".*[\\u0E00-\\u0E7F].*")) return "th";
        if (input.matches(".*[\\u0900-\\u097F].*")) return "hi";

        if (input.matches(".*\\b(hola|que|como|donde|quien|por que|si|no|buenas|gracias|por favor|este|esta|esas|estos|aqui|alla|muy|tambien|pero|porque|cuando|cuanto)\\b.*")) {
            return "es";
        }
        if (input.matches(".*\\b(hello|hey|what|how|where|who|why|yes|no|please|thanks|the|is|are|was|were|this|that|here|there|very|also|but|because|when|where)\\b.*")) {
            return "en";
        }
        if (input.matches(".*\\b(hallo|wie|wo|wer|was|warum|ja|nein|danke|bitte|guten|heute|morgen|abend|nacht|tag)\\b.*")) {
            return "de";
        }
        if (input.matches(".*\\b(bonjour|salut|comment|pourquoi|oui|merci|s'il|je|tu|nous|vous|ils|elles|bonsoir|matin|soir|nuit)\\b.*")) {
            return "fr";
        }
        if (input.matches(".*\\b(ciao|come|dove|chi|cosa|perché|si|no|grazie|per favore|buongiorno|buonasera|notte|giorno|sera)\\b.*")) {
            return "it";
        }
        if (input.matches(".*\\b(ola|como|onde|quem|o que|por que|sim|não|obrigado|por favor|bom|boa|dia|noite|tarde)\\b.*")) {
            return "pt";
        }

        return "universal";
    }

    public class ResponseResult {
        public final String text;
        public final int latencyModifier;
        public final String matchedKeyword;
        public final String language;

        public ResponseResult(String text, int latencyModifier, String matchedKeyword, String language) {
            this.text = text;
            this.latencyModifier = latencyModifier;
            this.matchedKeyword = matchedKeyword;
            this.language = language;
        }
    }

    private static class TemplateCategory {
        final List<String> keywords;
        final List<String> responses;
        final int latencyModifier;

        TemplateCategory(List<String> keywords, List<String> responses, int latencyModifier) {
            this.keywords = keywords;
            this.responses = responses;
            this.latencyModifier = latencyModifier;
        }
    }
}
