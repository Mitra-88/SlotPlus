package me.mitra.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;

final class BindingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BindingsStore() {
    }

    private record Data(int[][] pairs) {
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("slotplus-bindings.json");
    }

    static void load() {
        try {
            Path path = path();
            if (!Files.exists(path)) {
                SlotPlusLog.info("no bindings file at {} - starting empty", path);
                return;
            }
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("pairs")) {
                    int[][] pairs = GSON.fromJson(json.get("pairs"), int[][].class);
                    Bindings.restore(pairs);
                    SlotPlusLog.info("loaded {} pair(s) from {}", pairs == null ? 0 : pairs.length, path);
                } else {
                    SlotPlusLog.info("bindings file at {} has no pairs - starting empty", path);
                }
            }
        } catch (Exception e) {
            SlotPlusLog.warn("failed to read bindings file - starting empty", e);
        }
        for (int slot = 0; slot <= Bindings.MAX_SLOT; slot++) {
            Bindings.consistentPartners(slot);
        }
        saveIfDirty();
    }

    static void saveIfDirty() {
        if (!Bindings.isDirty()) return;
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                GSON.toJson(new Data(Bindings.snapshot()), writer);
            }
            Bindings.markClean();
            SlotPlusLog.info("saved bindings: {}", Bindings.describe());
        } catch (Exception e) {
            SlotPlusLog.warn("failed to save bindings file", e);
        }
    }
}
