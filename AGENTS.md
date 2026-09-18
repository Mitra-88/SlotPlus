# AGENTS.md — SlotPlus

## What this is

A **client-only Fabric mod** (mod id `slotplus`, package `me.mitra.client`) for **Minecraft 26.2**, Java **25**, Fabric Loader 0.19.5, Loom 1.17.20. It is a port of **NotEnoughUpdates' (NEU) slot binding** to modern Fabric: bind an inventory/armor slot to a hotbar slot (hold **B**, click two slots), then one-click swap them with middle-click or shift-click. LGPL-3.0-or-later because it is a NEU derivative — don't relicense or strip attribution.

**HARD REQUIREMENT — no automation, ever.** The mod's whole "is it allowed on Hypixel" argument (see README) rests on doing *only* what a player can do by hand: every swap is a single vanilla `ContainerInput.SWAP` click sent through `gameMode.handleContainerInput`, one click per human input, rate-limited to one per 50 ms per slot (see `InventoryScreenController.sendSwap`). No queued clicks, no repeated/automatic sends, no other outbound packets, no gameplay modification. Any change that automates, batches, or accelerates the click stream violates the design contract — push back on requests that would require it.

## Build & run

```bash
./gradlew build        # compile + jar (this is what CI runs, on Java 25)
./gradlew runClient    # launch a dev Minecraft instance with the mod
```

- No test sourceset exists; `./gradlew build` is the only verification gate — run it before finishing any change.
- Jar output: `build/libs/slotplus-<version>-fabric-mc26.2.jar`; the jar task embeds `LICENSE` (required by LGPL).
- CI (`.github/workflows/build.yml`) builds on every push to `master` and, while `releaseDevBuilds=true` in `gradle.properties`, recreates the `nightly` prerelease with auto-generated notes. Dependabot updates gradle + actions daily.
- `run/` is a scratch Minecraft instance (gitignored) — don't treat its contents as source.
- Versions live in `gradle.properties`; `version` is expanded into `fabric.mod.json` at process-resources time.
- `org.gradle.configuration-cache=false` is deliberate — leave it.

## How it works

**No mixins at all.** There is no `mixins.json`. The only vanilla access the mod needs is the GUI geometry of `AbstractContainerScreen`, provided by the access widener `src/main/resources/slotplus.accesswidener` (`leftPos`/`topPos`). Prefer Fabric API screen events; if you truly need more vanilla access, add to the widener instead of introducing mixins.

**Hook model:** `SlotPlusClient.onInitializeClient` registers `ScreenEvents.AFTER_INIT`, which instantiates one `InventoryScreenController` per screen passing an `instanceof InventoryScreen` check — the vanilla inventory plus modded subclasses such as Skyblocker's replacement screen; `CreativeModeInventoryScreen` extends `AbstractContainerScreen` directly, so creative stays excluded by design. The controller registers per-screen callbacks: `ScreenMouseEvents.allowMouseClick/allowMouseRelease`, `ScreenKeyboardEvents.afterKeyPress/afterKeyRelease`, `ScreenEvents.afterForeground` (render), `ScreenEvents.remove` (reset state). `suppressNextRelease` makes the controller swallow the release of clicks it already consumed so vanilla never sees the ghost click.

**Slot indexing — the part that's easy to get wrong.** Bindings are stored in **container slot numbers** (`InventoryMenu` grid): `0–8` = hotbar (`Bindings.isHotbarSlot`), `0–35` bindable (`MAX_SLOT = 35`, covers main inventory + armor). Menu (slot-grid) indices are a different space: main inventory rows are `InventoryMenu.INV_SLOT_START..INV_SLOT_END`, hotbar rows are `USE_ROW_SLOT_START..USE_ROW_SLOT_END`, and hotbar container slot *n* lives at menu index `USE_ROW_SLOT_START + n`. `Bindings.consistentPartner` re-validates symmetry on every read and silently self-heals broken pairs.

**Swap path:** inventory→hotbar sends `(slot.index, partner)`; hotbar shift-swap (opt-in config) sends `(partnerMenuSlot, containerSlot)`. Both go through `sendSwap` with the 50 ms rate limit keyed by container slot.

