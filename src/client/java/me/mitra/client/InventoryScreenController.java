package me.mitra.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

final class InventoryScreenController {
    private static final int RATE_LIMIT_MS = 50;
    private static final int LINE_COLOR = 0x80FF64D2;
    private static final int TARGET_OUTLINE_COLOR = 0xFFFF64D2;
    private static final int TARGET_HOVER_COLOR = 0x40FFFFFF;
    private static final int TUTORIAL_PANEL_COLOR = 0xC0101018;
    private static final int TUTORIAL_TITLE_COLOR = 0xFFFF64D2;
    private static final int TUTORIAL_TEXT_COLOR = 0xFFE0E0E0;
    private static final int TUTORIAL_DIM_COLOR = 0xFF808080;

    private final InventoryScreen screen;
    private final Minecraft minecraft;

    private boolean pairing;
    private int originMenuSlot = -1;
    private int originContainerSlot = -1;
    private boolean bindKeyDown;
    private boolean suppressNextRelease;
    private final long[] lastSwapAt = new long[Bindings.MAX_SLOT + 1];

    private boolean tutorialOpen;
    private final Component tutorialTitle = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.title");
    private final Component tutorialLine1 = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.line1",
            Component.keybind("key." + SlotPlusClient.MOD_ID + ".bind"));
    private final Component tutorialLine2 = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.line2",
            Component.keybind("key." + SlotPlusClient.MOD_ID + ".bind"));
    private final Component tutorialLine3 = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.line3");
    private final Component tutorialDismiss = Component.translatable(SlotPlusClient.MOD_ID + ".tutorial.dismiss");
    private final Component msgCleared = Component.translatable(SlotPlusClient.MOD_ID + ".msg.cleared");

    InventoryScreenController(InventoryScreen screen, Minecraft minecraft) {
        this.screen = screen;
        this.minecraft = minecraft;
        this.tutorialOpen = SlotPlusConfig.isEnabled() && !SlotPlusConfig.HANDLER.instance().tutorialSeen;
        if (tutorialOpen) {
            SlotPlusConfig.HANDLER.instance().tutorialSeen = true;
        }
        ScreenMouseEvents.allowMouseClick(screen).register(this::allowMouseClick);
        ScreenMouseEvents.allowMouseRelease(screen).register(this::allowMouseRelease);
        ScreenKeyboardEvents.afterKeyPress(screen).register(this::afterKeyPress);
        ScreenKeyboardEvents.afterKeyRelease(screen).register(this::afterKeyRelease);
        ScreenEvents.afterForeground(screen).register(this::afterForeground);
        ScreenEvents.remove(screen).register(_ -> reset());
    }

    private boolean allowMouseClick(Screen ignored, MouseButtonEvent event) {
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        LocalPlayer player = minecraft.player;
        if (!SlotPlusConfig.isEnabled() || gameMode == null || player == null) return true;

        if (tutorialOpen) {
            dismissTutorial();
            suppressNextRelease = true;
            return false;
        }

        InventoryMenu menu = screen.getMenu();
        Slot slot = slotAt(event.x(), event.y());

        if (pairing) {
            if (bindKeyDown && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (isValidPartner(slot)) {
                    completePairing(slot.getContainerSlot());
                    suppressNextRelease = true;
                    return false;
                }
                endPairingWithImplicitUnbind();
                return true;
            }
            return true;
        }

        if (slot == null) return true;
        int containerSlot = slot.getContainerSlot();

        if (bindKeyDown && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!isMainInventorySlot(slot) && !isHotbarSlot(slot)) return true;
            if (!menu.getCarried().isEmpty()) return true;
            Bindings.get().unbind(containerSlot);
            pairing = true;
            originMenuSlot = slot.index;
            originContainerSlot = containerSlot;
            return false;
        }

        boolean shiftClick = event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || shiftClick) {
            if (!menu.getCarried().isEmpty()) return true;
            int partner = Bindings.get().consistentPartner(containerSlot);
            if (partner == -1) return true;

            if (isMainInventorySlot(slot)) {
                if (!Bindings.isHotbarSlot(partner)) return true;
                sendSwap(menu, gameMode, player, slot.index, partner, containerSlot);
                suppressNextRelease = true;
                return false;
            }

            if (isHotbarSlot(slot) && shiftClick && SlotPlusConfig.isHotbarShiftSwapEnabled()) {
                int partnerMenuSlot = Bindings.isHotbarSlot(partner)
                        ? InventoryMenu.USE_ROW_SLOT_START + partner
                        : partner;
                sendSwap(menu, gameMode, player, partnerMenuSlot, containerSlot, containerSlot);
                suppressNextRelease = true;
                return false;
            }
        }
        return true;
    }

    private void sendSwap(InventoryMenu menu, MultiPlayerGameMode gameMode, LocalPlayer player,
                          int clickedSlot, int hotbarButton, int rateLimitKey) {
        long now = Util.getMillis();
        if (now - lastSwapAt[rateLimitKey] < RATE_LIMIT_MS) return;
        lastSwapAt[rateLimitKey] = now;
        gameMode.handleContainerInput(menu.containerId, clickedSlot, hotbarButton, ContainerInput.SWAP, player);
    }

    private boolean allowMouseRelease(Screen ignored, MouseButtonEvent event) {
        if (suppressNextRelease) {
            suppressNextRelease = false;
            return false;
        }
        return !pairing;
    }

    private void afterKeyPress(Screen ignored, KeyEvent event) {
        if (tutorialOpen) {
            dismissTutorial();
            return;
        }
        if (SlotPlusClient.bindKey().matches(event) && !bindKeyDown) {
            bindKeyDown = true;
        }
    }

    private void afterKeyRelease(Screen ignored, KeyEvent event) {
        if (!SlotPlusClient.bindKey().matches(event)) return;
        bindKeyDown = false;
        if (!pairing) return;
        Slot slot = slotAt(cursorX(), cursorY());
        if (isValidPartner(slot)) {
            completePairing(slot.getContainerSlot());
        } else {
            endPairingWithImplicitUnbind();
        }
    }

    private void completePairing(int targetContainerSlot) {
        int origin = originContainerSlot;
        originMenuSlot = -1;
        originContainerSlot = -1;
        pairing = false;
        if (origin == -1) return;
        Bindings.get().bind(origin, targetContainerSlot);
        int hotbarSlot = Bindings.isHotbarSlot(targetContainerSlot) ? targetContainerSlot : origin;
        actionBar(Component.translatable(SlotPlusClient.MOD_ID + ".msg.bound", hotbarSlot + 1));
    }

    private void endPairingWithImplicitUnbind() {
        int origin = originContainerSlot;
        originMenuSlot = -1;
        originContainerSlot = -1;
        pairing = false;
        if (origin == -1) return;
        if (Bindings.get().unbind(origin)) {
            actionBar(msgCleared);
        }
    }

    private void dismissTutorial() {
        SlotPlusConfig.HANDLER.instance().tutorialSeen = true;
        tutorialOpen = false;
    }

    private void actionBar(Component message) {
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(message);
        }
    }

    private void afterForeground(Screen ignored, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        if (!SlotPlusConfig.isEnabled()) return;
        InventoryMenu menu = screen.getMenu();

        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.USE_ROW_SLOT_END; i++) {
            Slot slot = menu.getSlot(i);
            if (Bindings.get().consistentPartner(slot.getContainerSlot()) == -1) continue;
            graphics.blit(RenderPipelines.GUI_TEXTURED, SlotPlusClient.BOUND_ICON,
                    screen.leftPos + slot.x, screen.topPos + slot.y, 0.0F, 0.0F, 16, 16, 16, 16);
        }

        if (pairing && originMenuSlot != -1) {
            Slot origin = menu.getSlot(originMenuSlot);
            drawLine(graphics,
                    screen.leftPos + origin.x + 8, screen.topPos + origin.y + 8,
                    mouseX, mouseY);
            drawTargetHighlights(graphics, menu, mouseX, mouseY);
        } else {
            drawBindingLine(graphics, menu, mouseX, mouseY);
        }

        if (tutorialOpen) {
            drawTutorial(graphics);
        }
    }

    private void drawBindingLine(GuiGraphicsExtractor graphics, InventoryMenu menu, double mouseX, double mouseY) {
        Slot hovered = slotAt(mouseX, mouseY);
        if (hovered == null) return;
        int partner = Bindings.get().consistentPartner(hovered.getContainerSlot());
        if (partner == -1) return;
        int partnerMenuSlot = Bindings.isHotbarSlot(partner)
                ? InventoryMenu.USE_ROW_SLOT_START + partner
                : partner;
        Slot partnerSlot = menu.getSlot(partnerMenuSlot);
        drawLine(graphics,
                screen.leftPos + hovered.x + 8, screen.topPos + hovered.y + 8,
                screen.leftPos + partnerSlot.x + 8, screen.topPos + partnerSlot.y + 8);
    }

    private boolean isValidPartner(Slot slot) {
        if (slot == null || originContainerSlot == -1) return false;
        int target = slot.getContainerSlot();
        if (!Bindings.isBindable(target) || target == originContainerSlot) return false;
        if (Bindings.isHotbarSlot(originContainerSlot)) {
            return isMainInventorySlot(slot) || isHotbarSlot(slot);
        }
        return isHotbarSlot(slot);
    }

    private void drawTargetHighlights(GuiGraphicsExtractor graphics, InventoryMenu menu, int mouseX, int mouseY) {
        Slot hovered = slotAt(mouseX, mouseY);
        boolean originIsHotbar = Bindings.isHotbarSlot(originContainerSlot);
        highlightRange(graphics, menu, InventoryMenu.USE_ROW_SLOT_START, InventoryMenu.USE_ROW_SLOT_END,
                hovered, originIsHotbar ? originContainerSlot : -1);
        if (originIsHotbar) {
            highlightRange(graphics, menu, InventoryMenu.INV_SLOT_START, InventoryMenu.INV_SLOT_END, hovered, -1);
        }
    }

    private void highlightRange(GuiGraphicsExtractor graphics, InventoryMenu menu, int from, int to,
                                Slot hovered, int skipContainerSlot) {
        for (int i = from; i < to; i++) {
            Slot slot = menu.getSlot(i);
            if (slot.getContainerSlot() == skipContainerSlot) continue;
            int x = screen.leftPos + slot.x;
            int y = screen.topPos + slot.y;
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
        int y0 = Math.max(screen.topPos - 4 - panelHeight, 4);
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

    private boolean isHotbarSlot(Slot slot) {
        return slot != null
                && slot.index >= InventoryMenu.USE_ROW_SLOT_START
                && slot.index < InventoryMenu.USE_ROW_SLOT_END
                && Bindings.isHotbarSlot(slot.getContainerSlot());
    }

    private boolean isMainInventorySlot(Slot slot) {
        return slot != null
                && slot.index >= InventoryMenu.INV_SLOT_START
                && slot.index < InventoryMenu.INV_SLOT_END
                && Bindings.isBindable(slot.getContainerSlot())
                && !Bindings.isHotbarSlot(slot.getContainerSlot());
    }

    private Slot slotAt(double mouseX, double mouseY) {
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) continue;
            double x = screen.leftPos + slot.x;
            double y = screen.topPos + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }

    private double cursorX() {
        return minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
    }

    private double cursorY() {
        return minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
    }

    private void reset() {
        pairing = false;
        originMenuSlot = -1;
        originContainerSlot = -1;
        bindKeyDown = false;
        suppressNextRelease = false;
    }
}
