#!/usr/bin/env python3
"""
Generates the "modern block" support used by the ViaBlocks client port:
  * assets/minecraft/{blockstates,models/block,textures/block} entries
  * net/minecraft/block/ModernBlocks.java (registry)
Textures are fetched from the InventivetalentDev/minecraft-assets mirror on GitHub.
"""
import json, os, sys, urllib.request, shutil

WS = "/home/claude/ws/eaglercraft-1.14-workspace-main"
RES = WS + "/desktopRuntime/resources/assets/minecraft"
JAVA_OUT = WS + "/src/main/java/net/minecraft/block/ModernBlocks.java"
CACHE = "/home/claude/data/tex"
BR = "1.21.4"
BASE = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/%s/assets/minecraft/textures/block/" % BR
os.makedirs(CACHE, exist_ok=True)

def fetch(tex):
    p = "%s/%s.png" % (CACHE, tex)
    if os.path.exists(p):
        return os.path.getsize(p) > 0
    try:
        with urllib.request.urlopen(BASE + tex + ".png", timeout=30) as r:
            data = r.read()
        # skip animated textures (we do not ship .mcmeta); they are tall strips
        if len(data) < 8 or data[:4] != b"\x89PNG":
            raise ValueError("not png")
        w = int.from_bytes(data[16:20], "big"); h = int.from_bytes(data[20:24], "big")
        if w != h:
            # animated strip: keep only the first frame (we do not ship .mcmeta)
            if h % w != 0:
                open(p, "wb").close()
                return False
            from PIL import Image
            import io
            im = Image.open(io.BytesIO(data)).convert("RGBA").crop((0, 0, w, w))
            buf = io.BytesIO(); im.save(buf, "PNG"); data = buf.getvalue()
        open(p, "wb").write(data)
        return True
    except Exception as e:
        open(p, "wb").close()
        return False

# ---------------------------------------------------------------- definitions
# prop presets: (material, hardness, resistance, sound, light)
ST = ("ROCK", 1.5, 6.0, "STONE", 0)
DS = ("ROCK", 3.0, 6.0, "STONE", 0)
DSB = ("ROCK", 3.5, 6.0, "STONE", 0)
TF = ("ROCK", 1.5, 6.0, "STONE", 0)
CU = ("ROCK", 3.0, 6.0, "METAL", 0)
WD = ("WOOD", 2.0, 3.0, "WOOD", 0)
LOG = ("WOOD", 2.0, 2.0, "WOOD", 0)
BS = ("ROCK", 1.5, 6.0, "STONE", 0)
EARTH = ("EARTH", 0.5, 0.5, "GROUND", 0)

blocks = []   # dicts: name, kind, tex..., props
def cube(name, props, tex=None):
    blocks.append(dict(name=name, kind="cube", tex=tex or name, props=props))
def pillar(name, props, side, end):
    blocks.append(dict(name=name, kind="pillar", side=side, end=end, props=props))
def cbt(name, props, top, side, bottom):
    blocks.append(dict(name=name, kind="cbt", top=top, side=side, bottom=bottom, props=props))
def glass(name):
    blocks.append(dict(name=name, kind="glass", tex=name, props=("GLASS", 0.3, 0.3, "GLASS", 0)))

# --- deepslate / tuff / mud / misc overworld
pillar("deepslate", ("ROCK", 3.0, 6.0, "STONE", 0), "deepslate", "deepslate_top")
for n in ["cobbled_deepslate"]: cube(n, DSB)
for n in ["polished_deepslate", "deepslate_bricks", "cracked_deepslate_bricks", "deepslate_tiles",
          "cracked_deepslate_tiles", "chiseled_deepslate"]: cube(n, DSB)
for n, ore in [("deepslate_coal_ore","coal"),("deepslate_iron_ore","iron"),("deepslate_copper_ore","copper"),
               ("deepslate_gold_ore","gold"),("deepslate_redstone_ore","redstone"),("deepslate_emerald_ore","emerald"),
               ("deepslate_lapis_ore","lapis"),("deepslate_diamond_ore","diamond")]:
    cube(n, ("ROCK", 4.5, 3.0, "STONE", 0))
