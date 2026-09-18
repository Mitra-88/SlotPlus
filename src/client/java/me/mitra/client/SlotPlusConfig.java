package me.mitra.client;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.Boolean;
import dev.isxander.yacl3.config.v2.api.autogen.Label;
import dev.isxander.yacl3.config.v2.api.autogen.TickBox;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SlotPlusConfig {
    public static final ConfigClassHandler<SlotPlusConfig> HANDLER = ConfigClassHandler.createBuilder(SlotPlusConfig.class)
            .id(Identifier.fromNamespaceAndPath(SlotPlusClient.MOD_ID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("slotplus.json5"))
                    .setJson5(true)
                    .build())
            .build();

    private static final String CATEGORY_GENERAL = "general";

    @AutoGen(category = CATEGORY_GENERAL)
    @Boolean(colored = true)
    @SerialEntry(comment = "Master switch for the bind gesture and swap triggers")
    public boolean enabled = true;

    @AutoGen(category = CATEGORY_GENERAL)
    @Boolean(colored = true)
    @SerialEntry(comment = "Shift-clicking a bound hotbar slot swaps it with its bound slot instead of quick-moving")
    public boolean hotbarShiftSwap = false;

    @SuppressWarnings("unused")
    @AutoGen(category = CATEGORY_GENERAL)
    @Label
    public final Component keybindNote = Component.translatable(
            "yacl3.config." + SlotPlusClient.MOD_ID + ":config.keybindNote");

    @AutoGen(category = CATEGORY_GENERAL)
    @Boolean(colored = true)
    @SerialEntry(comment = "Allow binding and swapping while the creative inventory is open")
    public boolean creativeSupport = false;

    @AutoGen(category = CATEGORY_GENERAL)
    @TickBox
    @SerialEntry(comment = "Show the binding tutorial again on the next inventory open")
    public boolean showTutorial = true;

    @AutoGen(category = CATEGORY_GENERAL)
    @TickBox
    @SerialEntry(comment = "Draw the paired hotbar slot's number on each bound slot")
    public boolean showPartnerDigit = true;

    static boolean isEnabled() {
        return HANDLER.instance().enabled;
    }

    static boolean isCreativeSupportEnabled() {
        return HANDLER.instance().creativeSupport;
    }

    static boolean isHotbarShiftSwapEnabled() {
        return HANDLER.instance().hotbarShiftSwap;
    }

    static boolean isPartnerDigitEnabled() {
        return HANDLER.instance().showPartnerDigit;
    }
}
