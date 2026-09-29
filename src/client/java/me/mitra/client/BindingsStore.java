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
import java.util.ArrayList;
import java.util.List;

final class BindingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BindingsStore() {
    }

    private record Data(int[][] pairs) {
    }

    // Pre-multi-bind format: one flat array where partner[i] is slot i's single partner.
    private record LegacyData(int[] partner) {
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
                } else if (json.has("partner")) {
                    LegacyData legacy = GSON.fromJson(json, LegacyData.class);
                    int[][] pairs = legacyPartnerArrayToPairs(legacy.partner());
                    Bindings.restore(pairs);
                    SlotPlusLog.info("migrated legacy bindings file: {} pair(s) from {}", pairs.length, path);
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

    // Converts the old one-partner-per-slot array into pair entries; pairs with
    // invalid slots are dropped (a slot could only have one partner back then).
    static int[][] legacyPartnerArrayToPairs(int[] legacy) {
        if (legacy == null) return new int[0][];
        List<int[]> pairs = new ArrayList<>();
        for (int slot = 0; slot < legacy.length && slot <= Bindings.MAX_SLOT; slot++) {
            int partner = legacy[slot];
            if (Bindings.isBindable(partner) && partner > slot) {
                pairs.add(new int[]{slot, partner});
            }
        }
        return pairs.toArray(new int[0][]);
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
