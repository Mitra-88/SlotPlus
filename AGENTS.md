# AGENTS.md — SlotPlus

## What this is

A **client-only Fabric mod** (mod id `slotplus`, package `me.mitra.client`) for **Minecraft 26.2**, Java **25**, Fabric Loader 0.19.5, Loom 1.17.20. It is a port of **NotEnoughUpdates' (NEU) slot binding** to modern Fabric: bind two slots together (hold **B**, click two slots) — inventory↔hotbar or hotbar↔hotbar, with inventory↔inventory behind an opt-in — then one-click swap them with middle-click or shift-click. LGPL-3.0-or-later because it is a NEU derivative — don't relicense or strip attribution.

**HARD REQUIREMENT — no automation, ever.** The mod's whole "is it allowed on Hypixel" argument (the README keeps it to a risk disclaimer; the full argument is spelled out to users in the `slotplus.warning.body` warning-screen text) rests on doing *only* what a player can do by hand: one click-equivalent per human input, rate-limited to one per 50 ms per slot, nothing queued, repeated, automatic, or faster than a human. In the survival inventory that click-equivalent is a single vanilla `ContainerInput.SWAP` click sent through `gameMode.handleContainerInput` (see `SwapSender.trySend`); in the creative inventory (opt-in `creativeSupport`) it is the exact mechanism a manual number-key hotbar swap uses there — one local `inventoryMenu.clicked(SWAP)` + `broadcastChanges()`, which emits the vanilla per-slot `ServerboundSetCreativeModeSlotPacket`s (see `SwapSender.trySendCreative`; the SWAP-packet path is unusable there because the creative screen owns `player.containerMenu`). The one exception is inventory-to-inventory pairs (opt-in `inventoryPairs`), which NEU itself refuses to ship — its words: binding to a normal inventory slot "would require cheats" — so they are warning-gated and swap as two ordinary `ContainerInput.PICKUP` clicks across two human inputs, middle-click one slot then its partner (the same two `SwapSender` senders with `ContainerInput.PICKUP`). Never both clicks from one input, never batched or timed together. No gameplay modification. Any change that automates, batches, or accelerates the click stream violates the design contract — push back on requests that would require it.

## Coding rules (mandatory — apply to every change)

**Purpose:** implement only what is genuinely necessary for the requested feature.

**Core rules**

- No overengineering.
- No unnecessary abstractions.
- No generic framework-like constructs when a simple, direct solution suffices.
- No "future-proofing" without a concrete need.
- No dead helper classes, wrappers, managers, registry layers, or utility collections without a clear current use case.
- No artificially bloated architectures.

**Style guidelines**

- Write simple, direct, readable code.
- Prefer concrete implementations over unnecessary generalization.
- Keep classes small and single-purpose.
- Keep methods short and clear.
- Use self-explanatory names instead of unnecessary comments; comment only where something isn't obvious.

**What to avoid**

- AI-typical "enterprise" patterns for small features.
- Excessive use of interfaces without real added value.
- Builders, factories, services, providers, adapters, etc., unless actually needed.
- Defensive abstractions for hypothetical future use cases.
- Multi-layered architecture for trivial logic.
- Duplicated helper logic in "Utils" just to make code look "cleaner".
- Complex configuration or event systems for simple flows.

**Implementation principle — for every change:**

1. What is the specific requirement?
2. What is the smallest clean solution?
3. Implement exactly that — nothing beyond it.

**Minecraft modding specifics**

- Use vanilla/Fabric APIs directly whenever possible.
- Do not add an abstraction layer just to "decouple" APIs without a valid reason.
- Keep mixins, events, registries, and screens only as complex as necessary.
- HUD, rendering, GUI, and networking code stays functional and minimal.
- No artificial splitting of small features across numerous files.

**Refactoring** happens only for a real benefit: better readability, less duplication, clearer responsibilities, or a necessary technical fix — never purely stylistic preference.

