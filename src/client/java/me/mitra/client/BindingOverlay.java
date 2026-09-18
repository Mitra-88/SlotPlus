package me.mitra.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

final class BindingOverlay {
    private static final Identifier BARRIER_TEXTURE = Identifier.withDefaultNamespace("textures/item/barrier.png");
    private static final int FORBIDDEN_TEXT_COLOR = 0xFFFF5555;
    private static final int ACCENT_COLOR = 0xFFFF64D2;
    private static final int ACTION_COLOR = 0xFFFFFF55;
    private static final int LINE_COLOR = 0x80FF64D2;
    private static final int TARGET_OUTLINE_COLOR = 0xFFFF64D2;
    private static final int TARGET_HOVER_COLOR = 0x40FFFFFF;
    private static final int PARTNER_DIGIT_COLOR = 0xFFFFFFFF;
    private static final int PARTNER_DIGIT_SHADOW_COLOR = 0xFF202028;
    private static final int PANEL_COLOR = 0xC0101018;
    private static final int PANEL_BORDER_COLOR = 0x90FF64D2;
    private static final int TUTORIAL_TITLE_COLOR = 0xFFFF64D2;
    private static final int TUTORIAL_TEXT_COLOR = 0xFFE0E0E0;
    private static final int TUTORIAL_DIM_COLOR = 0xFF808080;
    private static final int TUTORIAL_HOVER_COLOR = 0xFFFFFFFF;

