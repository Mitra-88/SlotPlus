package me.mitra.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;

final class BindingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BindingsStore() {
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("slotplus-bindings.json");
    }

    static void load() {
        try {
            Path path = path();
            if (!Files.exists(path)) return;
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                Data data = GSON.fromJson(reader, Data.class);
                if (data != null) {
                    Bindings.restore(data.partner());
                }
            }
        } catch (Exception ignored) {
        }
        for (int slot = 0; slot <= Bindings.MAX_SLOT; slot++) {
            Bindings.consistentPartner(slot);
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
        } catch (Exception ignored) {
        }
    }

    private record Data(int[] partner) {
    }
}
