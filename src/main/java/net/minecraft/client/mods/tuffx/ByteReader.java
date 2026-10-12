package net.minecraft.client.mods.tuffx;

import java.nio.charset.StandardCharsets;

/** Minimal big-endian reader matching java.io.DataOutput as written by the TuffX+ server plugin. */
final class ByteReader {
	final byte[] buf;
	int pos;

	ByteReader(byte[] buf) {
		this.buf = buf;
	}

	int remaining() {
		return buf.length - pos;
	}

	int readUnsignedByte() {
		return buf[pos++] & 255;
	}

	boolean readBoolean() {
		return buf[pos++] != 0;
	}

	int readUnsignedShort() {
		int v = ((buf[pos] & 255) << 8) | (buf[pos + 1] & 255);
		pos += 2;
		return v;
	}

	short readShort() {
		return (short) readUnsignedShort();
	}

	int readInt() {
		int v = ((buf[pos] & 255) << 24) | ((buf[pos + 1] & 255) << 16) | ((buf[pos + 2] & 255) << 8) | (buf[pos + 3] & 255);
		pos += 4;
		return v;
	}

	long readLong() {
		long hi = readInt() & 0xFFFFFFFFL;
		long lo = readInt() & 0xFFFFFFFFL;
		return (hi << 32) | lo;
	}

	/** DataInput.readUTF; the strings TuffX+ sends are plain ASCII so UTF-8 decoding is equivalent. */
	String readUTF() {
		int len = readUnsignedShort();
		String s = new String(buf, pos, len, StandardCharsets.UTF_8);
		pos += len;
		return s;
	}
}
