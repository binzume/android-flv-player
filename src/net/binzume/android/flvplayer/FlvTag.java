package net.binzume.android.flvplayer;

import java.nio.ByteBuffer;

public class FlvTag {
	public static final int TAG_AUDIO = 8;
	public static final int TAG_VIDEO = 9;
	public static final int TAG_SCRIPT = 18;

	public static final int CODEC_AUDIO_MP3 = 2;
	public static final int CODEC_AUDIO_AAC = 10;
	public static final int CODEC_AUDIO_MP3_8 = 14;

	public static final int CODEC_VIDEO_H263 = 2;
	public static final int CODEC_VIDEO_VP6 = 4;
	public static final int CODEC_VIDEO_VP6A = 5;
	public static final int CODEC_VIDEO_AVC = 7;

	public int tagType;
	public int timestamp;
	public int dataSize;
	public int codec;
	public ByteBuffer data;

	public FlvTag(ByteBuffer buf) {
		data = buf;
	}

	public int getAudioCodec() {
		return codec >> 4;
	}

	public int getAudioFreq() {
		int freq = 44100;
		switch ((codec >> 2) & 3) {
		case 0:
			freq = 5500;
			break;
		case 1:
			freq = 11000;
			break;
		case 2:
			freq = 22000;
			break;
		case 3:
			freq = 44100;
			break;
		}
		return freq;
	}

	public int getAudioChannels() {
		return 1 + (codec & 1);
	}

	public int getVideoCodec() {
		return (codec & 0x0f);
	}

	public boolean isVideoKeyframe() {
		return (codec >> 4 & 0x0f) == 1;
	}

}