cube("copper_ore", ("ROCK", 3.0, 3.0, "STONE", 0))
cube("tuff", TF)
for n in ["polished_tuff", "tuff_bricks"]: cube(n, TF)
cbt("chiseled_tuff", TF, "chiseled_tuff_top", "chiseled_tuff", "chiseled_tuff_top")
cbt("chiseled_tuff_bricks", TF, "chiseled_tuff_bricks_top", "chiseled_tuff_bricks", "chiseled_tuff_bricks_top")
cube("calcite", ("ROCK", 0.75, 0.75, "STONE", 0))
cube("amethyst_block", ("ROCK", 1.5, 1.5, "GLASS", 0))
cube("budding_amethyst", ("ROCK", 1.5, 1.5, "GLASS", 0))
cube("moss_block", ("ORGANIC", 0.1, 0.1, "PLANT", 0))
cube("rooted_dirt", ("EARTH", 0.5, 0.5, "GROUND", 0))
cube("mud", ("EARTH", 0.5, 0.5, "WET_GRASS", 0))
cube("packed_mud", ("EARTH", 1.0, 3.0, "STONE", 0))
cube("mud_bricks", ("ROCK", 1.5, 3.0, "STONE", 0))
cube("dripstone_block", ("ROCK", 1.5, 1.0, "STONE", 0))
cube("smooth_basalt", ("ROCK", 1.25, 4.2, "STONE", 0))
for n in ["raw_iron_block", "raw_copper_block", "raw_gold_block"]: cube(n, ("ROCK", 5.0, 6.0, "STONE", 0))
glass("tinted_glass")

# --- copper
for pre in ["", "exposed_", "weathered_", "oxidized_"]:
    cube(pre + "copper_block" if pre == "" else pre + "copper", CU, tex=(pre + "copper_block") if pre == "" else (pre + "copper"))
    cube(pre + "cut_copper", CU)
    cube(pre + "chiseled_copper", CU)
    cube(pre + "copper_grate", CU)

# --- wood types (1.17-1.21)
for w in ["bamboo", "cherry", "mangrove", "crimson", "warped", "pale_oak"]:
    cube(w + "_planks", WD)
cube("bamboo_mosaic", WD)
pillar("mangrove_log", LOG, "mangrove_log", "mangrove_log_top")
pillar("stripped_mangrove_log", LOG, "stripped_mangrove_log", "stripped_mangrove_log_top")
pillar("mangrove_wood", LOG, "mangrove_log", "mangrove_log")
pillar("stripped_mangrove_wood", LOG, "stripped_mangrove_log", "stripped_mangrove_log")
pillar("cherry_log", LOG, "cherry_log", "cherry_log_top")
pillar("stripped_cherry_log", LOG, "stripped_cherry_log", "stripped_cherry_log_top")
pillar("cherry_wood", LOG, "cherry_log", "cherry_log")
pillar("stripped_cherry_wood", LOG, "stripped_cherry_log", "stripped_cherry_log")
pillar("pale_oak_log", LOG, "pale_oak_log", "pale_oak_log_top")
pillar("stripped_pale_oak_log", LOG, "stripped_pale_oak_log", "stripped_pale_oak_log_top")
pillar("pale_oak_wood", LOG, "pale_oak_log", "pale_oak_log")
pillar("stripped_pale_oak_wood", LOG, "stripped_pale_oak_log", "stripped_pale_oak_log")
pillar("bamboo_block", LOG, "bamboo_block", "bamboo_block_top")
pillar("stripped_bamboo_block", LOG, "stripped_bamboo_block", "stripped_bamboo_block_top")

# --- nether
for s in ["crimson", "warped"]:
    pillar(s + "_stem", LOG, s + "_stem", s + "_stem_top")
    pillar("stripped_" + s + "_stem", LOG, "stripped_" + s + "_stem", "stripped_" + s + "_stem_top")
    pillar(s + "_hyphae", LOG, s + "_stem", s + "_stem")
    pillar("stripped_" + s + "_hyphae", LOG, "stripped_" + s + "_stem", "stripped_" + s + "_stem")
    cbt(s + "_nylium", ("ROCK", 0.4, 0.4, "STONE", 0), s + "_nylium", s + "_nylium_side", "netherrack")