    private final AbstractContainerScreen<?> screen;
    private final BindGesture gesture;
    private final boolean creative;
    private final Component tutorialTitle = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.title");
    private final Component tutorialLine1 = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.line1",
            Component.keybind(SlotPlusClient.BIND_KEY_NAME).withStyle(style -> style.withColor(ACCENT_COLOR)),
            accent("inventory slot"));
    private final Component tutorialLine2 = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.line2",
            action("click"), accent("partner slot"));
    private final Component tutorialLine3 = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.line3",
            action("Middle-click"), accent("partner"));
    private final Component tutorialDismiss = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.dismiss");
    private final Component swapPendingMessage = Component.translatable(SlotPlusClient.MOD_ID + ".msg.swapPending",
            accent("highlighted slot"));

    private int dismissMinX;
    private int dismissMaxX;
    private int dismissMinY;
    private int dismissMaxY;
    private int tutorialBottom;

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
            int hotbarSide = Bindings.hotbarSideOf(containerSlot, partner);
            if (SlotPlusConfig.isPartnerDigitEnabled() && Bindings.isHotbarSlot(hotbarSide)) {
                drawPartnerDigit(graphics, x, y, hotbarSide);
            }
        }

        if (gesture.isPairing()) {
            drawPairing(graphics, mouseX, mouseY);
        } else {
            drawBindingLine(graphics, mouseX, mouseY);
            drawPickupTarget(graphics);
        }

        if (gesture.isBindKeyDown()) {
            drawForbiddenHints(graphics, mouseX, mouseY);
        }

        if (gesture.tutorialOpen()) {
            drawTutorial(graphics, mouseX, mouseY);
        }

        drawMessage(graphics);
    }

    boolean onMouseClick(MouseButtonEvent event) {
        if (!gesture.tutorialOpen()) return false;
        boolean overDismiss = event.x() >= dismissMinX && event.x() < dismissMaxX
                && event.y() >= dismissMinY && event.y() < dismissMaxY;
        if (overDismiss) {
            gesture.dismissTutorial();
        }
        return overDismiss;
    }

    private void drawPairing(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Slot origin = SlotGeometry.findSlot(screen, gesture.originContainerSlot(), creative);
        if (origin != null) {
            drawLine(graphics,
                    SlotGeometry.slotCenterX(screen, origin), SlotGeometry.slotCenterY(screen, origin),
                    mouseX, mouseY);
        }
        Slot hovered = SlotGeometry.slotAt(screen, mouseX, mouseY);
        for (Slot slot : screen.getMenu().slots) {
            if (!SlotGeometry.isPlayerSlot(slot, creative)) continue;
            int containerSlot = SlotGeometry.containerSlotOf(slot, creative);
            if (containerSlot == gesture.originContainerSlot()) continue;
            if (!gesture.canPartner(gesture.originContainerSlot(), containerSlot)) continue;
            int x = SlotGeometry.slotX(screen, slot);
            int y = SlotGeometry.slotY(screen, slot);
            if (slot == hovered) {
                graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 16, TARGET_HOVER_COLOR);
            }
            graphics.outline(x, y, 16, 16, TARGET_OUTLINE_COLOR);
        }
    }

    private void drawForbiddenHints(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        Slot hovered = SlotGeometry.slotAt(screen, mouseX, mouseY);
        for (Slot slot : screen.getMenu().slots) {
            SlotGeometry.SlotRole role = SlotGeometry.SlotRole.of(slot, creative);
            if (role.messageKey == null) continue;
            graphics.blit(RenderPipelines.GUI_TEXTURED, BARRIER_TEXTURE,
                    SlotGeometry.slotX(screen, slot), SlotGeometry.slotY(screen, slot), 0.0F, 0.0F, 16, 16, 16, 16);
            if (slot == hovered) {
                graphics.setTooltipForNextFrame(
                        Component.translatable(role.messageKey).withStyle(style -> style.withColor(FORBIDDEN_TEXT_COLOR)),
                        (int) mouseX, (int) mouseY);
            }
        }
    }

    private void drawPickupTarget(GuiGraphicsExtractor graphics) {
        int pending = gesture.pickupSwapSlot();
        if (pending == -1) return;
        int partner = Bindings.consistentPartner(pending);
        if (partner == -1) return;
        Slot partnerSlot = SlotGeometry.findSlot(screen, partner, creative);
        if (partnerSlot == null) return;
        graphics.outline(SlotGeometry.slotX(screen, partnerSlot), SlotGeometry.slotY(screen, partnerSlot), 16, 16, TARGET_OUTLINE_COLOR);
    }

    private void drawPartnerDigit(GuiGraphicsExtractor graphics, int x, int y, int hotbarSide) {
        Component digit = Component.literal(String.valueOf(hotbarSide + 1));
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

    private void drawMessage(GuiGraphicsExtractor graphics) {
        Component message = gesture.pickupSwapSlot() != -1 ? swapPendingMessage : gesture.activeMessage();
        if (message == null) return;
        var font = screen.getFont();
        int panelWidth = font.width(message) + 24;
        int panelHeight = 24;
        int x0 = (screen.width - panelWidth) / 2;
        int y0 = gesture.tutorialOpen() ? tutorialBottom + 8 : 4;
        drawPanelBackground(graphics, x0, y0, x0 + panelWidth, y0 + panelHeight);
        graphics.centeredText(font, message, screen.width / 2, y0 + 6, TUTORIAL_TEXT_COLOR);
    }

    private void drawTutorial(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawPanel(graphics, tutorialTitle,
                new Component[]{tutorialLine1, tutorialLine2, tutorialLine3}, tutorialDismiss, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, Component title, Component[] lines,
                           Component dismiss, int mouseX, int mouseY) {
        var font = screen.getFont();
        int lineHeight = 12;
        int panelWidth = font.width(title);
        for (Component line : lines) {
            panelWidth = Math.max(panelWidth, font.width(line));
        }
        int dismissWidth = font.width(dismiss);
        panelWidth = Math.max(panelWidth, dismissWidth) + 24;
        int panelHeight = 20 + lines.length * lineHeight + 16;
        int x0 = (screen.width - panelWidth) / 2;
        int y0 = 4;
        drawPanelBackground(graphics, x0, y0, x0 + panelWidth, y0 + panelHeight);
        graphics.centeredText(font, title, screen.width / 2, y0 + 8, TUTORIAL_TITLE_COLOR);
        int y = y0 + 20;
        for (Component line : lines) {
            graphics.centeredText(font, line, screen.width / 2, y, TUTORIAL_TEXT_COLOR);
            y += lineHeight;
        }
        int dismissY = y0 + panelHeight - 12;
        int dismissX = screen.width / 2 - dismissWidth / 2;
        boolean hovered = mouseX >= dismissX && mouseX < dismissX + dismissWidth
                && mouseY >= dismissY - 2 && mouseY < dismissY + 10;
        graphics.centeredText(font, dismiss, screen.width / 2, dismissY, hovered ? TUTORIAL_HOVER_COLOR : TUTORIAL_DIM_COLOR);
        dismissMinX = dismissX;
        dismissMaxX = dismissX + dismissWidth;
        dismissMinY = dismissY - 2;
        dismissMaxY = dismissY + 10;
        tutorialBottom = y0 + panelHeight;
    }

    private static Component accent(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(ACCENT_COLOR));
    }

    private static Component action(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(ACTION_COLOR));
    }

    private static void fillRounded(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(RenderPipelines.GUI, x0 + 2, y0, x1 - 2, y1, color);
        graphics.fill(RenderPipelines.GUI, x0 + 1, y0 + 1, x1 - 1, y1 - 1, color);
        graphics.fill(RenderPipelines.GUI, x0, y0 + 2, x1, y1 - 2, color);
    }

    private void drawPanelBackground(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        fillRounded(graphics, x0, y0, x1, y1, PANEL_BORDER_COLOR);
        fillRounded(graphics, x0 + 1, y0 + 1, x1 - 1, y1 - 1, PANEL_COLOR);
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
