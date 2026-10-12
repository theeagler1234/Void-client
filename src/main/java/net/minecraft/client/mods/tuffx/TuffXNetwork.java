package net.minecraft.client.mods.tuffx;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.Unpooled;
import net.minecraft.client.mods.Mods;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CCustomPayloadPacket;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.chunk.Chunk;

/**
 * Entry point for the TuffX+ (github.com/tuffnetwork/tuffXplus) client features:
 * <ul>
 * <li>ViaBlocks - modern blocks on a server running newer than the client</li>
 * <li>Below Y0 - the world below y=0</li>
 * </ul>
 * Both talk to the server plugin through plugin messages.
 */
public final class TuffXNetwork {
	public static final ResourceLocation Y0_CHANNEL = new ResourceLocation("eagler", "below_y0");
	public static final ResourceLocation VIABLOCKS_DATA = new ResourceLocation("viablocks", "data");
	public static final ResourceLocation VIABLOCKS_HANDSHAKE = new ResourceLocation("viablocks", "handshake");
	private static final ResourceLocation REGISTER = new ResourceLocation("register");

	private TuffXNetwork() {
	}

	/** Called right after the join game packet has been handled. */
	public static void onJoin(NetworkManager nm) {
		Y0Client.reset();
		ViaBlocksClient.reset();
		if (!Mods.belowY0 && !Mods.viaBlocks) {
			return;
		}

		// Bukkit only delivers plugin messages on channels the client registered
		StringBuilder channels = new StringBuilder();
		if (Mods.viaBlocks) {
			channels.append(VIABLOCKS_DATA.toString()).append('\0');
		}
		if (Mods.belowY0) {
			channels.append(Y0_CHANNEL.toString()).append('\0');
		}
		String joined = channels.substring(0, channels.length() - 1);
		PacketBuffer reg = new PacketBuffer(Unpooled.buffer());
		reg.writeBytes(joined.getBytes(StandardCharsets.UTF_8));
		nm.sendPacket(new CCustomPayloadPacket(REGISTER, reg));

		if (Mods.belowY0) {
			byte[] sub = "ready2".getBytes(StandardCharsets.UTF_8);
			PacketBuffer b = new PacketBuffer(Unpooled.buffer());
			b.writeInt(0);
			b.writeInt(0);
			b.writeInt(0);
			b.writeByte(sub.length);
			b.writeBytes(sub);
			nm.sendPacket(new CCustomPayloadPacket(Y0_CHANNEL, b));
		}
		if (Mods.viaBlocks) {
			PacketBuffer b = new PacketBuffer(Unpooled.buffer());
			b.writeByte(0);
			nm.sendPacket(new CCustomPayloadPacket(VIABLOCKS_HANDSHAKE, b));
		}
	}

	public static void onDisconnect() {
		Y0Client.reset();
		ViaBlocksClient.reset();
	}

	/** @return true if the channel belongs to TuffX+ and was consumed */
	public static boolean handle(ResourceLocation channel, PacketBuffer buf) {
		boolean y0 = Y0_CHANNEL.equals(channel);
		boolean vb = VIABLOCKS_DATA.equals(channel);
		if (!y0 && !vb) {
			return false;
		}
		int readerIndex = buf.readerIndex();
		byte[] data = new byte[buf.readableBytes()];
		buf.readBytes(data);
		buf.readerIndex(readerIndex);
		try {
			if (y0) {
				Y0Client.handle(data);
			} else {
				ViaBlocksClient.handle(data);
			}
		} catch (RuntimeException e) {
			// a malformed message must never take the connection down
			System.err.println("[TuffX] bad " + channel + " message: " + e);
		}
		return true;
	}

	/** Called whenever chunk data was read into a client chunk. */
	public static void onChunkData(Chunk chunk) {
		Y0Client.onChunkData(chunk);
		ViaBlocksClient.onChunkData(chunk);
	}

	public static void onChunkUnload(int x, int z) {
		Y0Client.onChunkUnload(x, z);
		ViaBlocksClient.onChunkUnload(x, z);
	}
}
