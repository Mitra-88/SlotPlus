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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

final class BindGesture {
    // Quick human clicks land roughly 55-135 ms apart; every gap is drawn
    // independently so the sequence stays snappy (dungeon swaps are time-critical)
    // without ever looking machine-spaced. Each click also waits for the server
    // to confirm the previous one before firing.
    private static final long ACTION_CLICK_MIN_GAP_MS = 55;
    private static final long ACTION_CLICK_MAX_GAP_MS = 135;
    private static final long STATE_ID_WAIT_MS = 1500;

    // The mid-sequence cursor check only cares whether the cursor holds an item
    // when the sequence predicts one (and is empty when it doesn't). Comparing
    // full item data here would false-abort whenever the server re-sends an item
    // with re-encoded components, stranding it on the cursor.
    private record QueuedClick(int menuSlot, boolean expectsItem) {
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
            if (bindKeyDown && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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
            if (isForbidden(slot) && event.button() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                SlotPlusLog.info("bind mode: clicked forbidden {} - click consumed, nothing sent",
                        SlotGeometry.SlotRole.of(slot, creative));
                consumedButton = event.button();
                return false;
            }
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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
        int previousPartners = Bindings.consistentPartners(containerSlot).size();
        originWasBound = previousPartners > 0;
        originContainerSlot = containerSlot;
        consumedButton = event.button();
        SlotPlusLog.info("bind origin: slot={} ({} partner(s)) - now pairing; click another slot to add a bind, release B outside to unbind",
                containerSlot, previousPartners);
        return false;
    }

    private boolean isForbidden(Slot slot) {
        return slot != null && SlotGeometry.SlotRole.of(slot, creative).messageKey != null;
    }

    private boolean trySwap(MouseButtonEvent event, MultiPlayerGameMode gameMode, LocalPlayer player,
                            Slot slot, int containerSlot) {
        boolean middleClick = event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        boolean shiftClick = event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.hasShiftDown();
        if (!middleClick && !shiftClick) return true;
        if (!SlotGeometry.isPlayerSlot(slot, creative)) {
            SlotPlusLog.info("swap: not a player slot - passing through");
            return true;
        }
        List<Integer> partners = Bindings.consistentPartners(containerSlot);
        if (partners.isEmpty()) {
            SlotPlusLog.info("swap: slot {} is not bound - passing through", containerSlot);
            return true;
        }
        List<Slot> candidates = orderedPartnerSlots(slot, partners);
        SlotPlusLog.info("swap try: slot {} ({}) is bound to {} partner(s): {}", containerSlot,
                describe(slot.getItem()), partners.size(), describeSlots(candidates));
        if (shiftClick && Bindings.isHotbarSlot(containerSlot)
                && partners.stream().noneMatch(Bindings::isArmorSlot)
                && !SlotPlusConfig.isHotbarShiftSwapEnabled()) {
            // Shift-click on a hotbar slot stays vanilla quick-move, except for armor pairs.
            SlotPlusLog.info("  shift-click on hotbar side without armor partner (hotbarShiftSwap off) - vanilla quick-move");
            return true;
        }
        if (anythingCarried(player)) {
            SlotPlusLog.info("  item on cursor - passing through");
            return true;
        }
        if (candidates.isEmpty()) {
            SlotPlusLog.info("  no bound partner slot on this screen - passing through");
            return true;
        }

        // Try partners in order: the one holding something different from this
        // slot's item comes first, so with two helmets bound to the head, clicking
        // the head always brings in the other helmet. Pairs with a hotbar side use
        // one SWAP; the rest use the three-PICKUP sequence.
        boolean rejected = false;
        boolean rateLimited = false;
        boolean gated = false;
        for (Slot to : candidates) {
            int partnerSlotNum = SlotGeometry.containerSlotOf(to, creative);
            if (Bindings.isHotbarSlot(containerSlot) || Bindings.isHotbarSlot(partnerSlotNum)) {
                int hotbarSide = Bindings.isHotbarSlot(containerSlot) ? containerSlot : partnerSlotNum;
                int otherSide = hotbarSide == containerSlot ? partnerSlotNum : containerSlot;
                ItemStack hotbarItem = player.getInventory().getItem(hotbarSide);
                if (hotbarItem.isEmpty() && !to.hasItem()) continue;
                if (!vanillaAcceptsSwap(player, to, hotbarItem)) {
                    rejected = true;
                    continue;
                }
                SlotPlusLog.info("  sending SWAP: click menuSlot={} (the {} side) with button={} (hotbar side {})",
                        SlotGeometry.menuSlotOfContainer(otherSide),
                        otherSide == containerSlot ? "clicked" : "partner", hotbarSide, hotbarSide);
                if (sendClick(player, gameMode, SlotGeometry.menuSlotOfContainer(otherSide), hotbarSide, ContainerInput.SWAP, containerSlot)) {
                    expectAfterSwap(otherSide, hotbarItem.copy(), hotbarSide, to.getItem().copy());
                    consumedButton = event.button();
                    return false;
                }
                rateLimited = true;
            } else {
                if (!SlotPlusConfig.isInventoryPairsEnabled() || slot.getItem().isEmpty()) {
                    gated = true;
                    continue;
                }
                if (!vanillaAcceptsPickupSwap(player, slot, to)) {
                    rejected = true;
                    continue;
                }
                if (!swapSender.tryBeginAction(containerSlot)) {
                    rateLimited = true;
                    break;
                }
                actionCounter++;
                int first = SlotGeometry.menuSlotOfContainer(containerSlot);
                int second = SlotGeometry.menuSlotOfContainer(partnerSlotNum);
                ItemStack clickedCopy = slot.getItem().copy();
                ItemStack partnerCopy = to.getItem().copy();
                SlotPlusLog.info("swap action #{}: swapping '{}' (slot {}) with '{}' (slot {}) as PICKUP {} -> {} -> {} (random human spacing)",
                        actionCounter, describe(slot.getItem()), containerSlot,
                        describe(to.getItem()), partnerSlotNum, first, second, first);
                long now = Util.getMillis();
                pendingClicks.clear();
                // Each queued click only fires while the cursor state matches the
                // by-hand sequence (empty to pick up, holding the item to place);
                // otherwise the rest is cancelled.
                pendingClicks.add(new QueuedClick(first, false));
                pendingClicks.add(new QueuedClick(second, true));
                pendingClicks.add(new QueuedClick(first, true));
                nextClickAt = now;
                previousClickConfirmed = true;
                previousSentAt = now;
                lastSeenStateId = player.inventoryMenu.getStateId();
                expectAfterSwap(containerSlot, partnerCopy, partnerSlotNum, clickedCopy);
                consumedButton = event.button();
                return false;
            }
        }
        if (gated) {
            SlotPlusLog.info("  no hotbar side and inventoryPairs off - passing through");
        }
        if (rejected) {
            // No candidate passed the vanilla-legality check - block the vanilla
            // click and say so instead of letting the server silently drop it.
            showMessage(Component.translatable(SlotPlusClient.MOD_ID + ".msg.swapRejected"));
            consumedButton = event.button();
            return false;
        }
        if (rateLimited) {
            SlotPlusLog.info("  action was not sent (rate limited) - passing through");
        }
        return true;
    }