cube("warped_wart_block", ("ORGANIC", 1.0, 1.0, "NETHER_WART", 0))
cube("shroomlight", ("ORGANIC", 1.0, 1.0, "PLANT", 15))
cube("netherite_block", ("IRON", 50.0, 1200.0, "METAL", 0))
cbt("ancient_debris", ("ROCK", 30.0, 1200.0, "METAL", 0), "ancient_debris_top", "ancient_debris_side", "ancient_debris_top")
cube("crying_obsidian", ("ROCK", 50.0, 1200.0, "STONE", 10))
cube("soul_soil", ("EARTH", 0.5, 0.5, "SAND", 0))
cube("nether_gold_ore", ("ROCK", 3.0, 3.0, "STONE", 0))
cube("cracked_nether_bricks", ("ROCK", 2.0, 6.0, "STONE", 0))
cube("chiseled_nether_bricks", ("ROCK", 2.0, 6.0, "STONE", 0))
cbt("blackstone", BS, "blackstone_top", "blackstone", "blackstone_top")
for n in ["polished_blackstone", "chiseled_polished_blackstone", "polished_blackstone_bricks",
          "cracked_polished_blackstone_bricks", "gilded_blackstone"]: cube(n, BS)
pillar("basalt", ("ROCK", 1.25, 4.2, "STONE", 0), "basalt_side", "basalt_top")
pillar("polished_basalt", ("ROCK", 1.25, 4.2, "STONE", 0), "polished_basalt_side", "polished_basalt_top")
cube("honeycomb_block", ("EARTH", 0.6, 0.6, "CORAL", 0))
cbt("target", ("ORGANIC", 0.5, 0.5, "GRASS" if False else "PLANT", 0), "target_top", "target_side", "target_top")
cbt("lodestone", ("IRON", 3.5, 3.5, "METAL", 0), "lodestone_top", "lodestone_side", "lodestone_top")

# --- stairs / slabs / walls: (name_stem, base_block_name, kinds)
families = []
def fam(stem, base, stairs=True, slab=True, wall=False):
    families.append(dict(stem=stem, base=base, stairs=stairs, slab=slab, wall=wall))
fam("cobbled_deepslate", "cobbled_deepslate", wall=True)
fam("polished_deepslate", "polished_deepslate", wall=True)
fam("deepslate_brick", "deepslate_bricks", wall=True)
fam("deepslate_tile", "deepslate_tiles", wall=True)
fam("tuff", "tuff", wall=True)
fam("polished_tuff", "polished_tuff", wall=True)
fam("tuff_brick", "tuff_bricks", wall=True)
fam("mud_brick", "mud_bricks", wall=True)
fam("blackstone", "blackstone", wall=True)
fam("polished_blackstone", "polished_blackstone", wall=True)
fam("polished_blackstone_brick", "polished_blackstone_bricks", wall=True)
for pre in ["", "exposed_", "weathered_", "oxidized_"]:
    fam(pre + "cut_copper", pre + "cut_copper")
for w in ["bamboo", "cherry", "mangrove", "crimson", "warped", "pale_oak"]:
    fam(w, w + "_planks")
fam("bamboo_mosaic", "bamboo_mosaic")

# ----------------------------------------------------------------- generation
def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, separators=(",", ":"))

def tex_ref(t): return "block/" + t

def base_textures(b):
    """(bottom, top, side) texture names of a registered cube-ish block."""
    k = b["kind"]
    if k in ("cube", "glass"): return (b["tex"],) * 3
    if k == "cbt": return (b["bottom"], b["top"], b["side"])
    if k == "pillar": return (b["end"], b["end"], b["side"])
    raise ValueError(k)

byname = {}
used_tex = set()
reg_lines = []    # java lines
missing = []

def need(*texs):
    ok = True
    for t in texs:
        if not fetch(t):
            ok = False
            missing.append(t)
    return ok

