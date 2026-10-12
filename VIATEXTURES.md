# ViaTextures

Items added after 1.14.4 (netherite, copper, amethyst, pottery sherds, bundles, boats of every wood, ...) show their
real texture/model in inventories, hotbar, hands and as dropped items, **without TuffX+** and **with no toggle** (always on).

## How it works
A server running ViaVersion + ViaBackwards cannot send a 1.14.4 client an item it does not know. It sends a stand-in
item instead, and ViaBackwards marks the stack: a `VB|...` NBT key (stack tag and/or `display` tag), plus a `display.Name`
holding the real item's English name when the item had no custom name.

* `net.minecraft.client.mods.viatextures.ViaTextures.resolve(ItemStack)` recognises those stacks (a `VB|` key **and** a
  `display.Name` that matches a known newer item; a renamed normal item has no `VB|` key, so it is never touched).
* `net.minecraft.item.ViaItems` (generated) registers one **display-only** `Item` per newer item, after every vanilla item
  (vanilla ids do not move), in no creative tab and not mapped from any block. They exist only so the normal model
  pipeline bakes an inventory model for each; no code path can put one in a real stack, so they are never serialised to a server.
* `ItemModelMesher.getItemModel(ItemStack)` swaps in the display item's model for a recognised stand-in.
  `ItemColors.getColor` does the same for tints (only mangrove leaves has a fixed tint).
* Tooltips, names, counts, NBT and server behaviour are untouched - only how the stack is drawn changes.

## Regenerating
`python3 gen_via_textures.py` (needs Pillow and network access to GitHub). It diffs the item lists of 1.14.4 and 1.21.4
(PrismarineJS/minecraft-data), imports every new item's model, parent models and textures from the 1.21.4 asset mirror into
`desktopRuntime/resources/assets/minecraft` (existing files are never overwritten, so the hand-made ModernBlocks models win),
and rewrites `ViaItems.java`. Animated textures keep their first frame. Currently 486 items.

## Known limitations / not tested at runtime
* **Not run-tested** (compile-checked only, same error set as before the change). Please test on a ViaVersion+ViaBackwards
  server and report anything that renders as the missing-model cube.
* Needs the stand-in to carry ViaBackwards' `VB|` tag and its added name. A server/ViaBackwards setup that substitutes
  silently (no tag, or with `custom name`) cannot be recognised.
* Not covered: spawn eggs (need per-mob tints), `decorated_pot` and `piglin_head` (special renderers), items that exist in 1.14.4
  under another name. Items 1.21.4 draws with a special renderer would be invisible, so they are skipped.
* Tinted item layers other than mangrove leaves (e.g. the dye overlay of wolf armor) are drawn untinted.
* ~490 more item models and ~300 more textures are baked into the block atlas at startup (slightly longer load).
* Names are matched in English (1.21.4 `en_us`, plus the title-cased registry name).
