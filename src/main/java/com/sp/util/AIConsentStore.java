package com.sp.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side persistence of the player's acceptance of the mod's external voice and AI models.
 * Saved as JSON in the config folder so the consent screen can be shown before the main menu.
 */
public class AIConsentStore {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("spb-revamped-ai-consent.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean accepted;
    private static boolean loaded;

    private AIConsentStore() {
    }

    public static boolean hasAccepted() {
        if (!loaded) {
            load();
        }
        return accepted;
    }

    public static void setAccepted(boolean value) {
        accepted = value;
        save();
    }

    private static void load() {
        loaded = true;
        if (!Files.exists(FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            if (object.has("acceptedAIModels")) {
                accepted = object.get("acceptedAIModels").getAsBoolean();
            }
        } catch (Exception e) {
            accepted = false;
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
        } catch (IOException e) {
            return;
        }
        JsonObject object = new JsonObject();
        object.addProperty("acceptedAIModels", accepted);
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(object, writer);
        } catch (IOException e) {
            // ignore, the consent is re-asked next launch
        }
    }
}
