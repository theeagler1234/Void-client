package net.minecraft.client.mods.tuffx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.mods.Mods;
import net.minecraft.client.multiplayer.ClientChunkProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

/**
 * Client side of the TuffX+ "Below Y0" channel ({@code eagler:below_y0}).
 * <p>
 * The server sends the part of the world below y=0 (sections -4..-1) as plugin messages because
 * 1.14 chunk packets cannot carry it. Blocks arrive as 1.12.2 (id, meta) pairs plus a packed
 * light byte; they are converted to 1.14 states and stored in {@link Chunk#getBelowSections()}.
 */
public final class Y0Client {
	/** Server said below-y0 is enabled for the current world. */
	private static boolean active;

	/** Sections that arrived before their chunk did, keyed by chunk. */
	private static final LinkedHashMap<Long, ArrayList<byte[]>> PENDING = new LinkedHashMap<Long, ArrayList<byte[]>>() {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, ArrayList<byte[]>> eldest) {
			return size() > 1024;
		}
	};

	private Y0Client() {
	}

	public static boolean isActive() {
		return active;
	}

	static void reset() {
		PENDING.clear();
		setActive(false);
	}

	static void setActive(boolean on) {
		if (on && !Mods.belowY0) {
			return;
		}
		active = on;
		int newMin = on ? -64 : 0;
		if (World.minBuildHeight == newMin) {
			return;
		}
		World.minBuildHeight = newMin;
		Minecraft mc = Minecraft.getInstance();
		if (mc.worldRenderer != null && mc.world != null) {
			// the render section grid is sized by the build height, so it has to be rebuilt
			mc.worldRenderer.loadRenderers();
		}
	}

	static void handle(byte[] data) {
		ByteReader in = new ByteReader(data);
		String cmd = in.readUTF();
		switch (cmd) {
		case "y0_status":
			setActive(in.readBoolean());
			break;
		case "dimension_change":
			PENDING.clear();
			break;
		case "chunk_data":
			if (Mods.belowY0) {
				handleChunkData(data, in);
			}
			break;
		case "block_update":
			if (Mods.belowY0) {
				handleBlockUpdate(in);
			}
			break;
		case "lighting_update":
			if (Mods.belowY0) {
				handleLighting(in);
			}
			break;
		default:
			break;
		}
	}

	private static Chunk loadedChunk(int x, int z) {
		ClientWorld w = Minecraft.getInstance().world;
		if (w == null) {
			return null;
		}
		ClientChunkProvider p = w.getChunkProvider();
		return p.getChunk(x, z, ChunkStatus.FULL, false);
	}

	private static void handleChunkData(byte[] data, ByteReader in) {
		int x = in.readInt();
		int z = in.readInt();
		int sy = in.readInt();
		if (sy < -4 || sy > -1) {
			return;
		}
		Chunk c = loadedChunk(x, z);
		if (c == null) {
			byte[] copy = new byte[data.length];
			System.arraycopy(data, 0, copy, 0, data.length);
			PENDING.computeIfAbsent(ChunkPos.asLong(x, z), k -> new ArrayList<>()).add(copy);
			return;
		}
		applySection(c, sy, data, in.pos, data.length - in.pos);
		refresh(c, x, z);
	}

	/** Called for every chunk data packet: applies any below-y0 sections that were waiting for it. */
	static void onChunkData(Chunk chunk) {
		if (PENDING.isEmpty()) {
			return;
		}
		ChunkPos cp = chunk.getPos();
		ArrayList<byte[]> list = PENDING.remove(ChunkPos.asLong(cp.x, cp.z));
		if (list == null) {
			return;
		}
		for (byte[] d : list) {
			ByteReader in = new ByteReader(d);
			in.readUTF();
			in.readInt();
			in.readInt();
			int sy = in.readInt();
			applySection(chunk, sy, d, in.pos, d.length - in.pos);
		}
		// renderer for this chunk is scheduled by onChunkAdded
	}

	static void onChunkUnload(int x, int z) {
		PENDING.remove(ChunkPos.asLong(x, z));
	}

	private static void applySection(Chunk c, int sy, byte[] d, int off, int len) {
		int baseY = sy * 16;
		for (int y = 0; y < 16; ++y) {
			for (int z = 0; z < 16; ++z) {
				for (int x = 0; x < 16; ++x) {
					int rel = ((y << 8) | (z << 4) | x) * 3;
					int legacy = 0;
					int light = 0;
					if (rel + 3 <= len) {
						legacy = ((d[off + rel] & 255) << 8) | (d[off + rel + 1] & 255);
						light = d[off + rel + 2] & 255;
					}
					BlockState st = LegacyBlocks.get(legacy & 0xFFF, legacy >> 12 & 15);
					c.setBelowBlockState(x, baseY + y, z, st);
					c.setBelowLight(x, baseY + y, z, light);
				}
			}
		}
		// anything ViaBlocks already told us about this chunk takes precedence over the legacy approximation
		ViaBlocksClient.reapplyBelowZero(c);
	}

	private static void handleBlockUpdate(ByteReader in) {
		int x = in.readInt();
		int y = in.readInt();
		int z = in.readInt();
		short v = in.readShort();
		if (y >= 0 || y < -64) {
			return;
		}
		Chunk c = loadedChunk(x >> 4, z >> 4);
		if (c == null) {
			return;
		}
		BlockState st = LegacyBlocks.get(v & 0xFFF, v >> 12 & 15);
		c.setBelowBlockState(x, y, z, st);
		Minecraft.getInstance().worldRenderer.markBlockRangeForRenderUpdate(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
	}

	private static void handleLighting(ByteReader in) {
		int x = in.readInt();
		int z = in.readInt();
		int sy = in.readInt();
		if (sy < -4 || sy > -1 || in.remaining() < 4096) {
			return;
		}
		Chunk c = loadedChunk(x, z);
		if (c == null) {
			return;
		}
		byte[] arr = new byte[4096];
		System.arraycopy(in.buf, in.pos, arr, 0, 4096);
		c.setBelowLightData(sy, arr);
		refresh(c, x, z);
	}

	private static void refresh(Chunk c, int x, int z) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.worldRenderer != null) {
			mc.worldRenderer.scheduleBelowZeroRebuild(x, z);
		}
	}
}
