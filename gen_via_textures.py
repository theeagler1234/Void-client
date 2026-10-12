#!/usr/bin/env python3
"""
Generates the ViaTextures data (display-only items for everything added after 1.14.4):
  * assets/minecraft/models/item/<name>.json (+ any parent models they need)
  * assets/minecraft/textures/... for every texture those models reference
  * net/minecraft/item/ViaItems.java (registry of the display-only items + English names)

Source of truth for "what is new": PrismarineJS/minecraft-data items.json of 1.21.4 minus 1.14.4.
Models/textures/lang come from the InventivetalentDev/minecraft-assets 1.21.4 mirror.
Existing local files are never overwritten (so the hand-made ModernBlocks models win).

Usage:  python3 gen_via_textures.py            (set VIATEX_CACHE to keep the download cache elsewhere)
"""
import io
import json
import os
import re
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
RES = ROOT + "/desktopRuntime/resources/assets/minecraft"
JAVA_OUT = ROOT + "/src/main/java/net/minecraft/item/ViaItems.java"
CACHE = os.environ.get("VIATEX_CACHE", os.path.join(tempfile.gettempdir(), "viatex-cache"))
BR = "1.21.4"
ASSETS = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/%s/assets/minecraft/" % BR
MCDATA = "https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/%s/items.json"

# items that need a tint (spawn eggs), a special renderer, or are not plain "items"
SKIP_SUFFIX = ("_spawn_egg",)
SKIP_NAMES = {"air", "decorated_pot", "piglin_head"}  # drawn by special renderers in 1.21.4: a plain model would be invisible

lock = threading.Lock()
model_ok = {}      # model path ("item/foo") -> bool
tex_ok = {}        # texture path ("item/foo") -> bool
reasons = {}       # item name -> why it was skipped


def http_get(url, tries=4):
    last = None
    for i in range(tries):
        try:
            with urllib.request.urlopen(url, timeout=40) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            last = e
        except Exception as e:  # network hiccup
            last = e
        time.sleep(1.5 * (i + 1))
    sys.stderr.write("giving up on %s: %s\n" % (url, last))
    return None


def get_asset(rel):
    """bytes of an asset from the 1.21.4 mirror (disk-cached), or None if it does not exist"""
    p = os.path.join(CACHE, "assets", rel)
    if os.path.exists(p):
        data = open(p, "rb").read()
        return data if data else None
    data = http_get(ASSETS + rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p + ".tmp%d" % threading.get_ident(), "wb") as f:
        f.write(data or b"")
    os.replace(p + ".tmp%d" % threading.get_ident(), p)
    return data


def strip_ns(ref):
    return ref[len("minecraft:"):] if ref.startswith("minecraft:") else ref


def write_local(rel, data):
    path = RES + "/" + rel
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp%d" % threading.get_ident()
    with open(tmp, "wb") as f:
        f.write(data)
    os.replace(tmp, path)


def ensure_texture(ref):
    ref = strip_ns(ref)
    with lock:
        if ref in tex_ok:
            return tex_ok[ref]
    ok = False
    rel = "textures/%s.png" % ref
    if os.path.exists(RES + "/" + rel):
        ok = True
    else:
        data = get_asset(rel)
        if data and data[:4] == b"\x89PNG":
            w = int.from_bytes(data[16:20], "big")
            h = int.from_bytes(data[20:24], "big")
            if w == h:
                write_local(rel, data)
                ok = True
            elif w > 0 and h % w == 0:
                # animated strip: keep the first frame only (we do not ship .mcmeta)
                im = Image.open(io.BytesIO(data)).convert("RGBA").crop((0, 0, w, w))
                buf = io.BytesIO()
                im.save(buf, "PNG")
                write_local(rel, buf.getvalue())
                ok = True
    with lock:
        tex_ok[ref] = ok
    return ok