**When in doubt, prefer:** less code, fewer files, less abstraction, less magic.

**Goal:** the code should feel pragmatically and deliberately written by an experienced modder, not like generic AI output.

## Build & run

```bash
./gradlew build        # compile + jar (this is what CI runs, on Java 25)
./gradlew runClient    # launch a dev Minecraft instance with the mod
```

- A minimal JUnit sourceset (`src/test`) covers `Bindings`' invariants only — the game-facing code has no automated tests, so `./gradlew build` (compile + tests + widener validation) remains the only verification gate; run it before finishing any change.
- Source extraction into `agent_sources/src/` is **off by default** — plain `./gradlew build` skips it. Opt in with `./gradlew build -PagentSources` or run `./gradlew extractAgentSources` directly when you need the API sources to read.
- Jar output: `build/libs/slotplus-<version>-fabric-mc26.2.jar`; the jar task embeds `LICENSE` (required by LGPL).
- CI (`.github/workflows/build.yml`) builds on every push to `master` (plus manual dispatch) and, while `releaseDevBuilds=true` in `gradle.properties`, recreates the `nightly` prerelease with auto-generated notes. Dependabot updates gradle + actions daily.
- `run/` is a scratch Minecraft instance (gitignored) — don't treat its contents as source.
- Versions live in `gradle.properties`; `version` is expanded into `fabric.mod.json` at process-resources time.
- `org.gradle.configuration-cache=false` is deliberate — leave it.

## How it works

**No mixins at all.** There is no `mixins.json`. The only vanilla access the mod needs is the GUI geometry of `AbstractContainerScreen`, provided by the access widener `src/main/resources/slotplus.accesswidener` (`leftPos`/`topPos`). Prefer Fabric API screen events; if you truly need more vanilla access, add to the widener instead of introducing mixins.

