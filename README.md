# SlotPlus

A port of **NotEnoughUpdates' slot binding** for modern Minecraft (26.2, Fabric).

If you've played Hypixel SkyBlock with NEU you already know the feature: you bind an item slot
in your inventory to a hotbar slot, and from then on one click swaps between them. Great for
things like keeping your dungeon gear, a wand, or a quick-stash stack one click away.

## What it does

- **Bind** two slots together: an inventory slot to a hotbar slot, or hotbar to hotbar.
- **Swap** their contents with one click on either slot. Works even if one side is empty.
- Bound slots show the number of the hotbar key that swaps them (inventory-to-inventory pairs have no number), and a line to their partner when you hover them.
- Optional toggles in the config: hotbar numbers on bound slots, swap on shift-click for hotbar slots, binding in the creative inventory, and inventory-to-inventory binds (off by default; NEU considers those cheat territory, so the config asks every time you enable them, with a don't-ask-again option). Swapping an inventory-to-inventory pair takes two ordinary clicks: middle-click one slot, then its partner.

## How to use it

1. Open your inventory (**E**). A short tutorial shows up the first time.
2. Hold **B** and click two slots to bind them. Pink outlines show where you can click.
3. Middle-click a bound slot to swap it with its partner. Shift-click on a bound inventory slot also swaps.
4. To unbind, hold **B**, click the slot, then release **B** outside the pink slots.

The bind key can be changed under **Options → Controls → Key Binds → SlotPlus**.

## Is it allowed on Hypixel?

Use at your own risk, like with any mod. Nobody can promise you'll never be flagged.

## Compatibility notes

Mods that also manage your inventory (Skyblocker and similar) hook the same mouse clicks SlotPlus does, so they can interfere with binds and swaps. If binding stops
responding or clicks behave oddly, try toggling those mods off or open an issue so I can fix it.

## Install & Requirements

- [Fabric Loader](https://fabricmc.net/)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl)
- [Mod Menu](https://modrinth.com/mod/modmenu)

Supported Minecraft versions are listed on the (Soon) [Modrinth page](https://modrinth.com/mod/slotplus) and the [GitHub releases](https://github.com/Mitra-88/SlotPlus/releases/latest).

## Credits & license

Slot binding is a feature from **NotEnoughUpdates (NEU)**. I got so used to it on Hypixel
that playing without it stopped feeling right, so I ported it to modern Fabric. The idea and the little `bound.png` icon belong to NEU, the code here is my own implementation of their feature.
Since it's a port of an LGPL project, SlotPlus ships under the same license,  **LGPL-3.0-or-later**. The full text is in the `LICENSE` file and inside every jar.
