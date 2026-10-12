package net.minecraft.client.mods.tuffx;

import java.util.HashMap;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/** Converts 1.12.2 (id, meta) pairs, as sent by the TuffX+ y0 channel, to 1.14.4 block states. */
public final class LegacyBlocks {
	private static HashMap<Integer, String> table;
	private static final BlockState[] CACHE = new BlockState[65536];
	private static final boolean[] RESOLVED = new boolean[65536];

	private LegacyBlocks() {
	}

	private static void load() {
		table = new HashMap<>(4096);
		for (String chunk : LegacyBlockTable.CHUNKS) {
			int start = 0;
			int n = chunk.length();
			while (start < n) {
				int end = chunk.indexOf('\n', start);
				if (end < 0) {
					end = n;
				}
				int colon = chunk.indexOf(':', start);
				int eq = chunk.indexOf('=', start);
				if (colon > 0 && eq > colon && colon < end) {
					int id = Integer.parseInt(chunk.substring(start, colon));
					int meta = Integer.parseInt(chunk.substring(colon + 1, eq));
					table.put(id << 4 | meta, chunk.substring(eq + 1, end));
				}
				start = end + 1;
			}
		}
	}

	public static BlockState get(int id, int meta) {
		int key = (id & 0xFFF) << 4 | (meta & 15);
		if (RESOLVED[key]) {
			return CACHE[key];
		}
		BlockState st = resolve(id & 0xFFF, meta & 15);
		if (st == null) {
			st = Blocks.AIR.getDefaultState();
		}
		CACHE[key] = st;
		RESOLVED[key] = true;
		return st;
	}

	private static BlockState resolve(int id, int meta) {
		if (id == 0) {
			return Blocks.AIR.getDefaultState();
		}
		// fluids carry their flow level in the metadata (8-15 = falling)
		if (id == 8 || id == 9) {
			return BlockStateParser.parse("water[level=" + meta + "]");
		}
		if (id == 10 || id == 11) {
			return BlockStateParser.parse("lava[level=" + meta + "]");
		}
		if (table == null) {
			load();
		}
		String s = table.get(id << 4 | meta);
		if (s == null) {
			s = table.get(id << 4);
		}
		return s == null ? null : BlockStateParser.parse(s);
	}
}
