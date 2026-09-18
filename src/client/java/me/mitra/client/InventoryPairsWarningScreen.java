package me.mitra.client;

import dev.isxander.yacl3.api.Option;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

final class InventoryPairsWarningScreen extends Screen {
    private final Screen parent;
    private final Option<Boolean> option;
    private final LinearLayout layout = LinearLayout.vertical().spacing(8);
    private Checkbox dontShowAgain;

    InventoryPairsWarningScreen(Screen parent, Option<Boolean> option) {
        super(Component.translatable(SlotPlusClient.MOD_ID + ".warning.title").withStyle(ChatFormatting.RED));
        this.parent = parent;
        this.option = option;
    }

    @Override
    protected void init() {
        this.layout.defaultCellSetting().alignHorizontallyCenter();
        this.layout.addChild(new StringWidget(this.title, this.font));
        this.layout.addChild(new MultiLineTextWidget(warningBody(), this.font)
                .setMaxWidth(this.width - 120)
                .setMaxRows(15)
                .setCentered(true));
        this.dontShowAgain = this.layout.addChild(Checkbox.builder(
                Component.translatable(SlotPlusClient.MOD_ID + ".warning.dontShowAgain"), this.font).build());
        LinearLayout buttons = this.layout.addChild(LinearLayout.horizontal().spacing(8));
        buttons.defaultCellSetting().paddingTop(12);
        buttons.addChild(Button.builder(
                Component.translatable(SlotPlusClient.MOD_ID + ".warning.yes").withStyle(ChatFormatting.GREEN),
                _ -> close(true)).build());
        buttons.addChild(Button.builder(
                Component.translatable(SlotPlusClient.MOD_ID + ".warning.no").withStyle(ChatFormatting.RED),
                _ -> close(false)).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    private static Component warningBody() {
        return Component.translatable(SlotPlusClient.MOD_ID + ".warning.body")
                .append("\n\n")
                .append(Component.translatable(SlotPlusClient.MOD_ID + ".warning.fine")
                        .withStyle(ChatFormatting.YELLOW));
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        FrameLayout.centerInRectangle(this.layout, this.getRectangle());
    }

    private void close(boolean accepted) {
        if (accepted) {
            this.option.applyValue();
            if (this.dontShowAgain.selected()) {
                SlotPlusConfig.HANDLER.instance().inventoryPairsWarned = true;
            }
        } else {
            this.option.requestSet(false);
        }
        SlotPlusConfig.HANDLER.save();
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            this.close(false);
            return true;
        }
        return super.keyPressed(event);
    }
}
