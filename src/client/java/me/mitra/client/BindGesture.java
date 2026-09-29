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

import java.util.ArrayList;
import java.util.List;

final class BindGesture {
    // SDL mouse button numbering (the 26.3 input layer): 1 = left, 2 = middle, 3 = right.
    private static final int MOUSE_LEFT = 1;
    private static final int MOUSE_MIDDLE = 2;
    // Deliberate human clicks land roughly 90-210 ms apart; every gap is drawn
    // independently so the sequence never looks machine-spaced. Fast mode keeps a
    // randomized 50-120 ms safety floor even when the server confirms instantly.
    private static final long ACTION_CLICK_MIN_GAP_MS = 90;
    private static final long ACTION_CLICK_MAX_GAP_MS = 210;
    private static final long FAST_SWAP_MIN_GAP_MS = 50;
    private static final long FAST_SWAP_MAX_GAP_MS = 120;
    private static final long STATE_ID_WAIT_MS = 750;

    private record QueuedClick(int menuSlot, ItemStack expectedCarried) {
    }

    private final AbstractContainerScreen<?> screen;
    private final Minecraft minecraft;
    private final boolean creative;
    private final SwapSender swapSender = new SwapSender();
    private final List<QueuedClick> pendingClicks = new ArrayList<>();
    private long nextClickAt;
    private long actionCounter;
    private boolean previousClickConfirmed = true;
    private long previousSentAt;
    private int lastSeenStateId;

    private boolean bindKeyDown;
    private int originContainerSlot = -1;
    private int consumedButton = -1;
    private boolean originWasBound;
    private long verifyDeadline;
    private long actionVerifyId;
    private int verifySlotA = -1;
    private int verifySlotB = -1;
    private ItemStack verifyExpectedA = ItemStack.EMPTY;
    private ItemStack verifyExpectedB = ItemStack.EMPTY;

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
            if (!bindKeyDown) SlotPlusLog.info("bind key DOWN (pairing={})", isPairing());
            bindKeyDown = true;
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
        if (!pendingClicks.isEmpty()) {
            SlotPlusLog.warn("swap action #{} cancelled: the screen closed with {} click(s) still pending",
                    actionCounter, pendingClicks.size());
        }
        pendingClicks.clear();
        clearGesture();
        bindKeyDown = false;
        verifyDeadline = 0;
        verifyExpectedA = ItemStack.EMPTY;
        verifyExpectedB = ItemStack.EMPTY;
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
        SlotPlusLog.info("swap try: slot {} ({}) is bound to slot {} ({})", containerSlot,
                describe(slot.getItem()), partner, describe(SlotGeometry.findSlot(screen, partner, creative) == null
                        ? ItemStack.EMPTY : SlotGeometry.findSlot(screen, partner, creative).getItem()));
        if (shiftClick && Bindings.isHotbarSlot(containerSlot)
                && !Bindings.isArmorSlot(partner) && !SlotPlusConfig.isHotbarShiftSwapEnabled()) {
            // Shift-click on a hotbar slot stays vanilla quick-move, except for armor pairs.
            SlotPlusLog.info("  shift-click on hotbar side without armor partner (hotbarShiftSwap off) - vanilla quick-move");
            return true;
        }

