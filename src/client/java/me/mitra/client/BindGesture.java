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
import net.minecraft.util.Util;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

final class BindGesture {
    private final AbstractContainerScreen<?> screen;
    private final Minecraft minecraft;
    private final boolean creative;
    private final SwapSender swapSender = new SwapSender();

    private boolean bindKeyDown;
    private int originContainerSlot = -1;
    private int consumedButton = -1;
    private boolean originWasBound;
    private int pickupSwapSlot = -1;

    private boolean tutorialOpen;
    private Component message;
    private long messageUntil;

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
        return originContainerSlot != -1;
    }

    int originContainerSlot() {
        return originContainerSlot;
    }

    boolean isBindKeyDown() {
        return bindKeyDown;
    }

    int pickupSwapSlot() {
        validatePickupArmed();
        return pickupSwapSlot;
    }

    Component activeMessage() {
        return message != null && Util.getMillis() < messageUntil ? message : null;
    }

    boolean onMouseClick(MouseButtonEvent event) {
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        LocalPlayer player = minecraft.player;
        if (!SlotPlusConfig.isEnabled() || gameMode == null || player == null) return true;

        if (isPairing()) {
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
            return startBind(event, player, slot, containerSlot);
        }
        return trySwap(event, gameMode, player, slot, containerSlot);
    }

    boolean onMouseRelease(MouseButtonEvent event) {
        if (event.button() != consumedButton) return true;
        consumedButton = -1;
        if (isPairing()) {
            Slot slot = SlotGeometry.slotAt(screen, event.x(), event.y());
            if (slot != SlotGeometry.findSlot(screen, originContainerSlot, creative)) {
                resolvePairing(slot);
            }
        }
        return false;
    }

    void onKeyPress(KeyEvent event) {
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
        if (!isPairing()) return;
        resolvePairing(SlotGeometry.slotAt(screen,
                minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                minecraft.mouseHandler.getScaledYPos(minecraft.getWindow())));
    }

    private void resolvePairing(Slot slot) {
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

    private boolean startBind(MouseButtonEvent event, LocalPlayer player, Slot slot, int containerSlot) {
        if (!SlotGeometry.isPlayerSlot(slot, creative) || anythingCarried(player)) return true;
        originWasBound = Bindings.consistentPartner(containerSlot) != -1;
        Bindings.unbind(containerSlot);
        BindingsStore.saveIfDirty();
        originContainerSlot = containerSlot;
        consumedButton = event.button();
        return false;
    }

    private boolean trySwap(MouseButtonEvent event, MultiPlayerGameMode gameMode, LocalPlayer player,
                            Slot slot, int containerSlot) {
        validatePickupArmed();
        boolean shiftClick = event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.hasShiftDown()
                && (!Bindings.isHotbarSlot(containerSlot) || SlotPlusConfig.isHotbarShiftSwapEnabled());
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE && !shiftClick) return true;
        if (!SlotGeometry.isPlayerSlot(slot, creative)) return true;
        int partner = Bindings.consistentPartner(containerSlot);
        if (partner == -1) return true;

        boolean sent;
        if (pickupSwapSlot != -1) {
            if (containerSlot != pickupSwapSlot && partner != pickupSwapSlot) return true;
            sent = sendClick(player, gameMode, SlotGeometry.menuSlotOfContainer(containerSlot), 0, ContainerInput.PICKUP, containerSlot);
            if (sent) pickupSwapSlot = -1;
        } else {
            if (anythingCarried(player)) return true;
            if (!Bindings.isHotbarSlot(containerSlot) && !Bindings.isHotbarSlot(partner)) {
                if (!SlotPlusConfig.isInventoryPairsEnabled() || slot.getItem().isEmpty()) return true;
                sent = sendClick(player, gameMode, SlotGeometry.menuSlotOfContainer(containerSlot), 0, ContainerInput.PICKUP, containerSlot);
                if (sent) pickupSwapSlot = containerSlot;
            } else {
                int hotbarSide = Bindings.hotbarSideOf(containerSlot, partner);
                int otherSide = containerSlot == hotbarSide ? partner : containerSlot;
                sent = sendClick(player, gameMode, SlotGeometry.menuSlotOfContainer(otherSide), hotbarSide, ContainerInput.SWAP, containerSlot);
            }
        }
        if (sent) {
            consumedButton = event.button();
            return false;
        }
        return true;
    }

    private boolean sendClick(LocalPlayer player, MultiPlayerGameMode gameMode, int menuSlot, int button,
                              ContainerInput input, int rateLimitKey) {
        return creative
                ? swapSender.trySendCreative(player.inventoryMenu, player, menuSlot, button, input, rateLimitKey)
                : swapSender.trySend(player.inventoryMenu, gameMode, player, menuSlot, button, input, rateLimitKey);
    }

    private boolean anythingCarried(LocalPlayer player) {
        return !screen.getMenu().getCarried().isEmpty() || !player.inventoryMenu.getCarried().isEmpty();
    }

    private void validatePickupArmed() {
        LocalPlayer player = minecraft.player;
        if (pickupSwapSlot != -1 && (player == null || !anythingCarried(player))) {
            pickupSwapSlot = -1;
        }
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
            showMessage(Component.translatable(SlotPlusClient.MOD_ID + ".msg.cleared"));
            playClick();
        }
    }

    private void clearGesture() {
        originContainerSlot = -1;
        consumedButton = -1;
        pickupSwapSlot = -1;
    }

    private boolean isValidPartner(Slot slot) {
        if (slot == null || originContainerSlot == -1) return false;
        if (!SlotGeometry.isPlayerSlot(slot, creative)) return false;
        return canPartner(originContainerSlot, SlotGeometry.containerSlotOf(slot, creative));
    }

    boolean canPartner(int origin, int target) {
        if (!Bindings.isBindable(origin) || !Bindings.isBindable(target) || origin == target) return false;
        return Bindings.isHotbarSlot(origin) || Bindings.isHotbarSlot(target) || SlotPlusConfig.isInventoryPairsEnabled();
    }

    void dismissTutorial() {
        SlotPlusConfig.HANDLER.instance().showTutorial = false;
        tutorialOpen = false;
        SlotPlusConfig.HANDLER.save();
    }

    private void showMessage(Component message) {
        this.message = message;
        this.messageUntil = Util.getMillis() + 2500;
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
