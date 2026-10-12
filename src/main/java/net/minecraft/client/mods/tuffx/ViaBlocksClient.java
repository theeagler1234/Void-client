package net.minecraft.client.mods.tuffx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.mods.Mods;
import net.minecraft.client.multiplayer.ClientChunkProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

/**
 * Client side of the TuffX+ ViaBlocks channels ({@code viablocks:handshake} / {@code viablocks:data}).
 * <p>
 * ViaVersion has to substitute blocks the client does not know. The server therefore also sends the
 * <em>real</em> block states (as strings) of every such block, and the client overrides what it
 * received. Overrides stay recorded per chunk so they can be re-applied when the chunk is re-sent.
 */
public final class ViaBlocksClient {
	private static final ArrayList<String> PALETTE = new ArrayList<>();
	private static final ArrayList<BlockState> RESOLVED = new ArrayList<>();
	private static final ArrayList<Boolean> RESOLVED_FLAG = new ArrayList<>();

	/** chunk (ChunkPos.asLong) -> (packed block position -> palette id) */
	private static final HashMap<Long, HashMap<Long, Integer>> OVERRIDES = new HashMap<>();

	private ViaBlocksClient() {
	}

	static void reset() {
		PALETTE.clear();
		RESOLVED.clear();
		RESOLVED_FLAG.clear();
		OVERRIDES.clear();
	}

	// ---- position packing (matches CustomBlockListener#packLocation) ----
	private static int unpackX(long p) {
		return (int) (p >> 38);
	}

	private static int unpackY(long p) {
		return (int) ((p << 52) >> 52);
	}

	private static int unpackZ(long p) {
		return (int) ((p << 26) >> 38);
	}

	private static BlockState state(int id) {
		if (id < 0 || id >= PALETTE.size()) {
			return null;
		}
		if (!RESOLVED_FLAG.get(id)) {
			RESOLVED.set(id, BlockStateParser.parse(PALETTE.get(id)));
			RESOLVED_FLAG.set(id, Boolean.TRUE);
		}
		return RESOLVED.get(id);
	}

	private static void addPalette(String s) {
		PALETTE.add(s);
		RESOLVED.add(null);
		RESOLVED_FLAG.add(Boolean.FALSE);
	}

	static void handle(byte[] data) {
		if (!Mods.viaBlocks) {
			return;
		}
		ByteReader in = new ByteReader(data);
		String cmd = in.readUTF();
		switch (cmd) {
		case "INIT_PALETTE": {
			int n = in.readInt();
			PALETTE.clear();
			RESOLVED.clear();
			RESOLVED_FLAG.clear();
			for (int i = 0; i < n; ++i) {
				addPalette(in.readUTF());
			}
			break;
		}
		case "NEW_PALETTE_ENTRY":
			addPalette(in.readUTF());
			break;
		case "ADD_CHUNK": {
			int groups = in.readInt();
			HashMap<Long, Boolean> touched = new HashMap<>();
			for (int g = 0; g < groups; ++g) {
				int id = in.readInt();
				int count = in.readInt();
				for (int i = 0; i < count; ++i) {
					long loc = in.readLong();
					long ck = ChunkPos.asLong(unpackX(loc) >> 4, unpackZ(loc) >> 4);
					OVERRIDES.computeIfAbsent(ck, k -> new HashMap<>()).put(loc, id);
					touched.put(ck, Boolean.TRUE);
				}
			}
			for (Long ck : touched.keySet()) {
				applyChunk(chunkAt((int) (long) ck, (int) (ck >> 32)), ck, true);
			}
			break;
		}
		case "ADD_SINGLE": {
			int id = in.readInt();
			long loc = in.readLong();
			int x = unpackX(loc);
			int y = unpackY(loc);
			int z = unpackZ(loc);
			long ck = ChunkPos.asLong(x >> 4, z >> 4);
			if (id == 0) {
				// block was replaced by something ViaVersion can express: the regular block update wins
				HashMap<Long, Integer> m = OVERRIDES.get(ck);
				if (m != null) {
					m.remove(loc);
				}
				break;
			}
			OVERRIDES.computeIfAbsent(ck, k -> new HashMap<>()).put(loc, id);
			BlockState st = state(id);
			ClientWorld w = Minecraft.getInstance().world;
			if (st != null && w != null && chunkAt(x >> 4, z >> 4) != null) {
				w.setBlockState(new BlockPos(x, y, z), st, 2 | 16);
			}
			break;
		}
		default:
			break;
		}
	}

	private static Chunk chunkAt(int cx, int cz) {
		ClientWorld w = Minecraft.getInstance().world;
		if (w == null) {
			return null;
		}
		ClientChunkProvider p = w.getChunkProvider();
		return p.getChunk(cx, cz, ChunkStatus.FULL, false);
	}

	/** Re-apply all recorded overrides of a chunk; called after chunk data (re)arrives. */
	static void onChunkData(Chunk chunk) {
		if (OVERRIDES.isEmpty()) {
			return;
		}
		ChunkPos cp = chunk.getPos();
		long ck = ChunkPos.asLong(cp.x, cp.z);
		applyChunk(chunk, ck, false);
	}

	/** Re-apply the overrides that sit below y=0 after the y0 channel (re)wrote that part of the chunk. */
	static void reapplyBelowZero(Chunk chunk) {
		if (OVERRIDES.isEmpty()) {
			return;
		}
		ChunkPos cp = chunk.getPos();
		HashMap<Long, Integer> m = OVERRIDES.get(ChunkPos.asLong(cp.x, cp.z));
		if (m == null) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Map.Entry<Long, Integer> e : m.entrySet()) {
			long loc = e.getKey();
			int y = unpackY(loc);
			if (y >= 0) {
				continue;
			}
			BlockState st = state(e.getValue());
			if (st != null) {
				chunk.setBelowBlockState(unpackX(loc), y, unpackZ(loc), st);
			}
		}
	}

	private static void applyChunk(Chunk chunk, long ck, boolean rerender) {
		if (chunk == null) {
			return;
		}
		HashMap<Long, Integer> m = OVERRIDES.get(ck);
		if (m == null || m.isEmpty()) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Map.Entry<Long, Integer> e : m.entrySet()) {
			long loc = e.getKey();
			BlockState st = state(e.getValue());
			if (st == null) {
				continue;
			}
			pos.setPos(unpackX(loc), unpackY(loc), unpackZ(loc));
			chunk.setBlockState(pos, st, false);
		}
		if (rerender) {
			ChunkPos cp = chunk.getPos();
			Minecraft.getInstance().worldRenderer.markBlockRangeForRenderUpdate(cp.x * 16 - 1, net.minecraft.world.World.minBuildHeight, cp.z * 16 - 1,
					cp.x * 16 + 16, 255, cp.z * 16 + 16);
		}
	}

	static void onChunkUnload(int x, int z) {
		OVERRIDES.remove(ChunkPos.asLong(x, z));
	}
}