def emit_block_assets(b):
    n = b["name"]; k = b["kind"]
    if k in ("cube", "glass"):
        if not need(b["tex"]): return False
        write_json(f"{RES}/models/block/{n}.json", {"parent": "block/cube_all", "textures": {"all": tex_ref(b["tex"])}})
        write_json(f"{RES}/blockstates/{n}.json", {"variants": {"": {"model": "block/" + n}}})
        used_tex.add(b["tex"])
    elif k == "cbt":
        if not need(b["top"], b["side"], b["bottom"]): return False
        write_json(f"{RES}/models/block/{n}.json", {"parent": "block/cube_bottom_top", "textures":
                   {"top": tex_ref(b["top"]), "side": tex_ref(b["side"]), "bottom": tex_ref(b["bottom"])}})
        write_json(f"{RES}/blockstates/{n}.json", {"variants": {"": {"model": "block/" + n}}})
        used_tex.update([b["top"], b["side"], b["bottom"]])
    elif k == "pillar":
        if not need(b["side"], b["end"]): return False
        write_json(f"{RES}/models/block/{n}.json", {"parent": "block/cube_column", "textures":
                   {"end": tex_ref(b["end"]), "side": tex_ref(b["side"])}})
        write_json(f"{RES}/blockstates/{n}.json", {"variants": {
            "axis=y": {"model": "block/" + n},
            "axis=z": {"model": "block/" + n, "x": 90},
            "axis=x": {"model": "block/" + n, "x": 90, "y": 90}}})
        used_tex.update([b["side"], b["end"]])
    return True

SND = {"STONE","WOOD","GROUND","PLANT","METAL","GLASS","SAND","WET_GRASS","CORAL","NETHER_WART","BAMBOO","CLOTH","SLIME"}

def props_expr(p):
    mat, h, r, snd, light = p
    assert snd in SND, snd
    s = "Block.Properties.create(Material.%s).hardnessAndResistance(%sF, %sF).sound(SoundType.%s)" % (mat, h, r, snd)
    if light: s += ".lightValue(%d)" % light
    return s

ok_blocks = []
for b in blocks:
    if b["name"] in byname:
        continue
    if emit_block_assets(b):
        byname[b["name"]] = b
        ok_blocks.append(b)

vanilla_stairs = json.load(open(RES + "/blockstates/cobblestone_stairs.json"))
vanilla_wall = open(RES + "/blockstates/cobblestone_wall.json").read()

def emit_family(f):
    base = byname.get(f["base"])
    if base is None:
        return []
    bottom, top, side = base_textures(base)
    out = []
    stem = f["stem"]
    if f["stairs"]:
        n = stem + "_stairs"
        for suffix, parent in [("", "block/stairs"), ("_inner", "block/inner_stairs"), ("_outer", "block/outer_stairs")]:
            write_json(f"{RES}/models/block/{n}{suffix}.json", {"parent": parent, "textures":
                       {"bottom": tex_ref(bottom), "top": tex_ref(top), "side": tex_ref(side)}})
        s = json.dumps(vanilla_stairs).replace("block/cobblestone_stairs", "block/" + n)
        write_json(f"{RES}/blockstates/{n}.json", json.loads(s))
        out.append(("stairs", n, f["base"]))
    if f["slab"]:
        n = stem + "_slab"
        for suffix, parent in [("", "block/slab"), ("_top", "block/slab_top")]:
            write_json(f"{RES}/models/block/{n}{suffix}.json", {"parent": parent, "textures":
                       {"bottom": tex_ref(bottom), "top": tex_ref(top), "side": tex_ref(side)}})
        write_json(f"{RES}/blockstates/{n}.json", {"variants": {
            "type=bottom": {"model": "block/" + n}, "type=top": {"model": "block/" + n + "_top"},
            "type=double": {"model": "block/" + f["base"]}}})
        out.append(("slab", n, f["base"]))
    if f["wall"]:
        n = stem + "_wall"
        for suffix, parent in [("_post", "block/template_wall_post"), ("_side", "block/template_wall_side")]:
            write_json(f"{RES}/models/block/{n}{suffix}.json", {"parent": parent, "textures": {"wall": tex_ref(side)}})
        write_json(f"{RES}/blockstates/{n}.json", json.loads(vanilla_wall.replace("block/cobblestone_wall", "block/" + n)))
        out.append(("wall", n, f["base"]))
    return out

