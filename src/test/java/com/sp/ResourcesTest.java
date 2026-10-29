package com.sp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the mod's resources and wiring: everything a typo can break without the compiler noticing, and that only
 * shows up as a crash or a purple-black texture in game.
 */
class ResourcesTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path GENERATED = Path.of("src/main/generated");
    private static final String MOD = "spb-revamped";

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /**
     * A mod asset, looked up in the hand made resources and in the data generator's output.
     */
    private static Optional<Path> asset(String path) {
        for (Path root : List.of(RESOURCES, GENERATED)) {
            Path file = root.resolve("assets").resolve(MOD).resolve(path);
            if (Files.exists(file)) return Optional.of(file);
        }
        return Optional.empty();
    }

    private static boolean classExists(String name) {
        return ResourcesTest.class.getClassLoader().getResource(name.replace('.', '/') + ".class") != null;
    }

    @Test
    @DisplayName("Every lang file is valid JSON")
    void langFilesAreValid() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES.resolve("assets/" + MOD + "/lang"))) {
            for (Path file : files.toList()) {
                assertDoesNotThrow(() -> readJson(file), "Invalid JSON in " + file.getFileName());
            }
        }
    }

    @Test
    @DisplayName("Every Level 94 phrase is translated in English and Spanish")
    void level94PhrasesTranslated() throws Exception {
        Set<String> keys = new TreeSet<>();
        keys.add("entity.spb-revamped.neighbor");
        keys.add("spb-revamped.level94.exit.wait");
        keys.add("spb-revamped.level94.neighbor.late");
        keys.add("spb-revamped.level94.neighbor.welcome_home");
        keys.add("spb-revamped.level94.phrase.been_here");
        keys.add("spb-revamped.level94.phrase.always_here");

        Class<?> director = Class.forName("com.sp.world.level94.Level94Director");
        Field zonePhrases = director.getDeclaredField("ZONE_PHRASES");
        zonePhrases.setAccessible(true);
        for (String[] zone : (String[][]) zonePhrases.get(null)) {
            for (String phrase : zone) keys.add("spb-revamped.level94.phrase." + phrase);
        }
        Field neighborLines = director.getDeclaredField("NEIGHBOR_LINES");
        neighborLines.setAccessible(true);
        for (String line : (String[]) neighborLines.get(null)) {
            keys.add("spb-revamped.level94.neighbor." + line);
        }

        for (String language : List.of("en_us", "es_es", "es_mx")) {
            JsonObject lang = readJson(RESOURCES.resolve("assets/" + MOD + "/lang/" + language + ".json"));
            for (String key : keys) {
                assertTrue(lang.has(key), language + " is missing " + key);
            }
        }
    }

    @Test
    @DisplayName("fabric.mod.json: entrypoints and mixin configs exist, and so does every mixin class")
    void modWiring() throws IOException {
        JsonObject mod = readJson(RESOURCES.resolve("fabric.mod.json"));
        assertEquals(MOD, mod.get("id").getAsString());

        for (Map.Entry<String, JsonElement> entrypoint : mod.getAsJsonObject("entrypoints").entrySet()) {
            for (JsonElement element : entrypoint.getValue().getAsJsonArray()) {
                String className = element.isJsonObject() ? element.getAsJsonObject().get("value").getAsString() : element.getAsString();
                assertTrue(classExists(className), "Entrypoint class not found: " + className);
            }
        }

        for (JsonElement element : mod.getAsJsonArray("mixins")) {
            String configName = element.isJsonObject() ? element.getAsJsonObject().get("config").getAsString() : element.getAsString();
            JsonObject config = readJson(RESOURCES.resolve(configName));
            String pkg = config.get("package").getAsString();
            for (String side : List.of("mixins", "client", "server")) {
                if (!config.has(side)) continue;
                for (JsonElement mixin : config.getAsJsonArray(side)) {
                    String className = pkg + "." + mixin.getAsString();
                    assertTrue(classExists(className), "Mixin listed in " + configName + " not found: " + className);
                }
            }
        }
    }

    @Test
    @DisplayName("Every sound file listed in sounds.json exists")
    void soundsExist() throws IOException {
        JsonObject sounds = readJson(RESOURCES.resolve("assets/" + MOD + "/sounds.json"));
        for (Map.Entry<String, JsonElement> event : sounds.entrySet()) {
            for (JsonElement sound : event.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                String name = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
                if (sound.isJsonObject() && sound.getAsJsonObject().has("type")
                        && sound.getAsJsonObject().get("type").getAsString().equals("event")) continue;
                if (!name.startsWith(MOD + ":")) continue;
                String path = "sounds/" + name.substring(MOD.length() + 1) + ".ogg";
                assertTrue(asset(path).isPresent(), "Sound event " + event.getKey() + " plays a missing file: " + path);
            }
        }
    }

    @Test
    @DisplayName("Block states point to existing models, and models to existing textures")
    void blockModelsAndTexturesExist() throws IOException {
        List<String> missing = new ArrayList<>();
        Set<String> models = new TreeSet<>();

        for (Path root : List.of(RESOURCES, GENERATED)) {
            Path dir = root.resolve("assets/" + MOD + "/blockstates");
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    collectModels(JsonParser.parseString(Files.readString(file)), models);
                }
            }
        }
        assertFalse(models.isEmpty(), "No block models found");

        for (String model : models) {
            if (!model.startsWith(MOD + ":")) continue;
            String path = "models/" + model.substring(MOD.length() + 1) + ".json";
            Optional<Path> file = asset(path);
            if (file.isEmpty()) {
                missing.add("model " + path);
                continue;
            }
            JsonObject json = readJson(file.get());
            if (!json.has("textures")) continue;
            for (Map.Entry<String, JsonElement> texture : json.getAsJsonObject("textures").entrySet()) {
                String value = texture.getValue().getAsString();
                if (!value.startsWith(MOD + ":")) continue;
                String texturePath = "textures/" + value.substring(MOD.length() + 1) + ".png";
                if (asset(texturePath).isEmpty()) missing.add("texture " + texturePath + " (used by " + path + ")");
            }
        }

        assertTrue(missing.isEmpty(), "Missing assets:\n  " + String.join("\n  ", missing));
    }

    private static void collectModels(JsonElement element, Set<String> models) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) {
                    models.add(entry.getValue().getAsString());
                } else {
                    collectModels(entry.getValue(), models);
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectModels(child, models);
        }
    }

    @Test
    @DisplayName("The Neighbor's textures exist and match the model's UV size")
    void neighborTextures() throws IOException {
        BufferedImage skin = ImageIO.read(asset("textures/entity/neighbor/neighbor.png").orElseThrow().toFile());
        BufferedImage eyes = ImageIO.read(asset("textures/entity/neighbor/neighbor_eyes.png").orElseThrow().toFile());
        assertEquals(skin.getWidth(), skin.getHeight(), "The texture must be square");
        assertEquals(0, skin.getWidth() % 64, "The model is laid out on 64x64; the texture must be a multiple of it");
        assertEquals(skin.getWidth(), eyes.getWidth(), "The eyes layer must have the same size as the texture");

        // The face (front of the head, UV 8,8 to 16,17 in model pixels) must be painted
        int k = skin.getWidth() / 64;
        int alpha = skin.getRGB(12 * k, 12 * k) >>> 24;
        assertEquals(255, alpha, "The face is transparent");
    }

    @Test
    @DisplayName("Level 94 dimension: fixed afternoon time and valid dimension files")
    void dimensions() throws IOException {
        JsonObject type = readJson(RESOURCES.resolve("data/" + MOD + "/dimension_type/level94.json"));
        assertEquals(10000, type.get("fixed_time").getAsInt());

        try (Stream<Path> files = Files.list(RESOURCES.resolve("data/" + MOD + "/dimension"))) {
            for (Path file : files.toList()) {
                JsonObject dimension = readJson(file);
                String typeId = dimension.get("type").getAsString();
                if (!typeId.startsWith(MOD + ":")) continue;
                Path typeFile = RESOURCES.resolve("data/" + MOD + "/dimension_type/" + typeId.substring(MOD.length() + 1) + ".json");
                assertTrue(Files.exists(typeFile), file.getFileName() + " uses a missing dimension type " + typeId);
            }
        }
    }

    @Test
    @DisplayName("The grass shaders declare everything GrassRenderer sets")
    void grassShaders() throws IOException {
        Path dir = RESOURCES.resolve("assets/" + MOD + "/pinwheel/shaders/program/grass");
        String compute = Files.readString(dir.resolve("compute/positions.comp"));
        for (String uniform : List.of("isLevel94", "HeightOriginX", "HeightOriginZ", "HeightSize", "NumOfInstances", "density", "maxDist", "FrustumPlanes")) {
            assertTrue(compute.contains("uniform") && compute.contains(uniform), "positions.comp does not declare " + uniform);
        }
        assertTrue(compute.contains("binding = 2"), "positions.comp has no height buffer at binding 2");

        String vertex = Files.readString(dir.resolve("grass.vsh"));
        String fragment = Files.readString(dir.resolve("grass.fsh"));
        assertTrue(vertex.contains("out float bladeBase"), "grass.vsh does not pass the blade base on");
        assertTrue(fragment.contains("in float bladeBase"), "grass.fsh does not read the blade base");
    }
}
