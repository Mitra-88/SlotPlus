package me.mitra.client;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.autogen.OptionAccess;
import dev.isxander.yacl3.config.v2.api.autogen.SimpleOptionFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class WarningBooleanFactory extends SimpleOptionFactory<WarningBoolean, Boolean> {
    @Override
    protected ControllerBuilder<Boolean> createController(WarningBoolean annotation, ConfigField<Boolean> field,
                                                          OptionAccess storage, Option<Boolean> option) {
        return BooleanControllerBuilder.create(option).coloured(annotation.colored());
    }

    @Override
    protected void listener(WarningBoolean annotation, ConfigField<Boolean> field, OptionAccess storage,
                            Option<Boolean> option, Boolean value) {
        if (!value || SlotPlusConfig.HANDLER.instance().inventoryPairsWarned) return;
        Minecraft minecraft = Minecraft.getInstance();
        Screen configScreen = minecraft.gui.screen();
        minecraft.gui.setScreen(new InventoryPairsWarningScreen(configScreen, option));
    }
}
