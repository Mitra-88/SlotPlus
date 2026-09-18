package me.mitra.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class SlotPlusClient implements ClientModInitializer {
    public static final String MOD_ID = "slotplus";
    public static final Identifier BOUND_ICON = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/bound.png");

    private static KeyMapping bindKey;

    @Override
    public void onInitializeClient() {
        SlotPlusConfig.HANDLER.load();
        BindingsStore.load();

        bindKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + MOD_ID + ".bind",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "binds"))));

        ScreenEvents.AFTER_INIT.register((minecraft, screen, _, _) -> {
            if (screen instanceof InventoryScreen inventoryScreen) {
                new ScreenController(inventoryScreen, minecraft);
            } else if (SlotPlusConfig.isCreativeSupportEnabled()
                    && screen instanceof CreativeModeInventoryScreen creativeScreen) {
                new ScreenController(creativeScreen, minecraft);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> save());
        ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> save());
    }

    static KeyMapping bindKey() {
        return bindKey;
    }

    private static void save() {
        BindingsStore.saveIfDirty();
        SlotPlusConfig.HANDLER.save();
    }
}
