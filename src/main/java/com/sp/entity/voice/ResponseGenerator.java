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

        templates.put("fear", new TemplateCategory(
            Arrays.asList("angst", "hilfe", "hilf", "was war das", "oh gott", "gruselig", "unheimlich"),
            Arrays.asList("was ist los", "alles gut?", "ruhig", "keine angst", "was war das", "beruhig dich", "entspann dich", "ist nichts"),
            800
        ));

        templates.put("hostile", new TemplateCategory(
            Arrays.asList("geh weg", "hau ab", "verschwinde", "stirb", "töte", "lass mich"),
            Arrays.asList("warum?", "was hab ich getan", "ruhig", "was ist dein problem", "ich hab nichts gemacht", "chill mal", "was soll das"),
            200
        ));

        templates.put("question", new TemplateCategory(
            Arrays.asList("was", "wie", "wann", "warum", "wieso", "welche", "wo", "wer", "?"),
            Arrays.asList("keine ahnung", "weiss nicht", "wer weiss", "was meinst du?", "hä?", "nochmal bitte", "versteh ich nicht", "was?"),
            500
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
            Arrays.asList("j'explore", "un joueur", "je ne sais pas", "je me balade", "perdu", "nouveau ici", "rien"),
            0
        ));

        templates.put("location", new TemplateCategory(
            Arrays.asList("où", "tu es", "là-bas", "ici"),
            Arrays.asList("ici", "pas loin", "autour", "perdu", "j'explore", "dans un couloir", "je ne sais pas"),
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

        templates.put("fear", new TemplateCategory(
            Arrays.asList("peur", "aide", "aidez", "au secours", "c'était quoi", "mon dieu", "flippant"),
            Arrays.asList("qu'est-ce qui se passe", "ça va?", "calme-toi", "n'aie pas peur", "c'était quoi ça", "détends-toi", "c'est rien", "relax"),
            800
        ));

        templates.put("hostile", new TemplateCategory(
            Arrays.asList("va-t'en", "dégage", "pars", "meurs", "tue", "laisse-moi"),
            Arrays.asList("pourquoi?", "j'ai rien fait", "calme-toi", "c'est quoi ton problème", "qu'est-ce que j'ai fait", "du calme", "quoi?"),
            200
        ));

        templates.put("question", new TemplateCategory(
            Arrays.asList("quoi", "comment", "quand", "pourquoi", "quel", "quelle", "où", "qui", "?"),
            Arrays.asList("aucune idée", "je sais pas", "qui sait", "tu veux dire quoi?", "hein?", "répète", "je comprends pas", "quoi?"),
            500
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

        templates.put("fear", new TemplateCategory(
            Arrays.asList("paura", "aiuto", "aiutami", "cos'era", "oddio", "spaventoso"),
            Arrays.asList("che succede", "tutto bene?", "calmati", "non avere paura", "cos'era quello", "rilassati", "non è niente", "tranquillo"),
            800
        ));

        templates.put("hostile", new TemplateCategory(
            Arrays.asList("vattene", "vai via", "sparisci", "muori", "uccidi", "lasciami"),
            Arrays.asList("perché?", "cosa ho fatto", "calmati", "qual è il tuo problema", "non ho fatto niente", "tranquillo", "che c'è?"),
            200
        ));

        templates.put("question", new TemplateCategory(
            Arrays.asList("cosa", "come", "quando", "perché", "perche", "quale", "dove", "chi", "?"),
            Arrays.asList("non lo so", "boh", "chi lo sa", "cosa intendi?", "eh?", "ripeti", "non capisco", "cosa?"),
            500
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

        templates.put("fear", new TemplateCategory(
            Arrays.asList("medo", "ajuda", "socorro", "o que foi isso", "meu deus", "assustador"),
            Arrays.asList("o que houve", "tudo bem?", "calma", "não tenha medo", "o que foi isso", "relaxa", "não é nada", "tranquilo"),
            800
        ));

        templates.put("hostile", new TemplateCategory(
            Arrays.asList("vai embora", "sai daqui", "some", "morre", "mata", "me deixa"),
            Arrays.asList("por quê?", "o que eu fiz", "calma", "qual é o seu problema", "não fiz nada", "relaxa mano", "que foi?"),
            200
        ));

        templates.put("question", new TemplateCategory(
            Arrays.asList("que", "como", "quando", "porque", "qual", "onde", "quem", "?"),
            Arrays.asList("não sei", "sei lá", "quem sabe", "como assim?", "hein?", "repete", "não entendi", "o quê?"),
            500
        ));

        templatesByLang.put("pt", templates);
    }

    private static final List<String> CATEGORY_ORDER = List.of("fear", "hostile", "identity", "location", "greeting", "question");

    public ResponseResult generateResponse(String heardInput, SkinWalkerEntity entity) {
        return generateResponse(heardInput, entity, null);
    }

    /**
     * @param heardInput   what the SkinWalker heard: the text said/written, or {@code "voice_<name>"} when it only
     *                     knows that somebody talked.
     * @param languageHint the language of the input if it is already known (e.g. the speech-to-text model's
     *                     language), or null to detect it from the text.
     */
    public ResponseResult generateResponse(String heardInput, SkinWalkerEntity entity, String languageHint) {
        if (heardInput == null || heardInput.isEmpty()) {
            return null;
        }

        String lower = heardInput.toLowerCase(Locale.ROOT).trim();

        if (lower.equals(lastHeardInput)) {
            repetitionCount++;
        } else {
            repetitionCount = 0;
        }
        lastHeardInput = lower;

        String lang = detectLanguage(entity, lower, languageHint);
        Map<String, TemplateCategory> templates = templatesByLang.getOrDefault(lang, templatesByLang.get("universal"));

        if (lower.startsWith("voice_")) {
            TemplateCategory voiceTemplate = templates.get("voice_detected");
            String responseText = voiceTemplate.responses.get(
                random.nextBetween(0, voiceTemplate.responses.size() - 1));
            int latency = voiceTemplate.latencyModifier + (repetitionCount * 300);
            return new ResponseResult(responseText, latency, "voice_", lang);
        }

        Set<String> words = LanguageDetector.tokenize(lower);
        TemplateCategory selected = null;
        String matchedKeyword = null;

        for (String category : CATEGORY_ORDER) {
            TemplateCategory template = templates.get(category);
            if (template == null) continue;

            for (String keyword : template.keywords) {
                if (matchesKeyword(lower, words, keyword)) {
                    selected = template;
                    matchedKeyword = keyword;
                    break;
                }
            }
            if (selected != null) break;
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

    /**
     * Single words must match a whole word ("hi" must not match "this"), phrases are searched as text.
     */
    private static boolean matchesKeyword(String lower, Set<String> words, String keyword) {
        if (keyword.equals("?")) return lower.contains("?");
        if (keyword.indexOf(' ') >= 0 || keyword.indexOf('-') >= 0 || keyword.indexOf('\'') >= 0) {
            return lower.contains(keyword);
        }
        return words.contains(keyword);
    }

    /**
     * Priority: the target player's language setting, then the given hint, then detection from the text.
     */
    private String detectLanguage(SkinWalkerEntity entity, String input, String languageHint) {
        UUID targetUUID = InitializeComponents.SKIN_WALKER.get(entity).getTargetPlayerUUID();
        if (targetUUID != null) {
            PlayerEntity target = entity.getWorld().getPlayerByUuid(targetUUID);
            if (target != null) {
                PlayerComponent playerComp = InitializeComponents.PLAYER.get(target);
                String setting = playerComp.getSkinWalkerLang();
                if (setting != null && !setting.equals("auto") && templatesByLang.containsKey(setting)) {
                    return setting;
                }
            }
        }

        if (languageHint != null && templatesByLang.containsKey(languageHint)) {
            return languageHint;
        }

        if (input.startsWith("voice_")) {
            return "es";
        }

        String detected = LanguageDetector.detect(input);
        return detected != null ? detected : "universal";
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