**State & persistence:** `Bindings` is an in-memory singleton with a dirty flag; `BindingsStore` persists it to `config/slotplus-bindings.json` (plain Gson). `SlotPlusConfig` is a YACL v2 autogen config (`ConfigClassHandler`) serialized as JSON5 at `config/slotplus.json5`. Both save on `ClientPlayConnectionEvents.DISCONNECT` and `ClientLifecycleEvents.CLIENT_STOPPING` (registered in the initializer). Config options: `enabled` (master), `hotbarShiftSwap` (off by default), plus `tutorialSeen` — a `@SerialEntry` without `@AutoGen`, i.e. persisted but deliberately not a UI option.

## Codebase tour

All Java code is under `src/client/java/me/mitra/client/` — **split environment source sets** (`splitEnvironmentSourceSets()` in `build.gradle`): `src/main` holds only resources, and `src/client` is where all code lives.

- `SlotPlusClient` — `ClientModInitializer`. Loads config + bindings, registers the bind key (default **B**, own `KeyMapping.Category` via `KeyMappingHelper.registerKeyMapping`), the `AFTER_INIT` screen hook, and the disconnect/stopping saves. Owns `MOD_ID` and `BOUND_ICON`.
- `InventoryScreenController` — the whole feature: bind gesture (hold B, click origin → click/release over a valid partner; release outside = implicit unbind), swap triggers (middle-click; shift-click for hotbar with `hotbarShiftSwap`), the once-per-profile tutorial overlay, and all drawing (bound-icon blit, hovered↔partner line, pink target outlines during pairing, tutorial panel). Slot hit-testing via `slotAt` uses `screen.leftPos/topPos` from the access widener.
- `Bindings` — partner-array model (`partner[a] == b && partner[b] == a` invariant), `isHotbarSlot`/`isBindable` bounds helpers, dirty tracking, `snapshot`/`restore` for persistence.
- `BindingsStore` — Gson load / save-if-dirty to `config/slotplus-bindings.json`; errors are swallowed by design.
- `SlotPlusConfig` — YACL v2 autogen (`@AutoGen` + `@SerialEntry`); static `isEnabled()`/`isHotbarShiftSwapEnabled()` wrappers are how the rest of the code reads it.
- `SlotPlusModMenu` — the only ModMenu hook, returns `SlotPlusConfig.HANDLER.generateGui().generateScreen(parent)`.

**Resources** (`src/main/resources/`) — `fabric.mod.json` (client + modmenu entrypoints, `environment: "client"`, depends fabric-api / YACL / modmenu; `version` templated), `slotplus.accesswidener`, `assets/slotplus/lang/en_us.json` (keybind/category, YACL labels, tutorial + action-bar messages), `assets/slotplus/textures/gui/bound.png` (the NEU-provided bound icon), `assets/slotplus/icon.png`.

## Rules that matter for edits

- **All new code goes in `src/client/java/me/mitra/client/`** — one flat package, six classes today. Keep classes `final`, helpers `static`, and follow the existing wrapper pattern for config reads.
- **Mappings:** Mojang official mappings (`net.minecraft.resources.Identifier`, `net.minecraft.client.gui.screens.inventory.*`) — don't mix in Yarn names.
- **MC 26.x APIs differ from older tutorials:** GUI drawing uses `GuiGraphicsExtractor` with `RenderPipelines.GUI` / `GUI_TEXTURED`; input callbacks take `MouseButtonEvent`/`KeyEvent`; keymaps register via `KeyMappingHelper` + `KeyMapping.Category.register`; the line "renderer" is a hand-rolled Bresenham loop of 1px `fill` calls. Match the existing controller rather than porting pre-26.x snippets.
- **Event callback semantics:** the mouse handlers are *allow* (return `false` to cancel/consume), key handlers are *after*; if you consume a press, set `suppressNextRelease` so the matching release doesn't leak to vanilla.
- **Translation keys:** YACL autogen expects `yacl3.config.slotplus:config.<field>` (`.desc` for descriptions) in `en_us.json`; in-game messages use `slotplus.tutorial.*` / `slotplus.msg.*`. New user-visible strings belong in `en_us.json`, not hardcoded.
- **State lifecycle:** the controller is per-screen-instance and resets in `ScreenEvents.remove`; anything long-lived must be registered/reset in `SlotPlusClient.onInitializeClient` like the DISCONNECT/CLIENT_STOPPING saves.