def ensure_model(ref):
    """Makes sure model `ref` (e.g. "item/foo", "block/bar") and everything it needs exists locally."""
    ref = strip_ns(ref)
    if ref.startswith("builtin/"):
        return ref in ("builtin/generated", "builtin/missing")
    with lock:
        if ref in model_ok:
            return model_ok[ref]
    rel = "models/%s.json" % ref
    ok = False
    if os.path.exists(RES + "/" + rel):
        ok = True
    else:
        raw = get_asset(rel)
        if raw:
            try:
                m = json.loads(raw.decode("utf-8"))
                m.pop("overrides", None)  # predicates/models of newer versions are not resolvable here
                ok = True
                if "parent" in m and not ensure_model(m["parent"]):
                    ok = False
                tex = m.get("textures", {})
                for v in tex.values():
                    if not isinstance(v, str):
                        ok = False
                    elif not v.startswith("#") and not ensure_texture(v):
                        ok = False
                if ok:
                    write_local(rel, (json.dumps(m, indent=2) + "\n").encode("utf-8"))
            except Exception as e:
                sys.stderr.write("bad model %s: %s\n" % (ref, e))
                ok = False
    with lock:
        model_ok[ref] = ok
    return ok


def extract_model(node):
    """First plain model reference of a 1.21.4 client item definition (items/<name>.json), or None"""
    if isinstance(node, list):
        for x in node:
            r = extract_model(x)
            if r:
                return r
        return None
    if not isinstance(node, dict):
        return None
    if node.get("type") in ("minecraft:model", "model") and isinstance(node.get("model"), str):
        return node["model"]
    # conditions/selects/ranges/composites: prefer the fallback / "off" branch, then anything
    for key in ("fallback", "on_false", "base"):
        if key in node:
            r = extract_model(node[key])
            if r:
                return r
    for key in ("cases", "entries", "models", "on_true"):
        if key in node:
            r = extract_model(node[key])
            if r:
                return r
    if "model" in node:
        return extract_model(node["model"])
    return None


def ensure_item_model(name):
    """models/item/<name>.json, from the old-style file or (1.21.4 block items) from items/<name>.json"""
    if get_asset("models/item/%s.json" % name) is not None or os.path.exists(RES + "/models/item/%s.json" % name):
        return ensure_model("item/" + name)
    raw = get_asset("items/%s.json" % name)
    if not raw:
        return False
    try:
        parent = extract_model(json.loads(raw.decode("utf-8")).get("model"))
    except Exception:
        return False
    if not parent or not ensure_model(parent):
        return False
    write_local("models/item/%s.json" % name, (json.dumps({"parent": parent}, indent=2) + "\n").encode("utf-8"))
    with lock:
        model_ok["item/" + name] = True
    return True


def renderable(ref, depth=0):
    """True if the model chain actually draws something (own elements, or builtin/generated down the chain).
    1.21.4 special-renderer items (decorated pot, ...) have a model with neither and would be invisible."""
    ref = strip_ns(ref)
    if ref == "builtin/generated":
        return True
    if depth > 16 or ref.startswith("builtin/"):
        return False
    path = RES + "/models/%s.json" % ref
    if not os.path.exists(path):
        return False
    m = json.load(open(path, encoding="utf-8"))
    if m.get("elements"):
        return True
    return "parent" in m and renderable(m["parent"], depth + 1)


def title(name):
    return " ".join(w.capitalize() for w in name.split("_"))