        boolean sent;
        if (anythingCarried(player)) {
            SlotPlusLog.info("  item on cursor - passing through");
            return true;
        }
        if (!Bindings.isHotbarSlot(containerSlot) && !Bindings.isHotbarSlot(partner)) {
            // No one-click vanilla swap exists between two non-hotbar slots, so this
            // replays the whole by-hand click sequence from one input: pick up the
            // clicked item, swap it into the partner, and put the partner's item back
            // where the click started. Ends with both slots swapped, cursor empty.
            // The clicks are spaced a humanized random gap apart across frames —
            // servers drop container clicks that arrive in the same tick.
            if (!SlotPlusConfig.isInventoryPairsEnabled() || slot.getItem().isEmpty()) {
                SlotPlusLog.info("  no hotbar side and (inventoryPairs={}, slot has item={}) - passing through",
                        SlotPlusConfig.isInventoryPairsEnabled(), !slot.getItem().isEmpty());
                return true;
            }
            Slot partnerSlot = SlotGeometry.findSlot(screen, partner, creative);
            if (partnerSlot == null || !vanillaAcceptsPickupSwap(player, slot, partnerSlot)) {
                // Don't send a sequence the server would half-execute (e.g. a helmet
                // bound to the chestplate slot) — block the vanilla click and say so.
                SlotPlusLog.info("  rejected client-side: sequence PICKUP {} -> {} -> {} not vanilla-legal - click blocked",
                        containerSlot, partner, containerSlot);
                showMessage(Component.translatable(SlotPlusClient.MOD_ID + ".msg.swapRejected"));
                consumedButton = event.button();
                return false;
            }
            if (!swapSender.tryBeginAction(containerSlot)) {
                SlotPlusLog.info("  action was not sent (rate limited) - passing through");
                return true;
            }
            actionCounter++;
            int first = SlotGeometry.menuSlotOfContainer(containerSlot);
            int second = SlotGeometry.menuSlotOfContainer(partner);
            ItemStack clickedCopy = slot.getItem().copy();
            ItemStack partnerCopy = partnerSlot.getItem().copy();
            SlotPlusLog.info("swap action #{}: swapping '{}' (slot {}) with '{}' (slot {}) as PICKUP {} -> {} -> {} (random human spacing)",
                    actionCounter, describe(slot.getItem()), containerSlot,
                    describe(partnerSlot.getItem()), partner, first, second, first);
            long now = Util.getMillis();
            pendingClicks.clear();
            // Each queued click only fires while the cursor holds exactly what the
            // by-hand sequence predicts; otherwise the rest is cancelled.
            pendingClicks.add(new QueuedClick(first, ItemStack.EMPTY));
            pendingClicks.add(new QueuedClick(second, clickedCopy));
            pendingClicks.add(new QueuedClick(first, partnerCopy));
            nextClickAt = now;
            previousClickConfirmed = true;
            previousSentAt = now;
            lastSeenStateId = player.inventoryMenu.getStateId();
            expectAfterSwap(containerSlot, partnerCopy, partner, clickedCopy);
            consumedButton = event.button();
            return false;
        } else {
            int hotbarSide = Bindings.hotbarSideOf(containerSlot, partner);
            int otherSide = containerSlot == hotbarSide ? partner : containerSlot;
            Slot target = SlotGeometry.findSlot(screen, otherSide, creative);
            if (target == null) {
                // Partner slot not on this screen: refuse to send an unvalidated click.
                SlotPlusLog.info("  partner slot {} not found on this screen - passing through", otherSide);
                return true;
            }
            ItemStack hotbarItem = player.getInventory().getItem(hotbarSide);
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
            SlotPlusLog.info("  sending SWAP: click menuSlot={} (the {} side) with button={} (hotbar side {})",
                    SlotGeometry.menuSlotOfContainer(otherSide),
                    containerSlot == otherSide ? "clicked" : "partner", hotbarSide, hotbarSide);
            sent = sendClick(player, gameMode, SlotGeometry.menuSlotOfContainer(otherSide), hotbarSide, ContainerInput.SWAP, containerSlot);
            if (sent) {
                expectAfterSwap(otherSide, hotbarItem.copy(), hotbarSide, target.getItem().copy());
            }
        }
        if (sent) {
            consumedButton = event.button();
            return false;
        }
        SlotPlusLog.info("  action was not sent (rate limited) - passing through");
        return true;
    }

    // Runs once per frame from the afterForeground callback. Flushes due swap
    // clicks, then verifies a finished action against its prediction.
    void tick() {
        flushDueClicks();
        verifyTick();
    }

    // Sends the queued by-hand click sequence. The next click only fires once the
    // server has confirmed the previous one (the menu's stateId advanced) — sending
    // on a stale stateId is what makes strict servers drop clicks. After the
    // confirmation, fast mode fires immediately; the default adds a humanized
    // random delay. A click that never gets confirmed within 750 ms is sent anyway,
    // and a cursor that doesn't hold the predicted stack cancels the rest.
    private void flushDueClicks() {
        if (pendingClicks.isEmpty()) return;
        LocalPlayer player = minecraft.player;
        if (player == null) {
            SlotPlusLog.warn("swap action cancelled: player is gone ({} click(s) were still pending)", pendingClicks.size());
            pendingClicks.clear();
            return;
        }
        long now = Util.getMillis();
        if (!previousClickConfirmed) {
            int currentStateId = player.inventoryMenu.getStateId();
            if (currentStateId != lastSeenStateId) {
                SlotPlusLog.info("swap action #{}: server confirmed the click in {} ms",
                        actionCounter, now - previousSentAt);
                lastSeenStateId = currentStateId;
                previousClickConfirmed = true;
            } else if (now - previousSentAt >= STATE_ID_WAIT_MS) {
                SlotPlusLog.info("swap action #{}: no server confirmation within {} ms - proceeding anyway",
                        actionCounter, STATE_ID_WAIT_MS);
                previousClickConfirmed = true;
            } else {
                return;
            }
        }
        if (now < nextClickAt) return;
        QueuedClick click = pendingClicks.get(0);
        ItemStack carried = screen.getMenu().getCarried();
        if (!ItemStack.matches(carried, click.expectedCarried())) {
            SlotPlusLog.warn("swap action #{} aborted mid-sequence: cursor holds {} but {} was expected - remaining click(s) cancelled, place the item by hand",
                    actionCounter, describe(carried), describe(click.expectedCarried()));
            pendingClicks.clear();
            verifyDeadline = 0;
            return;
        }
        pendingClicks.remove(0);
        previousSentAt = now;
        previousClickConfirmed = false;
        lastSeenStateId = player.inventoryMenu.getStateId();
        boolean fast = SlotPlusConfig.isFastSwapEnabled();
        long minGap = fast ? FAST_SWAP_MIN_GAP_MS : ACTION_CLICK_MIN_GAP_MS;
        long maxGap = fast ? FAST_SWAP_MAX_GAP_MS : ACTION_CLICK_MAX_GAP_MS;
        long gap = java.util.concurrent.ThreadLocalRandom.current().nextLong(minGap, maxGap + 1);
        // The gap counts from the send time; the confirmation wait above still has
        // to pass as well, so a click never fires before both are satisfied.
        nextClickAt = now + gap;
        SlotPlusLog.info("swap action #{}: click sent: menuSlot={} button=0 PICKUP ({} click(s) left, next in ~{} ms{})",
                actionCounter, click.menuSlot(), pendingClicks.size(), gap, fast ? ", fast mode" : "");
        if (creative) {
            swapSender.sendCreativeActionClick(player.inventoryMenu, player, click.menuSlot());
        } else {
            swapSender.sendActionClick(player.inventoryMenu, gameMode(), player, click.menuSlot());
        }
    }

    private MultiPlayerGameMode gameMode() {
        return minecraft.gameMode;
    }

    // Records the state both slots should have once the server accepts the action.
    // verifyTick compares against it a moment later and reports any correction.
    private void expectAfterSwap(int slotA, ItemStack expectA, int slotB, ItemStack expectB) {
        verifySlotA = slotA;
        verifySlotB = slotB;
        verifyExpectedA = expectA;
        verifyExpectedB = expectB;
        actionVerifyId = actionCounter;
        verifyDeadline = Util.getMillis() + ACTION_CLICK_MAX_GAP_MS * pendingClicks.size() + 2000;
    }

    // Runs once per frame while a verification is pending. Logs exactly one line:
    // either the server accepted the action (both slots match the prediction, exactly
    // or item-for-item) or it corrected something, which is the signal to look here.
    private void verifyTick() {
        if (verifyDeadline == 0 || Util.getMillis() <= verifyDeadline) return;
        verifyDeadline = 0;
        Slot slotA = SlotGeometry.findSlot(screen, verifySlotA, creative);
        Slot slotB = SlotGeometry.findSlot(screen, verifySlotB, creative);
        if (slotA == null || slotB == null) return;
        boolean exact = ItemStack.matches(slotA.getItem(), verifyExpectedA)
                && ItemStack.matches(slotB.getItem(), verifyExpectedB);
        if (exact) {
            SlotPlusLog.info("post-swap check: action #{} accepted - both slots match the prediction exactly",
                    actionVerifyId);
            return;
        }
        boolean sameItems = sameItem(slotA.getItem(), verifyExpectedA) && sameItem(slotB.getItem(), verifyExpectedB);
        if (sameItems) {
            SlotPlusLog.info("post-swap check: action #{} accepted - right items in both slots, server re-sent their data",
                    actionVerifyId);
            return;
        }
        SlotPlusLog.warn("post-swap check FAILED for action #{}: server state differs from prediction - slot {} expected {} found {}, slot {} expected {} found {}",
                actionVerifyId,
                verifySlotA, describe(verifyExpectedA), describe(slotA.getItem()),
                verifySlotB, describe(verifyExpectedB), describe(slotB.getItem()));
    }

    private static boolean sameItem(ItemStack stack, ItemStack expected) {
        return stack.getItem() == expected.getItem() && stack.getCount() == expected.getCount();
    }

    // Mirrors AbstractContainerMenu's SWAP case, which honors mayPickup/mayPlace.
    private static boolean vanillaAcceptsSwap(LocalPlayer player, Slot target, ItemStack hotbarItem) {
        if (hotbarItem.isEmpty()) return target.mayPickup(player);
        return target.mayPlace(hotbarItem) && (!target.hasItem() || target.mayPickup(player));
    }

    // Mirrors the three-PICKUP sequence: pick up the clicked item, swap it with the
    // partner's contents, place the partner's item back into the emptied slot.
    private static boolean vanillaAcceptsPickupSwap(LocalPlayer player, Slot from, Slot to) {
        ItemStack moving = from.getItem();
        if (moving.isEmpty() || !from.mayPickup(player)) return false;
        if (to.getItem().isEmpty() ? !to.mayPlace(moving) : !to.mayPickup(player) || !to.mayPlace(moving)) return false;
        return to.getItem().isEmpty() || from.mayPlace(to.getItem());
    }

    private static String describe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        return "'" + stack.getHoverName().getString() + "' (" + stack.getItem() + " x" + stack.getCount() + ")";
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
