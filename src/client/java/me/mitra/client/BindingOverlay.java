package me.mitra.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

final class BindingOverlay {
    private static final int LINE_COLOR = 0x80FF64D2;
    private static final int TARGET_OUTLINE_COLOR = 0xFFFF64D2;
    private static final int TARGET_HOVER_COLOR = 0x40FFFFFF;
    private static final int PARTNER_DIGIT_COLOR = 0xFFFFFFFF;
    private static final int PARTNER_DIGIT_SHADOW_COLOR = 0xFF202028;
    private static final int TUTORIAL_PANEL_COLOR = 0xC0101018;
    private static final int TUTORIAL_TITLE_COLOR = 0xFFFF64D2;
    private static final int TUTORIAL_TEXT_COLOR = 0xFFE0E0E0;
    private static final int TUTORIAL_DIM_COLOR = 0xFF808080;

    private final AbstractContainerScreen<?> screen;
    private final BindGesture gesture;
    private final boolean creative;
    private final Component tutorialTitle = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.title");
    private final Component tutorialLine1 = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.line1",
            Component.keybind("key." + SlotPlusClient.MOD_ID + ".bind"));
    private final Component tutorialLine2 = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.line2");
    private final Component tutorialLine3 = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.line3");
    private final Component tutorialDismiss = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.dismiss");

    BindingOverlay(AbstractContainerScreen<?> screen, BindGesture gesture) {
        this.screen = screen;
        this.gesture = gesture;
        this.creative = gesture.isCreative();
    }

    void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!SlotPlusConfig.isEnabled()) return;

        for (Slot slot : screen.getMenu().slots) {
            if (!SlotGeometry.isPlayerSlot(slot, creative)) continue;
            int containerSlot = SlotGeometry.containerSlotOf(slot, creative);
            int partner = Bindings.consistentPartner(containerSlot);
            if (partner == -1) continue;
            int x = SlotGeometry.slotX(screen, slot);
            int y = SlotGeometry.slotY(screen, slot);
            graphics.blit(RenderPipelines.GUI_TEXTURED, SlotPlusClient.BOUND_ICON,
                    x, y, 0.0F, 0.0F, 16, 16, 16, 16);
            if (SlotPlusConfig.isPartnerDigitEnabled()) {
                drawPartnerDigit(graphics, x, y, containerSlot, partner);
            }
        }

        if (gesture.isPairing()) {
            Slot origin = SlotGeometry.findSlot(screen, gesture.originContainerSlot(), creative);
            if (origin != null) {
                drawLine(graphics,
                        SlotGeometry.slotCenterX(screen, origin), SlotGeometry.slotCenterY(screen, origin),
                        mouseX, mouseY);
            }
            drawTargetHighlights(graphics, mouseX, mouseY);
        } else {
            drawBindingLine(graphics, mouseX, mouseY);
        }

        if (gesture.tutorialOpen()) {
            drawTutorial(graphics);
        }
    }

    private void drawPartnerDigit(GuiGraphicsExtractor graphics, int x, int y, int containerSlot, int partner) {
        int hotbarSlot = Bindings.hotbarSideOf(partner, containerSlot);
        Component digit = Component.literal(String.valueOf(hotbarSlot + 1));
        var font = screen.getFont();
        graphics.centeredText(font, digit, x + 5, y + 4, PARTNER_DIGIT_SHADOW_COLOR);
        graphics.centeredText(font, digit, x + 4, y + 3, PARTNER_DIGIT_COLOR);
    }

    private void drawBindingLine(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        Slot hovered = SlotGeometry.slotAt(screen, mouseX, mouseY);
        if (!SlotGeometry.isPlayerSlot(hovered, creative)) return;
        int partner = Bindings.consistentPartner(SlotGeometry.containerSlotOf(hovered, creative));
        if (partner == -1) return;
        Slot partnerSlot = SlotGeometry.findSlot(screen, partner, creative);
        if (partnerSlot == null) return;
        drawLine(graphics,
                SlotGeometry.slotCenterX(screen, hovered), SlotGeometry.slotCenterY(screen, hovered),
                SlotGeometry.slotCenterX(screen, partnerSlot), SlotGeometry.slotCenterY(screen, partnerSlot));
    }

    private void drawTargetHighlights(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean originIsHotbar = Bindings.isHotbarSlot(gesture.originContainerSlot());
        Slot hovered = SlotGeometry.slotAt(screen, mouseX, mouseY);
        for (Slot slot : screen.getMenu().slots) {
            if (!SlotGeometry.isPlayerSlot(slot, creative)) continue;
            int containerSlot = SlotGeometry.containerSlotOf(slot, creative);
            if (containerSlot == gesture.originContainerSlot()) continue;
            if (!originIsHotbar && !Bindings.isHotbarSlot(containerSlot)) continue;
            int x = SlotGeometry.slotX(screen, slot);
            int y = SlotGeometry.slotY(screen, slot);
            if (slot == hovered) {
                graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 16, TARGET_HOVER_COLOR);
            }
            graphics.outline(x, y, 16, 16, TARGET_OUTLINE_COLOR);
        }
    }

    private void drawTutorial(GuiGraphicsExtractor graphics) {
        Component[] lines = {tutorialLine1, tutorialLine2, tutorialLine3};
        var font = screen.getFont();
        int lineHeight = 12;
        int panelWidth = font.width(tutorialTitle);
        for (Component line : lines) {
            panelWidth = Math.max(panelWidth, font.width(line));
        }
        panelWidth = Math.max(panelWidth, font.width(tutorialDismiss)) + 24;
        int panelHeight = 20 + lines.length * lineHeight + 16;
        int x0 = (screen.width - panelWidth) / 2;
        int y0 = 4;
        graphics.fill(RenderPipelines.GUI, x0, y0, x0 + panelWidth, y0 + panelHeight, TUTORIAL_PANEL_COLOR);
        graphics.centeredText(font, tutorialTitle, screen.width / 2, y0 + 8, TUTORIAL_TITLE_COLOR);
        int y = y0 + 20;
        for (Component line : lines) {
            graphics.centeredText(font, line, screen.width / 2, y, TUTORIAL_TEXT_COLOR);
            y += lineHeight;
        }
        graphics.centeredText(font, tutorialDismiss, screen.width / 2, y0 + panelHeight - 12, TUTORIAL_DIM_COLOR);
    }

    private static void drawLine(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            graphics.fill(RenderPipelines.GUI, x1 - 1, y1 - 1, x1 + 1, y1 + 1, LINE_COLOR);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + dx * i / steps;
            int y = y1 + dy * i / steps;
            graphics.fill(RenderPipelines.GUI, x, y, x + 1, y + 1, LINE_COLOR);
        }
    }
}