def java_str(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def main():
    os.makedirs(CACHE, exist_ok=True)
    old = {i["name"] for i in json.loads(http_get(MCDATA % "1.14.4").decode())}
    new = {i["name"] for i in json.loads(http_get(MCDATA % BR).decode())}
    todo = sorted(n for n in new - old if n not in SKIP_NAMES and not n.endswith(SKIP_SUFFIX))
    print("1.14.4: %d items, %s: %d items, %d new candidates" % (len(old), BR, len(new), len(todo)))

    lang = json.loads(get_asset("lang/en_us.json").decode("utf-8"))

    def work(name):
        if not ensure_item_model(name):
            reasons[name] = "model or one of its parents/textures is not available"
            return None
        if not renderable("item/" + name):
            reasons[name] = "model draws nothing (special-renderer item)"
            try:
                os.remove(RES + "/models/item/%s.json" % name)
            except OSError:
                pass
            return None
        return name

    with ThreadPoolExecutor(max_workers=12) as ex:
        results = list(ex.map(work, todo))
    ok_names = [n for n in results if n]
    print("usable: %d, skipped: %d" % (len(ok_names), len(reasons)))
    for n, why in sorted(reasons.items())[:80]:
        print("  skipped %s: %s" % (n, why))

    ids, ens = [], []
    for n in ok_names:
        ids.append(n)
        ens.append(lang.get("item.minecraft." + n) or lang.get("block.minecraft." + n) or title(n))

    out = []
    out.append("package net.minecraft.item;\n")
    out.append("import java.util.HashMap;")
    out.append("import java.util.Locale;")
    out.append("import java.util.Map;")
    out.append("import net.minecraft.util.ResourceLocation;")
    out.append("import net.minecraft.util.registry.Registry;\n")
    out.append("/**")
    out.append(" * Display-only items for everything added after 1.14.4 (ViaTextures). They are never sent to or received from a server;")
    out.append(" * they exist so the normal model pipeline bakes an inventory model for each of them, which")
    out.append(" * {@link net.minecraft.client.mods.viatextures.ViaTextures} then swaps in for ViaBackwards-substituted stacks.")
    out.append(" * Registered after every vanilla item (ids unchanged), in no creative tab, and NOT mapped from blocks.")
    out.append(" * GENERATED by gen_via_textures.py - do not edit by hand.")
    out.append(" */")
    out.append("public final class ViaItems {")
    out.append("    private static final Map<String, Item> BY_NAME = new HashMap<>();")
    out.append("    private static boolean done;\n")
    out.append("    private ViaItems() {")
    out.append("    }\n")
    out.append("    private static final String[] ID = {")
    for i in range(0, len(ids), 4):
        out.append("        " + ", ".join(java_str(x) for x in ids[i:i + 4]) + ",")
    out.append("    };\n")
    out.append("    private static final String[] EN = {")
    for i in range(0, len(ens), 4):
        out.append("        " + ", ".join(java_str(x) for x in ens[i:i + 4]) + ",")
    out.append("    };\n")
    out.append("    /** @return the display-only item whose (1.21) English name is this, or null */")
    out.append("    public static Item byEnglishName(String name) {")
    out.append("        return BY_NAME.get(name.trim().toLowerCase(Locale.ROOT));")
    out.append("    }\n")
    out.append("    public static int count() {")
    out.append("        return ID.length;")
    out.append("    }\n")
    out.append("    /** Called once from the static initialiser of {@link Items}, after every vanilla item. */")
    out.append("    static void register() {")
    out.append("        if (done) {")
    out.append("            return;")
    out.append("        }")
    out.append("        done = true;")
    out.append("        for (int i = 0; i < ID.length; ++i) {")
    out.append("            ResourceLocation key = new ResourceLocation(ID[i]);")
    out.append("            if (Registry.ITEM.containsKey(key)) {")
    out.append("                continue;")
    out.append("            }")
    out.append("            Item item = Registry.register(Registry.ITEM, key, new Item(new Item.Properties()));")
    out.append("            BY_NAME.putIfAbsent(EN[i].toLowerCase(Locale.ROOT), item);")
    out.append("            StringBuilder sb = new StringBuilder();")
    out.append("            for (String w : ID[i].split(\"_\")) {")
    out.append("                if (!w.isEmpty()) {")
    out.append("                    sb.append(sb.length() == 0 ? \"\" : \" \").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));")
    out.append("                }")
    out.append("            }")
    out.append("            BY_NAME.putIfAbsent(sb.toString().toLowerCase(Locale.ROOT), item);")
    out.append("        }")
    out.append("    }")
    out.append("}")
    with open(JAVA_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print("wrote %s (%d items)" % (JAVA_OUT, len(ids)))


if __name__ == "__main__":
    main()
