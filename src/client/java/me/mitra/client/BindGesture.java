package me.mitra.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

final class BindGesture {
    private final AbstractContainerScreen<?> screen;
    private final Minecraft minecraft;
    private final boolean creative;
    private final SwapSender swapSender = new SwapSender();

    private boolean pairing;
    private boolean bindKeyDown;
    private int originContainerSlot = -1;
    private int consumedButton = -1;
    private boolean originWasBound;

    private boolean tutorialOpen;

    BindGesture(AbstractContainerScreen<?> screen, Minecraft minecraft) {
        this.screen = screen;
        this.minecraft = minecraft;
        this.creative = screen instanceof CreativeModeInventoryScreen;
        this.tutorialOpen = SlotPlusConfig.isEnabled() && SlotPlusConfig.HANDLER.instance().showTutorial;
        if (tutorialOpen) {
            SlotPlusConfig.HANDLER.instance().showTutorial = false;
        }
    }

    boolean isCreative() {
        return creative;
    }

    boolean tutorialOpen() {
        return tutorialOpen;
    }

    boolean isPairing() {
        return pairing;
    }

    int originContainerSlot() {
        return originContainerSlot;
    }

    boolean onMouseClick(MouseButtonEvent event) {
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        LocalPlayer player = minecraft.player;
        if (!SlotPlusConfig.isEnabled() || gameMode == null || player == null) return true;

        if (tutorialOpen) {
            dismissTutorial();
            consumedButton = event.button();
            return false;
        }

        if (pairing) {
            if (bindKeyDown && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                Slot slot = SlotGeometry.slotAt(screen, event.x(), event.y());
                if (isValidPartner(slot)) {
                    completePairing(SlotGeometry.containerSlotOf(slot, creative));
                    consumedButton = event.button();
                    return false;
                }
                endPairing();
            }
            return true;
        }

        Slot slot = SlotGeometry.slotAt(screen, event.x(), event.y());
        if (slot == null) return true;
        int containerSlot = SlotGeometry.containerSlotOf(slot, creative);

        if (bindKeyDown && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!SlotGeometry.isPlayerSlot(slot, creative) || anythingCarried(player)) return true;
            originWasBound = Bindings.consistentPartner(containerSlot) != -1;
            Bindings.unbind(containerSlot);
            BindingsStore.saveIfDirty();
            pairing = true;
            originContainerSlot = containerSlot;
            consumedButton = event.button();
            return false;
        }

        boolean shiftClick = event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0
                && (!Bindings.isHotbarSlot(containerSlot) || SlotPlusConfig.isHotbarShiftSwapEnabled());
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || shiftClick) {
            if (!SlotGeometry.isPlayerSlot(slot, creative) || anythingCarried(player)) return true;
            int partner = Bindings.consistentPartner(containerSlot);
            if (partner == -1) return true;

            int hotbarSide = Bindings.hotbarSideOf(containerSlot, partner);
            int otherSide = containerSlot == hotbarSide ? partner : containerSlot;
            int clickedMenuSlot = SlotGeometry.menuSlotOfContainer(otherSide);
            boolean sent = creative
                    ? swapSender.trySendCreative(player.inventoryMenu, player, clickedMenuSlot, hotbarSide, containerSlot)
                    : swapSender.trySend(player.inventoryMenu, gameMode, player, clickedMenuSlot, hotbarSide, containerSlot);
            if (sent) {
                consumedButton = event.button();
                return false;
            }
            return true;
        }
        return true;
    }

    boolean onMouseRelease(MouseButtonEvent event) {
        if (event.button() == consumedButton) {
            consumedButton = -1;
            return false;
        }
        return true;
    }

    void onKeyPress(KeyEvent event) {
        if (tutorialOpen) {
            dismissTutorial();
            return;
        }
        if (SlotPlusClient.bindKey().matches(event)) {
            bindKeyDown = true;
        }
    }

    void onKeyRelease(KeyEvent event) {
        if (!SlotPlusClient.bindKey().matches(event)) return;
        bindKeyDown = false;
        if (!SlotPlusConfig.isEnabled()) {
            clearGesture();
            return;
        }
        if (!pairing) return;
        Slot slot = SlotGeometry.slotAt(screen,
                minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
        if (isValidPartner(slot)) {
            completePairing(SlotGeometry.containerSlotOf(slot, creative));
        } else {
            endPairing();
        }
    }

    void reset() {
        clearGesture();
        bindKeyDown = false;
    }

    private boolean anythingCarried(LocalPlayer player) {
        return !screen.getMenu().getCarried().isEmpty() || !player.inventoryMenu.getCarried().isEmpty();
    }

    private void completePairing(int targetContainerSlot) {
        int origin = originContainerSlot;
        clearGesture();
        if (origin == -1) return;
        Bindings.bind(origin, targetContainerSlot);
        playClick();
        BindingsStore.saveIfDirty();
    }

    private void endPairing() {
        int origin = originContainerSlot;
        clearGesture();
        if (origin == -1) return;
        if (originWasBound) {
            actionBar(Component.translatable(SlotPlusClient.MOD_ID + ".msg.cleared"));
            playClick();
        }
    }

    private void clearGesture() {
        pairing = false;
        originContainerSlot = -1;
        consumedButton = -1;
    }

    private boolean isValidPartner(Slot slot) {
        if (slot == null || originContainerSlot == -1) return false;
        if (!SlotGeometry.isPlayerSlot(slot, creative)) return false;
        int target = SlotGeometry.containerSlotOf(slot, creative);
        if (!Bindings.isBindable(target) || target == originContainerSlot) return false;
        return Bindings.isHotbarSlot(originContainerSlot) || Bindings.isHotbarSlot(target);
    }

    private void dismissTutorial() {
        SlotPlusConfig.HANDLER.instance().showTutorial = false;
        tutorialOpen = false;
        SlotPlusConfig.HANDLER.save();
    }

    private void actionBar(Component message) {
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(message);
        }
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
