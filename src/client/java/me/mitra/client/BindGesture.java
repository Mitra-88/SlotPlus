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
import net.minecraft.world.item.ItemStack;

final class BindGesture {
    // SDL mouse button numbering (the 26.3 input layer): 1 = left, 2 = middle, 3 = right.
    private static final int MOUSE_LEFT = 1;
    private static final int MOUSE_MIDDLE = 2;

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
        SlotPlusLog.info("mouse click: button={} shift={} at ({}, {})",
                event.button(), event.hasShiftDown(), (int) event.x(), (int) event.y());

        if (isPairing()) {
            if (bindKeyDown && event.button() == MOUSE_LEFT) {
                Slot slot = SlotGeometry.slotAt(screen, event.x(), event.y());
                if (isValidPartner(slot)) {
                    SlotPlusLog.info("pairing: clicked valid partner {} -> completing pair",
                            SlotGeometry.containerSlotOf(slot, creative));
                    completePairing(SlotGeometry.containerSlotOf(slot, creative));
                    consumedButton = event.button();
                    return false;
                }
                if (isForbidden(slot)) {
                    SlotPlusLog.info("pairing: clicked forbidden {} - click consumed, pairing continues",
                            SlotGeometry.SlotRole.of(slot, creative));
                    consumedButton = event.button();
                    return false;
                }
                SlotPlusLog.info("pairing: clicked non-partner - pairing ends, click passes to vanilla");
                endPairing();
            }
            return true;
        }

        Slot slot = SlotGeometry.slotAt(screen, event.x(), event.y());
        if (slot == null) {
            SlotPlusLog.info("  no slot under cursor - passing through");
            return true;
        }
        int containerSlot = SlotGeometry.containerSlotOf(slot, creative);
        SlotPlusLog.info("  slot: containerSlot={} role={} menuSlot={}", containerSlot,
                SlotGeometry.SlotRole.of(slot, creative), SlotGeometry.menuSlotOfContainer(containerSlot));

