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
                    Bindings.get().restore(data.partner());
                }
            }
            Bindings.get().markClean();
        } catch (Exception ignored) {
        }
    }

    static void saveIfDirty() {
        Bindings bindings = Bindings.get();
        if (!bindings.isDirty()) return;
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                GSON.toJson(new Data(bindings.snapshot()), writer);
            }
            bindings.markClean();
        } catch (Exception ignored) {
        }
    }

    private record Data(int[] partner) {
    }
}