**Hook model:** `SlotPlusClient.onInitializeClient` registers `ScreenEvents.AFTER_INIT`, which instantiates one `ScreenController` per `InventoryScreen` (vanilla inventory plus modded subclasses such as Skyblocker's replacement screen) and, only when `creativeSupport` is on, per `CreativeModeInventoryScreen` — read at screen-open time, so toggling requires reopening. The controller is thin wiring: it hands the per-screen callbacks (`ScreenMouseEvents.allowMouseClick/allowMouseRelease`, `ScreenKeyboardEvents.afterKeyPress/afterKeyRelease`, `ScreenEvents.afterForeground` render, `ScreenEvents.remove` reset) to `BindGesture` (all input decisions) and `BindingOverlay` (all drawing). The gesture tracks the consumed mouse *button*, so only the matching release is swallowed — vanilla never sees a ghost click and never loses a release it is owed.

**Slot indexing — the part that's easy to get wrong.** Bindings are stored in **container slot numbers**: `0–8` = hotbar (`Bindings.isHotbarSlot`), `0–35` bindable (`MAX_SLOT = 35` — hotbar 0–8 plus main inventory 9–35; armor, offhand, and crafting-grid slots are deliberately *not* bindable). Menu (slot-grid) indices are a different space: main inventory rows are `InventoryMenu.INV_SLOT_START..INV_SLOT_END`, hotbar rows are `USE_ROW_SLOT_START..USE_ROW_SLOT_END`, and hotbar container slot *n* lives at menu index `USE_ROW_SLOT_START + n`. Never convert between the spaces by hand — `SlotGeometry` is the single home for the conversions and for the `isPlayerSlot` filter (crafting-grid container slots alias hotbar slots, so every hover consumer must filter). In creative the wrapper slots break the naive mapping — `SlotGeometry.containerSlotOf(slot, creative)` normalizes hotbar wrappers (raw `getContainerSlot()` 36–44) back to 0–8, and `isPlayerSlot` additionally rejects the visible armor wrappers (raw 5–8) and everything whose `container` is not the player `Inventory` (picker grid, trash slot). Both helpers take the `creative` flag — survival and creative wrappers need different rules for the same raw numbers. `Bindings.consistentPartner` re-validates symmetry on every read and silently self-heals broken pairs.

**Swap path:** both sides of the pair are resolved to `(clickedMenuSlot, hotbarSide)` via `SlotGeometry.menuSlotOfContainer(otherSide)` — survival sends it as one `ContainerInput.SWAP` through `gameMode.handleContainerInput`; creative runs the local `inventoryMenu.clicked(SWAP)` + `broadcastChanges()` vanilla-sync path instead (see the hard requirement above). Both go through `SwapSender` with the 50 ms rate limit keyed by the clicked slot's container slot; a rate-limited click is *not consumed* — it passes through to vanilla instead of being eaten. Inventory-to-inventory pairs (opt-in `inventoryPairs`) instead run the same click shapes as a manual pick-up/place-down: the first middle-click sends one `ContainerInput.PICKUP` on the clicked slot and arms `BindGesture.pickupSwapSlot` (top-of-screen hint panel + pink outline on the partner); the next middle-click on the origin (cancel) or its partner (finish) sends the second `PICKUP`. Clicking anywhere else mid-swap passes through to vanilla untouched — the carried item behaves exactly as if the user had clicked by hand.

**State & persistence:** `Bindings` is a static in-memory model with a dirty flag — no instances, all members static; `hotbarSideOf(a, b)` is the single home for "which side of a pair is the hotbar". `BindingsStore` persists it to `config/slotplus-bindings.json` (plain Gson). Saving happens **immediately after every binding mutation** (bind, eager unbind at gesture start, load-time self-heal); `ClientPlayConnectionEvents.DISCONNECT` and `ClientLifecycleEvents.CLIENT_STOPPING` saves (registered in the initializer) remain as a backstop and also flush the config. `restore` accepts arrays of any length so files from other versions migrate (prefix copy, pad with unbound) instead of being discarded. `SlotPlusConfig` is a YACL v2 autogen config (`ConfigClassHandler`) serialized as JSON5 at `config/slotplus.json5`. Options: `enabled` (master), `hotbarShiftSwap` (off by default), `keybindNote` (a `@Label` pointing at the key-bind options screen), `creativeSupport` (off by default — hooks `CreativeModeInventoryScreen` when on), `inventoryPairs` (off by default — allows main-inventory↔main-inventory pairs, see the swap path and the hard requirement), `showPartnerDigit` (on by default — hides/shows the partner digit on bound icons), and `showTutorial` — a `@TickBox` ("Show the tutorial again"; ticking replays the tutorial, and the gesture auto-unticks it once the tutorial has been shown). There is also a hidden `inventoryPairsWarned` flag (`@SerialEntry` only, no `@AutoGen` — serialized but never shown in the GUI). The `inventoryPairs` option uses the custom `WarningBoolean` autogen factory (registered in `SlotPlusClient`): every time it is toggled on, a vanilla-widget `InventoryPairsWarningScreen` pops over the config — "Yes, I'm aware" applies the option (`option.applyValue()`) and keeps it enabled, and ticking "Don't show me again" records the hidden flag; "No, thank you" or Esc reverts the toggle via `option.requestSet(false)`. Both paths save. YACL caveat (verified against `SimpleStateManager`/`OptionImpl`): autogen options get *pending* semantics — `requestSet`/controller toggles change only the pending value, the backing field is written only on `applyValue()` (YACL's save), and the factory `listener` fires on pending change with `pendingValue`. That is why "Yes" must call `applyValue()` (otherwise the field and the immediate save keep the old value), why the revert goes through `option.requestSet(false)`, and why the other options' field reads only change after YACL's own save.

## Codebase tour

All Java code is under `src/client/java/me/mitra/client/` — **split environment source sets** (`splitEnvironmentSourceSets()` in `build.gradle`): `src/main` holds only resources, and `src/client` is where all code lives.

- `SlotPlusClient` — `ClientModInitializer`. Loads config + bindings, registers the bind key (default **B**, own `KeyMapping.Category` via `KeyMappingHelper.registerKeyMapping`), the `WarningBoolean` option factory, the `AFTER_INIT` screen hook, and the disconnect/stopping saves. Owns `MOD_ID` and `BOUND_ICON`.
- `ScreenController` — thin per-screen wiring: bridges the Fabric screen events to `BindGesture` and `BindingOverlay` for both supported screens; nothing else lives here.
- `BindGesture` — the whole input state machine: bind gesture (hold B, click origin → click/release over a valid partner; release outside = implicit unbind; bind-key held state is tracked from `ScreenKeyboardEvents` press/release into `bindKeyDown`), swap triggers (middle-click works on any bound player slot; shift-click always swaps a bound main-inventory slot, but swaps a bound hotbar slot only with `hotbarShiftSwap` since it otherwise collides with vanilla quick-move; inventory-to-inventory pairs use the two-PICKUP flow via `pickupSwapSlot` instead of one SWAP; a rate-limited swap is not consumed; the shared `canPartner(origin, target)` rule drives both pairing validation and the overlay's target outlines), tutorial state (closed only via its Dismiss label — that click is handled by `BindingOverlay.onMouseClick`, routed before the gesture in `ScreenController`), transient top-of-screen message panels ("Binding cleared", auto-hides after 2.5 s via `showMessage`) and UI click sounds. Persists bindings right after each mutation.
- `SwapSender` — the only place that triggers swaps: two senders, one per click shape — `trySend` (survival, `gameMode.handleContainerInput`) and `trySendCreative` (creative, `inventoryMenu.clicked` + `broadcastChanges`) — each taking the vanilla `ContainerInput` (SWAP for hotbar pairs, PICKUP for the two-click inventory-pair flow) plus the 50 ms per-slot rate limit (`isRateLimited`); they return whether the click was performed.
- `BindingOverlay` — all drawing: bound-icon blit + the pair's hotbar digit (`showPartnerDigit` gates the digit; inventory-to-inventory pairs have no digit since there is no hotbar side), hovered↔partner line, pink target outlines during pairing, the partner outline while an inventory-pair swap is armed, the bind-forbidden hints (while the bind key is held, every armor/offhand/crafting slot shows the vanilla barrier icon — blitted from the vanilla `textures/item/barrier.png` path every frame so texture-pack overrides apply — and hovering one additionally shows its red message in a real tooltip via `setTooltipForNextFrame`, which renders same-frame because the Fabric afterForeground event fires before the screen's deferred-tooltip pass), transient message panels (`slotplus.msg.*`, drawn below the tutorial when that is open) and the tutorial panel (anchored near the top of the screen; its Dismiss label highlights on hover and is the only way to close it). Panels draw rounded with a dim pink border (`fillRounded`), and tutorial words are color-accented via styled translatable args: the bind key and slot nouns pink, click actions yellow. Slot hit-testing goes through `SlotGeometry.slotAt`, which uses `screen.leftPos/topPos` from the access widener.
- `SlotGeometry` — static helpers for the two slot index spaces (`containerSlotOf` with the creative wrapper normalization, `menuSlotOfContainer`, `isPlayerSlot` as a thin check over the `SlotRole` classifier — the single home for the creative/survival wrapper ranges and armor/offhand/crafting verdicts, each forbidden role carrying its message key for the overlay), slot hit-testing, partner/origin slot lookup (`findSlot` — never index `menu.slots` directly, creative menu positions differ), and slot screen-coordinate math. The only place that converts between spaces.
- `Bindings` — static partner-array model (`partner[a] == b && partner[b] == a` invariant), `isHotbarSlot`/`isBindable`/`hotbarSideOf` bounds helpers, dirty tracking, `snapshot`/`restore` for persistence (`restore` accepts any array length for version migration). `bind` accepts any two distinct bindable slots — whether inventory-to-inventory pairs are allowed is gesture policy, not model policy.
- `BindingsStore` — Gson load / save-if-dirty to `config/slotplus-bindings.json`; load runs a self-heal pass over all slots and persists the result; errors are swallowed by design.
- `SlotPlusConfig` — YACL v2 autogen (`@AutoGen` + `@SerialEntry`); static `isEnabled()`/`isCreativeSupportEnabled()`/`isInventoryPairsEnabled()`/`isHotbarShiftSwapEnabled()`/`isPartnerDigitEnabled()` wrappers are how the rest of the code reads it.
- `SlotPlusModMenu` — the only ModMenu hook, returns `SlotPlusConfig.HANDLER.generateGui().generateScreen(parent)`.

**Resources** (`src/main/resources/`) — `fabric.mod.json` (client + modmenu entrypoints, `environment: "client"`, depends fabric-api / YACL / modmenu; `version` templated), `slotplus.accesswidener`, `assets/slotplus/lang/en_us.json` (keybind/category, YACL labels, tutorial + message-panel + hint + warning-screen strings), `assets/slotplus/textures/gui/bound.png` (the NEU-provided bound icon), `assets/slotplus/icon.png`.

## Rules that matter for edits

- **All new code goes in `src/client/java/me/mitra/client/`** — one flat package. Keep classes `final`, helpers `static`, and follow the existing wrapper pattern for config reads.
- **Mappings:** Mojang official mappings (`net.minecraft.resources.Identifier`, `net.minecraft.client.gui.screens.inventory.*`) — don't mix in Yarn names.
- **MC 26.x APIs differ from older tutorials:** GUI drawing uses `GuiGraphicsExtractor` with `RenderPipelines.GUI` / `GUI_TEXTURED`; input callbacks take `MouseButtonEvent`/`KeyEvent`; keymaps register via `KeyMappingHelper` + `KeyMapping.Category.register`; the line "renderer" is a hand-rolled Bresenham loop of 1px `fill` calls. Match the existing controller rather than porting pre-26.x snippets.
- **Event callback semantics:** the mouse handlers are *allow* (return `false` to cancel/consume), key handlers are *after*; if you consume a press, record the button in `BindGesture.consumedButton` so only the matching release is swallowed — never eat releases the user is owed, and never consume a click you didn't act on.
- **Translation keys:** YACL autogen expects `yacl3.config.slotplus:config.<field>` (`.desc` for descriptions) in `en_us.json`; in-game messages use `slotplus.tutorial.*` / `slotplus.msg.*` / `slotplus.warning.*`. New user-visible strings belong in `en_us.json`, not hardcoded.
- **State lifecycle:** the controller is per-screen-instance and resets in `ScreenEvents.remove`; anything long-lived must be registered/reset in `SlotPlusClient.onInitializeClient` like the DISCONNECT/CLIENT_STOPPING saves.

## Gotchas & quirks

- `KeyMapping.isDown()` is always false while any screen is open — vanilla's `KeyboardHandler` only calls `KeyMapping.set` when `screen() == null` (and clears mappings when a screen consumes a key). Never gate screen-time logic on keymap state; track it from `ScreenKeyboardEvents.afterKeyPress/afterKeyRelease` like `BindGesture.bindKeyDown` does.
- Screen gating is `screen instanceof InventoryScreen` (modded replacement screens that subclass it, Skyblocker etc., are supported) plus, with `creativeSupport` on, `CreativeModeInventoryScreen`. The creative screen is special: it is a sibling of `InventoryScreen` with its own `ItemPickerMenu`, it **assigns `player.containerMenu`** for its lifetime, and its slot lists change per tab — the picker tabs hold 45 `CustomCreativeSlot`s over a scratch `SimpleContainer`, the player tab swaps in `SlotWrapper`s whose `getContainerSlot()` returns the *inventoryMenu slot index* (hotbar 36–44, armor 5–8 visible, crafting 0–4 at y=-2000, offhand 45), and the inventory tab also appends a trash `Slot`. Any new consumer of creative slots must go through `SlotGeometry.isPlayerSlot`/`containerSlotOf` or it will misread armor as hotbar and the picker grid as inventory. If you ever change MC versions, re-verify those wrapper ranges in `CreativeModeInventoryScreen` (constructor, `selectTab`) before trusting the normalization.
- `consistentPartner` mutating (self-healing) on read is intentional; don't make it pure.
- Zero logging in the codebase, and `BindingsStore` swallows all IO exceptions — that's the current style, not an oversight to fix.
- The 50 ms rate limit (`RATE_LIMIT_MS`) and one-click-per-input discipline are load-bearing for the Hypixel-safety argument — don't tune them down.
- By default pairs need a hotbar side; with `inventoryPairs` on, the gesture also allows main-inventory↔main-inventory, swapped as two `PICKUP` clicks across two human inputs (see the hard requirement — do not collapse that into one input). Armor, offhand, and crafting-grid slots are deliberately out of scope — don't add them without revisiting the index-space mapping (crafting container slots alias hotbar slots).

## Where to look things up

**`agent_sources/src/` is the primary API reference.** The `extractAgentSources` Gradle task unpacks every dependency `-sources.jar` plus Loom's decompiled Minecraft sources into that folder as plain `.java` files — wiped and re-extracted on every run, which is slow, so it is **not** part of a normal build: pass `-PagentSources` to `build` to include it, or run `./gradlew extractAgentSources` directly (always runs regardless of the flag). Read classes there directly — no jar unzipping needed. Coverage: Minecraft 26.2 (Mojang mappings), Fabric API modules, YACL, ModMenu, loader. `agent_sources/` is gitignored generated output and does not exist on a fresh clone until the first extraction.

## RTK

RTK (`rtk`) is installed and available on PATH. Use RTK commands whenever an equivalent exists to reduce unnecessary CLI output and context usage.

### Rules

- Prefer `rtk` over the normal command when RTK provides an equivalent.
- Use the normal command when RTK does not provide an appropriate equivalent.
- Do not use RTK if the full/raw output is required for the task.
- Do not run both RTK and the normal command just to compare their output.
- RTK only filters/condenses output; it does not change the underlying command's intended behavior.
- If RTK hides information needed to continue, use `rtk recall` when applicable or run the normal command.

### Common replacements

- `ls` → `rtk ls`
- `tree` → `rtk tree`
- `cat` / file reading → `rtk read`
- `find` → `rtk find`
- `grep` → `rtk grep`
- `rg` → `rtk rg`
- `git ...` → `rtk git ...`
- `gh ...` → `rtk gh ...`
- `curl ...` → `rtk curl ...`
- `wget ...` → `rtk wget ...`

Maven has no RTK equivalent — run `mvn` normally.

### Useful specialized commands

- Use `rtk test` when only test failures/results are needed.
- Use `rtk err` when only errors and warnings are relevant.
- Use `rtk diff` for a compact diff when the full diff is unnecessary.
- Use `rtk json` when inspecting JSON output.
- Use `rtk summary` or `rtk smart` when a concise command summary is useful.

Do not blindly replace every command with RTK; if RTK's filtering could hide information needed to continue, run the normal command.

### On this machine

- ZCode's shell is **Git Bash** (win32), not PowerShell — invoke `rtk` normally, never `.\rtk.exe`.
- Installed at `C:\Program Files\rtk-x86_64-pc-windows-msvc\rtk.exe` and on the persisted user PATH.
- Shell env vars don't persist between Bash calls, so `export PATH=...` won't stick. If plain `rtk` isn't found (e.g. ZCode was launched before the PATH entry was added — inherited env is stale until ZCode restarts), call it by absolute path `"/c/Program Files/rtk-x86_64-pc-windows-msvc/rtk.exe"` or fall back to the normal command.
