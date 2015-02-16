package net.binzume.android.flvplayer;

public class BitStream {

	public byte[] buf;

	public int pos;

	public BitStream(int sz) {
		pos = 0;
		buf = new byte[sz];
	}

	public BitStream(byte[] b) {
		pos = 0;
		buf = b;
	}

	public void writebits(int data, int bits) {
		while (bits > 0) {
			int d = data << 32 - bits;
			int w = 8 - (pos & 7);
			int mask = (1 << w) - 1;
			if (w > bits)
				w = bits;
			buf[pos >> 3] &= ~((1 << w) - 1);
			buf[pos >> 3] |= (d >> ((pos & 7) + 24)) & mask;
			bits -= w;
			pos += w;
		}
	}

	public int readbits(int bits) {
		int a = 0;
		while (bits > 0) {
			int r = 8 - (pos & 7);
			int mask = (1 << r) - 1;
			if (r > bits)
				r = bits;
			a = (a << r) | (buf[pos >> 3] & mask) >> (8 - r - (pos & 7));
			bits -= r;
			pos += r;
		}
		return a;
	}

	public void write(BitStream src, int bits) {
		// TODO ...
		while (bits > 32) {
			writebits(src.readbits(32), 32);
			bits -= 32;
		}
		writebits(src.readbits(bits), bits);
	}

	public void alignByte() {
		pos = (pos + 7) & ~7;
	}

	public byte[] bytes() {
		return buf;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Bits(pos:" + pos + ")[");
		for (int i = 0; i < 100 && i < (pos + 7) / 8; i++) {
			sb.append(String.format("%02x,", buf[i]));
		}
		return sb.toString();
	}
}