        if (bindKeyDown) {
            if (isForbidden(slot) && event.button() != MOUSE_MIDDLE) {
                SlotPlusLog.info("bind mode: clicked forbidden {} - click consumed, nothing sent",
                        SlotGeometry.SlotRole.of(slot, creative));
                consumedButton = event.button();
                return false;
            }
            if (event.button() == MOUSE_LEFT) {
                return startBind(event, player, slot, containerSlot);
            }
        }
        return trySwap(event, gameMode, player, slot, containerSlot);
    }

    boolean onMouseRelease(MouseButtonEvent event) {
        if (event.button() != consumedButton) return true;
        consumedButton = -1;
        SlotPlusLog.info("mouse release (button {}) swallowed to match consumed click", event.button());
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
            SlotPlusLog.info("bind key DOWN (pairing={})", isPairing());
        }
    }

    void onKeyRelease(KeyEvent event) {
        if (!SlotPlusClient.bindKey().matches(event)) return;
        bindKeyDown = false;
        SlotPlusLog.info("bind key UP (pairing={})", isPairing());
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
            SlotPlusLog.info("pairing: released over valid partner {} -> completing pair",
                    SlotGeometry.containerSlotOf(slot, creative));
            completePairing(SlotGeometry.containerSlotOf(slot, creative));
        } else {
            SlotPlusLog.info("pairing: released over {} -> pairing ends", slot == null ? "nothing" : "a non-partner");
            endPairing();
        }
    }

    void reset() {
        clearGesture();
        bindKeyDown = false;
    }

    private boolean startBind(MouseButtonEvent event, LocalPlayer player, Slot slot, int containerSlot) {
        if (!SlotGeometry.isPlayerSlot(slot, creative)) {
            SlotPlusLog.info("bind mode: clicked non-player slot - passing through");
            return true;
        }
        if (anythingCarried(player)) {
            // Bind mode is modal: swallow the click instead of letting vanilla
            // place the carried item into the slot.
            SlotPlusLog.info("bind mode: item on cursor - click consumed so vanilla cannot place it");
            consumedButton = event.button();
            return false;
        }
        int previous = Bindings.consistentPartner(containerSlot);
        originWasBound = previous != -1;
        Bindings.unbind(containerSlot);
        BindingsStore.saveIfDirty();
        originContainerSlot = containerSlot;
        consumedButton = event.button();
        SlotPlusLog.info("bind origin: slot={} (was bound to {}) - now pairing, click another slot or release B",
                containerSlot, previous);
        return false;
    }

    private boolean isForbidden(Slot slot) {
        return slot != null && SlotGeometry.SlotRole.of(slot, creative).messageKey != null;
    }

    private boolean trySwap(MouseButtonEvent event, MultiPlayerGameMode gameMode, LocalPlayer player,
                            Slot slot, int containerSlot) {
        validatePickupArmed();
        boolean middleClick = event.button() == MOUSE_MIDDLE;
        boolean shiftClick = event.button() == MOUSE_LEFT && event.hasShiftDown();
        if (!middleClick && !shiftClick) return true;
        if (!SlotGeometry.isPlayerSlot(slot, creative)) {
            SlotPlusLog.info("swap: not a player slot - passing through");
            return true;
        }
        int partner = Bindings.consistentPartner(containerSlot);
        if (partner == -1) {
            SlotPlusLog.info("swap: slot {} is not bound - passing through", containerSlot);
            return true;
        }
        SlotPlusLog.info("swap try: slot {} is bound to {}", containerSlot, partner);
        if (shiftClick && Bindings.isHotbarSlot(containerSlot)
                && !Bindings.isArmorSlot(partner) && !SlotPlusConfig.isHotbarShiftSwapEnabled()) {
            // Shift-click on a hotbar slot stays vanilla quick-move, except for armor pairs.
            SlotPlusLog.info("  shift-click on hotbar side without armor partner (hotbarShiftSwap off) - vanilla quick-move");
            return true;
        }

        boolean sent;
        if (pickupSwapSlot != -1) {
            if (containerSlot != pickupSwapSlot && partner != pickupSwapSlot) {
                SlotPlusLog.info("  armed flow: clicked unrelated slot - passing through (item stays on cursor)");
                return true;
            }
            SlotPlusLog.info("  armed flow: finishing click on {} - sending second PICKUP", containerSlot);
            sent = sendClick(player, gameMode, SlotGeometry.menuSlotOfContainer(containerSlot), 0, ContainerInput.PICKUP, containerSlot);
            if (sent) pickupSwapSlot = -1;
        } else {
            if (anythingCarried(player)) {
                SlotPlusLog.info("  item on cursor - passing through");
                return true;
            }
            if (!Bindings.isHotbarSlot(containerSlot) && !Bindings.isHotbarSlot(partner)) {
                // The pick-up/place-down flow is middle-click only — one click per input,
                // same as moving the items by hand. Shift-click stays vanilla quick-move.
                if (!middleClick || !SlotPlusConfig.isInventoryPairsEnabled() || slot.getItem().isEmpty()) {
                    SlotPlusLog.info("  no hotbar side and (middleClick={}, inventoryPairs={}, slot has item={}) - passing through",
                            middleClick, SlotPlusConfig.isInventoryPairsEnabled(), !slot.getItem().isEmpty());
                    return true;
                }
                SlotPlusLog.info("  no hotbar side: arming two-click flow - sending PICKUP on menuSlot={}",
                        SlotGeometry.menuSlotOfContainer(containerSlot));
                sent = sendClick(player, gameMode, SlotGeometry.menuSlotOfContainer(containerSlot), 0, ContainerInput.PICKUP, containerSlot);
                if (sent) pickupSwapSlot = containerSlot;
            } else {
                int hotbarSide = Bindings.hotbarSideOf(containerSlot, partner);
                int otherSide = containerSlot == hotbarSide ? partner : containerSlot;
                Slot target = SlotGeometry.findSlot(screen, otherSide, creative);
                ItemStack hotbarItem = player.getInventory().getItem(hotbarSide);
                if (target != null) {
                    if (hotbarItem.isEmpty() && !target.hasItem()) {
                        SlotPlusLog.info("  both sides empty - passing through");
                        return true;
                    }
                    if (!vanillaAcceptsSwap(player, target, hotbarItem)) {
                        // Armor slots honor mayPlace/mayPickup server-side (a helmet is never
                        // accepted by the chestplate slot): don't send a click the server
                        // would silently drop — block the vanilla click and say so instead.
                        SlotPlusLog.info("  rejected client-side: target mayPlace({})={} mayPickup={} targetItem='{}' hotbarItem='{}' - click blocked",
                                otherSide, target.mayPlace(hotbarItem), target.mayPickup(player), target.getItem().getItem(), hotbarItem.getItem());
                        showMessage(Component.translatable(SlotPlusClient.MOD_ID + ".msg.swapRejected"));
                        consumedButton = event.button();
                        return false;
                    }
                }
                SlotPlusLog.info("  sending SWAP: click menuSlot={} (the {} side) with button={} (hotbar side {})",
                        SlotGeometry.menuSlotOfContainer(otherSide),
                        containerSlot == otherSide ? "clicked" : "partner", hotbarSide, hotbarSide);
                sent = sendClick(player, gameMode, SlotGeometry.menuSlotOfContainer(otherSide), hotbarSide, ContainerInput.SWAP, containerSlot);
            }
        }
        if (sent) {
            consumedButton = event.button();
            return false;
        }
        SlotPlusLog.info("  click was not sent (rate limited) - passing through");
        return true;
    }

    // Mirrors AbstractContainerMenu's SWAP case, which honors mayPickup/mayPlace.
    private static boolean vanillaAcceptsSwap(LocalPlayer player, Slot target, ItemStack hotbarItem) {
        if (hotbarItem.isEmpty()) return target.mayPickup(player);
        return target.mayPlace(hotbarItem) && (!target.hasItem() || target.mayPickup(player));
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
            SlotPlusLog.info("armed swap on slot {} disarmed (cursor is empty again)", pickupSwapSlot);
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
        SlotPlusLog.info("pairing ended at origin {} (origin was bound: {})", origin, originWasBound);
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