    // Orders partner slots so the ones holding something different from this
    // slot's item come first - with two helmets bound to the head, that is the
    // helmet meant to come in. Falls back to the original bind order.
    private List<Slot> orderedPartnerSlots(Slot clicked, List<Integer> partners) {
        List<Slot> candidates = new ArrayList<>();
        for (int p : partners) {
            Slot partnerSlot = SlotGeometry.findSlot(screen, p, creative);
            if (partnerSlot != null) candidates.add(partnerSlot);
        }
        ItemStack mine = clicked.getItem();
        if (!mine.isEmpty()) {
            candidates.sort((a, b) -> Boolean.compare(
                    ItemStack.matches(a.getItem(), mine), ItemStack.matches(b.getItem(), mine)));
        }
        return candidates;
    }

    private String describeSlots(List<Slot> slots) {
        StringBuilder text = new StringBuilder();
        for (Slot candidate : slots) {
            text.append(text.length() == 0 ? "" : ", ")
                    .append(SlotGeometry.containerSlotOf(candidate, creative)).append("=")
                    .append(describe(candidate.getItem()));
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    // Runs once per frame from the afterForeground callback. Flushes due swap
    // clicks, then verifies a finished action against its prediction.
    void tick() {
        flushDueClicks();
        verifyTick();
    }

    // Sends the queued by-hand click sequence. The next click only fires once the
    // server has confirmed the previous one (the menu's stateId advanced) — sending
    // on a stale stateId is what makes strict servers drop clicks — plus a
    // humanized random delay. If the server never confirms in time, the remaining
    // clicks are cancelled instead of sent on a guess, and a cursor state that
    // doesn't match the sequence cancels the rest too.
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
                SlotPlusLog.warn("swap action #{} aborted: the server never confirmed the click within {} ms - remaining click(s) cancelled, place the item by hand",
                        actionCounter, STATE_ID_WAIT_MS);
                pendingClicks.clear();
                verifyDeadline = 0;
                return;
            } else {
                return;
            }
        }
        if (now < nextClickAt) return;
        QueuedClick click = pendingClicks.get(0);
        boolean cursorHasItem = !screen.getMenu().getCarried().isEmpty();
        if (cursorHasItem != click.expectsItem()) {
            SlotPlusLog.warn("swap action #{} aborted mid-sequence: the cursor is {} but the sequence needed it {} - remaining click(s) cancelled, place the item by hand",
                    actionCounter, cursorHasItem ? "holding an item" : "empty", click.expectsItem() ? "holding the item" : "empty");
            pendingClicks.clear();
            verifyDeadline = 0;
            return;
        }
        pendingClicks.remove(0);
        previousSentAt = now;
        previousClickConfirmed = false;
        lastSeenStateId = player.inventoryMenu.getStateId();
        long gap = java.util.concurrent.ThreadLocalRandom.current().nextLong(ACTION_CLICK_MIN_GAP_MS, ACTION_CLICK_MAX_GAP_MS + 1);
        // The gap counts from the send time; the confirmation wait above still has
        // to pass as well, so a click never fires before both are satisfied.
        nextClickAt = now + gap;
        SlotPlusLog.info("swap action #{}: click sent: menuSlot={} button=0 PICKUP ({} click(s) left, next in ~{} ms)",
                actionCounter, click.menuSlot(), pendingClicks.size(), gap);
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
            // Releasing over nothing unbinds the origin from all of its partners.
            Bindings.unbind(origin);
            BindingsStore.saveIfDirty();
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