## Gotchas & quirks

- Screen gating is `screen instanceof InventoryScreen`, so modded replacement screens that subclass it (Skyblocker etc.) are supported; creative stays out only because `CreativeModeInventoryScreen` is a sibling, not a subclass. Re-verify that hierarchy when updating MC versions.
- `consistentPartner` mutating (self-healing) on read is intentional; don't make it pure.
- Zero logging in the codebase, and `BindingsStore` swallows all IO exceptions — that's the current style, not an oversight to fix.
- The 50 ms rate limit (`RATE_LIMIT_MS`) and one-click-per-input discipline are load-bearing for the Hypixel-safety argument — don't tune them down.
- Only hotbar↔anything or inventory→hotbar pairs are bindable (`Bindings.bind` rejects pairs without a hotbar side); armor slots bind *to* hotbar but a hotbar origin can't target armor.
- `docs-for-agents/` is **gitignored local-only** — on a fresh clone it won't exist.

## Where to look things up

`docs-for-agents/` is the vendored doc cache — ~67 Markdown files, flat, no index; browse by filename prefix. Read the relevant file before touching screen events, keybinds, YACL config, or the widener.

- `docs.fabricmc.net_develop_*.md` — official Fabric docs (screen events, key mappings, events, access widening, loom, porting…)
- `docs.isxander.dev_yet-another-config-lib_*.md` — YACL v2 config API + autogen GUI builder (controllers, special options)
- `maven.fabricmc.net_docs_fabric-api-0.160.0+26.2_*.md` — Fabric API javadoc dumps; `..._index-all.html.md` is the full class index
- `github.com_LlamaLad7_MixinExtras_wiki_*.md` — MixinExtras wiki (present for reference; this mod uses no mixins)
- `github.com_TerraformersMC_ModMenu_tree_26.2.md`, `www.glfw.org_docs_3.5.1_group__keys.html.md` (key constants), Java 25 API index, MC 26.2 changelog articles, `mcsrc.dev_index.md`

Decompiled/reference sources in the Gradle caches (paths verified on this machine):

- **Minecraft 26.2, Mojang mappings** — mapped jar: `C:\Users\Mitra\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.2\minecraft-merged-deobf-26.2.jar`; full decompiled sources (Vineflower): `C:\Users\Mitra\.gradle\caches\fabric-loom\decompile\v1.zip`.
- **Library sources jars** under `C:\Users\Mitra\.gradle\caches\modules-2\files-2.1\<group>\<artifact>\<version>\<sha1>\` (grab `*-sources.jar` inside the `<sha1>` folder):
  - `net.fabricmc.fabric-api\fabric-api\0.160.0+26.2\`
  - `dev.isxander\yet-another-config-lib\3.9.6+26.2-fabric\` (YACL — config + autogen screen API)
  - `com.terraformersmc\modmenu\20.0.2\`

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

- `ls` → `rtk ls`, `tree` → `rtk tree`, `cat`/file reading → `rtk read`, `find` → `rtk find`
- `grep`/`rg` → `rtk grep` / `rtk rg`, `git ...` → `rtk git ...`, `gh ...` → `rtk gh ...`
- `./gradlew ...` has no RTK equivalent — run Gradle normally.

### Important

Do not blindly replace every command with RTK. When debugging or inspecting exact output, prefer the normal command if RTK's filtering could hide relevant information.

### On this machine

- ZCode's shell is **Git Bash** (win32), not PowerShell — invoke `rtk` normally, never `.\rtk.exe`.
- Installed at `C:\Program Files\rtk-x86_64-pc-windows-msvc\rtk.exe` and on the persisted user PATH.
- Shell env vars don't persist between Bash calls, so `export PATH=...` won't stick. If plain `rtk` isn't found, call it by absolute path `"/c/Program Files/rtk-x86_64-pc-windows-msvc/rtk.exe"` or fall back to the normal command.
