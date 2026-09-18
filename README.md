# SlotPlus

A port of **NotEnoughUpdates' slot binding** for modern Minecraft (26.2, Fabric).

If you've played Hypixel SkyBlock with NEU you already know the feature: you bind an item slot
in your inventory to a hotbar slot, and from then on one click swaps between them. Great for
things like keeping your dungeon gear, a wand, or a quick-stash stack one click away.

## What it does

- **Bind** any two of your slots inventory to hotbar, or hotbar to hotbar, so you can
  one-click swap two items that both live in your hotbar.
- **Swap** with one click: middle-click or shift-click a bound slot in your inventory and its
  contents trade places with the paired hotbar slot. Works even if one side is empty.
- **Optional hotbar swap** (off by default, toggle in the config): shift-clicking a bound
  hotbar slot swaps it with its bound partner (inventory or another hotbar slot) instead of
  moving the item to the top of your inventory.
- Bound slots show a small icon, and hovering a bound slot draws a line to its partner.

## How to use it

1. Open your inventory (**E**). A short tutorial pops up once, the first time only.
2. **Bind a slot:** hold **B** (the bind key), click the first slot you want, then click the
   second one — you can start from either your inventory or your hotbar, whichever you're
   already looking at. Pink outlines show where you're allowed to click.
3. **Swap:** middle-click the bound slot. Done — your item and the paired item trade places.
4. **Unbind:** hold **B**, click the bound slot, then release **B** anywhere outside the
   valid partner slots. Or just bind it to a different slot instead.

The bind key is a normal Minecraft keybind — change it under
**Options → Controls → Key Binds → SlotPlus**.

## Config

Open it through **Mod Menu → SlotPlus → Configure**. Two options:

| Option | Default | What it does |
|--------|---------|--------------|
| Enable slot binding | On | Master switch for everything above. |
| Hotbar shift-click swap | Off | Shift-clicking a bound hotbar slot swaps it with its bound partner instead of quick-moving the item to the top of your inventory. |

The mod ships enabled, so if nothing seems to happen, make sure it wasn't toggled off.

## Is it allowed on Hypixel?

Like with any mod, and especially any mod that touches inventory behavior, Hypixel's stance
is **use at your own risk**. Nobody can promise you'll never be flagged, and you should
follow the server's rules.

That said, here's why I personally consider it safe: the mod doesn't do anything a player
can't already do by hand. The swap it sends is exactly the same inventory click your own
number-key hotbar swap sends same packet, same kind, one click per input. There is no
automation, no macros, nothing queued, repeated, or done faster than a human could. It just
saves you the two clicks of dragging an item to your hotbar and back.

## Install & Requirements

- [Fabric Loader](https://fabricmc.net/)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl)
- [Mod Menu](https://modrinth.com/mod/modmenu)

Supported Minecraft versions are listed on the (Soon) [Modrinth page](https://modrinth.com/mod/slotplus) and the [GitHub releases](https://github.com/Mitra-88/SlotPlus/releases/latest).

## Credits & license

- Original slot binding concept, behavior, and the `bound.png` icon: **NotEnoughUpdates**
  (NEU). This mod is an independent port of that feature to modern Fabric it is a
  derivative work of NEU and couldn't exist without it. No NEU code is bundled here beyond
  what porting the feature requires.
- Because NEU is licensed under the **GNU Lesser General Public License v3.0 (or any later
  version)**, this port is a modified version of it and is therefore distributed under the
  same terms: **LGPL-3.0-or-later**. The full license text ships in the `LICENSE` file (the
  LGPL incorporates the GPL-3.0 by reference, so both texts are included) and is embedded in
  every built jar.

Under LGPL-3.0-or-later you're free to use, study, modify, and redistribute this mod, and to
release your own modified versions as long as you keep the same license and stay
LGPL-compatible. See the [GNU LGPL page](https://www.gnu.org/licenses/lgpl-3.0.html) for the
plain details.
