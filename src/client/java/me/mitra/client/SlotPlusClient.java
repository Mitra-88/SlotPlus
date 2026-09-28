package me.mitra.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.config.v2.api.autogen.OptionFactory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;

public final class SlotPlusClient implements ClientModInitializer {
    public static final String MOD_ID = "slotplus";
    public static final String BIND_KEY_NAME = "key." + MOD_ID + ".bind";
    public static final Identifier BOUND_ICON = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/bound.png");

    private static KeyMapping bindKey;

    @Override
    public void onInitializeClient() {
        SlotPlusConfig.HANDLER.load();
        SlotPlusLog.configure(SlotPlusConfig.isVerboseLoggingEnabled());
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().toString())
                .orElse("unknown");
        SlotPlusLog.info("SlotPlus {} init (enabled={}, creativeSupport={}, inventoryPairs={}, hotbarShiftSwap={})",
                version, SlotPlusConfig.isEnabled(), SlotPlusConfig.isCreativeSupportEnabled(),
                SlotPlusConfig.isInventoryPairsEnabled(), SlotPlusConfig.isHotbarShiftSwapEnabled());
        BindingsStore.load();
        SlotPlusLog.info("bindings: {}", Bindings.describe());
        OptionFactory.register(WarningBoolean.class, new WarningBooleanFactory());

        bindKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                BIND_KEY_NAME,
                InputConstants.Type.KEYBOARD,
                SDLScancode.SDL_SCANCODE_B,
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
        SlotPlusLog.info("disconnect/stopping: flushing saves");
        BindingsStore.saveIfDirty();
        SlotPlusConfig.HANDLER.save();
    }
}