derived = []
for f in families:
    derived += emit_family(f)

# ------------------------------------------------------------------ copy textures
tex_dir = RES + "/textures/block"
copied = 0
for t in sorted(used_tex):
    src = "%s/%s.png" % (CACHE, t)
    dst = "%s/%s.png" % (tex_dir, t)
    if os.path.exists(dst):
        continue  # never overwrite a texture that already ships with the workspace
    if os.path.getsize(src) > 0:
        shutil.copyfile(src, dst)
        copied += 1

# ------------------------------------------------------------------ java
L = []
L.append("package net.minecraft.block;")
L.append("")
L.append("import java.util.HashMap;")
L.append("import java.util.Map;")
L.append("import net.minecraft.block.material.Material;")
L.append("import net.minecraft.util.ResourceLocation;")
L.append("import net.minecraft.util.registry.Registry;")
L.append("")
L.append("/**")
L.append(" * Blocks added after 1.14.4 that the ViaBlocks client port needs to be able to display")
L.append(" * (deepslate, tuff, copper, mud, bamboo/cherry/mangrove wood, nether 1.16 blocks, ...).")
L.append(" * They are registered after every vanilla block so vanilla block-state ids are unchanged.")
L.append(" * GENERATED by gen_modern_blocks.py - do not edit by hand.")
L.append(" */")
L.append("public final class ModernBlocks {")
L.append("    private static final Map<String, Block> BY_NAME = new HashMap<>();")
L.append("    private static boolean done;")
L.append("")
L.append("    private ModernBlocks() {")
L.append("    }")
L.append("")
L.append("    private static final class ViaStairs extends StairsBlock {")
L.append("        ViaStairs(BlockState state, Block.Properties props) {")
L.append("            super(state, props);")
L.append("        }")
L.append("    }")
L.append("")
L.append("    private static Block reg(String name, Block block) {")
L.append("        Registry.register(Registry.BLOCK, name, block);")
L.append("        BY_NAME.put(name, block);")
L.append("        return block;")
L.append("    }")
L.append("")
L.append("    /** @return the modern block registered under this (namespace-less) name, or null */")
L.append("    public static Block get(String name) {")
L.append("        return BY_NAME.get(name);")
L.append("    }")
L.append("")
L.append("    public static int count() {")
L.append("        return BY_NAME.size();")
L.append("    }")
L.append("")
L.append("    /** Called once from the static initialiser of {@link Blocks}, before block-state ids are assigned. */")
L.append("    static void register() {")
L.append("        if (done) {")
L.append("            return;")
L.append("        }")
L.append("        done = true;")
for b in ok_blocks:
    n = b["name"]; k = b["kind"]
    pe = props_expr(b["props"])
    if k == "pillar":
        L.append(f'        reg("{n}", new RotatedPillarBlock({pe}));')
    elif k == "glass":
        L.append(f'        reg("{n}", new GlassBlock({pe}.notSolid()));' if False else
                 f'        reg("{n}", new GlassBlock({pe}));')
    else:
        L.append(f'        reg("{n}", new Block({pe}));')
for kind, n, base in derived:
    bp = byname[base]["props"]
    pe = props_expr(bp)
    if kind == "stairs":
        L.append(f'        reg("{n}", new ViaStairs(BY_NAME.get("{base}").getDefaultState(), {pe}));')
    elif kind == "slab":
        L.append(f'        reg("{n}", new SlabBlock({pe}));')
    else:
        L.append(f'        reg("{n}", new WallBlock({pe}));')
L.append("    }")
L.append("}")
open(JAVA_OUT, "w").write("\n".join(L) + "\n")

print("blocks:", len(ok_blocks), "derived:", len(derived), "textures copied:", copied)
print("missing textures:", sorted(set(missing)))
