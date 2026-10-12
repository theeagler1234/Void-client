package net.minecraft.client.mods.tuffx;

import java.util.HashMap;
import java.util.Optional;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.IProperty;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;

/**
 * Turns a flattened block state string such as {@code minecraft:deepslate[axis=y]} into a
 * 1.14.4 {@link BlockState}. Unknown blocks yield {@code null}; unknown properties/values are
 * ignored so a block never fails to resolve just because the server's version has more properties.
 */
public final class BlockStateParser {
	private static final HashMap<String, BlockState> CACHE = new HashMap<>();
	private static final BlockState MISSING = null;

	private BlockStateParser() {
	}

	public static BlockState parse(String s) {
		if (CACHE.containsKey(s)) {
			return CACHE.get(s);
		}
		BlockState r = parse0(s);
		CACHE.put(s, r);
		return r;
	}

	private static BlockState parse0(String s) {
		int br = s.indexOf('[');
		String name = br < 0 ? s : s.substring(0, br);
		if (name.indexOf(':') < 0) {
			name = "minecraft:" + name;
		}
		ResourceLocation rl = ResourceLocation.tryCreate(name);
		if (rl == null) {
			return MISSING;
		}
		if (!Registry.BLOCK.containsKey(rl) && rl.getPath().startsWith("waxed_")) {
			// waxed copper looks identical to its unwaxed counterpart
			rl = new ResourceLocation(rl.getNamespace(), rl.getPath().substring(6));
		}
		if (!Registry.BLOCK.containsKey(rl)) {
			return MISSING;
		}
		Block block = Registry.BLOCK.getOrDefault(rl);
		BlockState state = block.getDefaultState();
		if (br >= 0 && s.endsWith("]")) {
			String props = s.substring(br + 1, s.length() - 1);
			if (!props.isEmpty()) {
				for (String kv : props.split(",")) {
					int eq = kv.indexOf('=');
					if (eq <= 0) {
						continue;
					}
					state = applyProperty(state, kv.substring(0, eq), kv.substring(eq + 1));
				}
			}
		}
		return state;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static BlockState applyProperty(BlockState state, String key, String value) {
		IProperty prop = state.getBlock().getStateContainer().getProperty(key);
		if (prop == null) {
			return state;
		}
		Optional<Comparable> parsed = prop.parseValue(value);
		if (parsed.isPresent()) {
			return state.with(prop, parsed.get());
		}
		if (prop instanceof BooleanProperty) {
			// 1.16+ walls use none/low/tall instead of true/false
			if (value.equals("low") || value.equals("tall")) {
				return state.with(prop, Boolean.TRUE);
			}
			if (value.equals("none")) {
				return state.with(prop, Boolean.FALSE);
			}
		}
		return state;
	}
}
