package net.binzume.android.flvplayer;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.util.Log;
import android.view.Surface;

public class FlvPlayer {
	private static final String TAG = "FlvParser";

	private String path;
	private Surface surface;
	private int state = 0;

	public void setDataSource(String path) {
		this.path = path;
	}

	public void setSurface(Surface surface) {
		this.surface = surface;
	}

	public void play() {
		new Thread() {

			@Override
			public void run() {
				FlvParser parser = new FlvParser();
				try {
					state = 1;
					parser.open(path);
					play(parser, surface);
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					parser.close();
				}
				super.run();
			}

		}.start();
	}

	boolean isPlaying() {
		return state == 1;
	}

	public void stop() {
		state = -1;
	}

	private void play(FlvParser parser, Surface surface) throws IOException {
		Log.d(TAG, "play() start");

		AudioTrack audioTrack = null;
		MediaCodec audioCodec = null;
		ByteBuffer[] audioCodecInputBuffers = null;
		ByteBuffer[] audioCodecOutputBuffers = null;

		MediaCodec videoCodec = null;
		ByteBuffer[] videoCodecInputBuffers = null;
		ByteBuffer[] videoCodecOutputBuffers = null;

		BufferInfo outputBufferInfo = new BufferInfo();

		for (;;) {
			if (state == -1)
				break;

			FlvTag tag = parser.parseNextTag();
			if (tag == null)
				break;

			int tagType = tag.tagType;
			int dataSize = tag.dataSize;
			int timestamp = tag.timestamp;
			ByteBuffer buf = tag.data;

			if (tagType == FlvTag.TAG_VIDEO) {
				String videoCodecType = "";
				int ofs = 0;
				boolean header = false;
				int videoFormat = tag.getVideoCodec();
				if (videoFormat == FlvTag.CODEC_VIDEO_H263) {
					videoCodecType = "video/3gpp";
					// buf.array()[3] &= 0x80; // h263 : clear version bit
					// TODO: fix picture data
					BitStream bs = new BitStream(buf.array());
					bs.readbits(8);
					bs.readbits(22);
					int tr = bs.readbits(8);
					int psize = bs.readbits(3);
					int ptype = bs.readbits(2);
					int df = bs.readbits(1);
					int q = bs.readbits(5);
					Log.d(TAG, "sorenson_h263 pic tr:" + tr + ",sz:" + psize + ",p:" + ptype + ",df:" + df + ",q:" + q);
					// if (ptype != 0 ) continue;

					// buf = ByteBuffer.allocate(655360);
					BitStream bs2 = new BitStream(buf.array());
					bs2.writebits(0, 8); // dummy
					bs2.writebits(0b0000_0000_0000_0000_1_00000, 22); // dummy
					bs2.writebits(tr, 8);

					int ptype263 = 0b10000_011_0_0000 | ((ptype == 0 ? 0 : 1) << 4);
					bs2.writebits(ptype263, 13);

					bs2.writebits(q, 5); // PQUANT

					bs2.writebits(0, 1); // CPM

					while (bs.readbits(1) == 1) {
						bs2.writebits(1, 1); // PEI
						int ext = bs.readbits(8);
						bs2.writebits(ext, 8); // ??
						Log.d(TAG, "sorenson_h263 ext:" + ext);
					}

					bs2.writebits(0, 1); // PEI
					bs2.write(bs, dataSize * 8 - bs.pos);
					bs2.alignByte();
					dataSize = bs2.pos / 8;
				} else if (videoFormat == FlvTag.CODEC_VIDEO_AVC) {
					videoCodecType = "video/avc";
					byte frameType = buf.array()[1];
					timestamp += buf.getInt(1) & 0xffffff; // composition time offset.
					ofs = 4;
					if (frameType == 0) {
						header = true;
						ofs += 4;
						// convert avcC to NALs
						dataSize += 1;
						buf.limit(dataSize);
						int csd0sz = buf.getShort(7 + 4);
						int csd1sz = buf.getShort(8 + 4 + csd0sz + 2);
						Log.d(TAG, "video csd_sz:" + csd0sz + " " + csd1sz);
						buf.position(5 + 4);
						buf.putInt(1);
						buf.position(8 + 4 + csd0sz + 4);
						byte[] cds1 = new byte[csd1sz];
						buf.get(cds1);
						buf.position(8 + 4 + csd0sz + 1);
						buf.putInt(1);
						buf.put(cds1);
						String s = "data=";
						for (int i = ofs + 1; i < dataSize; i++) {
							s += " " + buf.array()[i] + ",";
						}
						Log.d(TAG, "video csd:" + s);
					} else if (frameType == 1) {
						// fix NAL start
						int p = 5;
						while (p + 4 < dataSize) {
							int nalsz = buf.getInt(p);
							buf.position(p);
							buf.putInt(1);
							p += nalsz + 4;
						}
					}
				} else if (videoFormat == FlvTag.CODEC_VIDEO_VP6) {
					videoCodecType = "video/vp6";
				} else {
					videoCodecType = "video/???";
				}

				boolean keyFrame = tag.isVideoKeyframe();

				Log.d(TAG, "video: " + videoCodecType + " " + buf.array()[1] + "(h:" + header + " k:" + keyFrame);

				if (videoFormat != FlvTag.CODEC_VIDEO_AVC && videoFormat != FlvTag.CODEC_VIDEO_H263)
					continue;

				if (videoCodec == null) {
					Log.d(TAG, "video: init videoCodec.");
					videoCodec = MediaCodec.createDecoderByType(videoCodecType);
					MediaFormat vformat = MediaFormat.createVideoFormat(videoCodecType, 1280, 720);
					vformat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 655360);
					videoCodec.configure(vformat, surface, null, 0);
					videoCodec.start();
					videoCodecInputBuffers = videoCodec.getInputBuffers();
					videoCodecOutputBuffers = videoCodec.getOutputBuffers();
				}

				int inputBufIndex = videoCodec.dequeueInputBuffer(10000);
				if (inputBufIndex < 0) {
					Log.d(TAG, "video inputBufIndex " + inputBufIndex);
					continue;
				}

				buf.position(1 + ofs);
				videoCodecInputBuffers[inputBufIndex].position(0);
				videoCodecInputBuffers[inputBufIndex].put(buf);
				videoCodec.queueInputBuffer(inputBufIndex, 0, dataSize - 1 - ofs, timestamp * 1000, header ? MediaCodec.BUFFER_FLAG_CODEC_CONFIG
						: (keyFrame ? MediaCodec.BUFFER_FLAG_SYNC_FRAME : 0));

				int outputBufIndex = videoCodec.dequeueOutputBuffer(outputBufferInfo, 1000);
				if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
					videoCodecOutputBuffers = videoCodec.getOutputBuffers();
					outputBufIndex = videoCodec.dequeueOutputBuffer(outputBufferInfo, 1000);
				}
				if (outputBufIndex >= 0) {
					/*
					// debug..
					ByteBuffer obuf = videoCodecOutputBuffers[outputBufIndex];
					Log.d(TAG, "video size: " + outputBufferInfo.size);
					if (obuf != null) {
						if (surface != null) {
							Canvas c = surface.lockCanvas(null);
							int w = 352 * 2;
							int h = 240 * 2;
							int[] pixbuf = new int[w*h];
							obuf.asIntBuffer().get(pixbuf);
							//c.drawColor(Color.RED);
							c.drawBitmap(pixbuf, 0, w, 0, 0, w, h, false, new Paint());
							surface.unlockCanvasAndPost(c);
						}
					}
					*/

					videoCodec.releaseOutputBuffer(outputBufIndex, surface != null);
				}

			}
			if (tagType == FlvTag.TAG_AUDIO) {
				String audioCodecType = "";
				int ofs = 0;
				int audioFormat = tag.getAudioCodec();
				if (audioFormat == FlvTag.CODEC_AUDIO_MP3) {
					audioCodecType = "audio/mpeg"; // MIMETYPE_AUDIO_MPEG
				} else if (audioFormat == FlvTag.CODEC_AUDIO_AAC) {
					audioCodecType = "audio/mp4a-latm";
					ofs = 1;
				} else {
					audioCodecType = "audio/???";
				}

				if (audioCodec == null) {
					Log.d(TAG, "audio: init audioCodec.");
					int freq = tag.getAudioFreq();
					audioCodec = MediaCodec.createDecoderByType(audioCodecType);
					MediaFormat format = MediaFormat.createAudioFormat(audioCodecType, freq, tag.getAudioChannels());
					audioCodec.configure(format, null, null, 0);
					audioCodec.start();
					audioCodecInputBuffers = audioCodec.getInputBuffers();
					audioCodecOutputBuffers = audioCodec.getOutputBuffers();

					audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, freq, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, 44100,
							AudioTrack.MODE_STREAM);
					audioTrack.play();

				}

				int inputBufIndex = audioCodec.dequeueInputBuffer(10000);
				buf.position(1 + ofs);
				audioCodecInputBuffers[inputBufIndex].position(0);
				audioCodecInputBuffers[inputBufIndex].put(buf);
				audioCodec.queueInputBuffer(inputBufIndex, 0, dataSize - 1 - ofs, timestamp * 1000, 0);

				int outputBufIndex = audioCodec.dequeueOutputBuffer(outputBufferInfo, 1000);
				if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
					audioCodecOutputBuffers = audioCodec.getOutputBuffers();
					outputBufIndex = videoCodec.dequeueOutputBuffer(outputBufferInfo, 1000);
				}
				if (outputBufIndex >= 0) {
					ByteBuffer obuf = audioCodecOutputBuffers[outputBufIndex];
					// audioTrack.write(obuf.array(), obuf.position(), obuf.remaining()); // obuf is readonly....
					byte[] tmpbuf = new byte[outputBufferInfo.size];
					int orgPos = obuf.position();
					obuf.get(tmpbuf);
					obuf.position(orgPos);

					audioTrack.write(tmpbuf, 0, tmpbuf.length);

					Log.d(TAG, "audio write: " + tmpbuf.length);

					try {
						Thread.sleep(20); // FIXME:
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					audioCodec.releaseOutputBuffer(outputBufIndex, false);
				}
			}
		}

		if (audioCodec != null) {
			audioCodec.stop();
			audioCodec.release();
			audioTrack.stop();
		}

		if (videoCodec != null) {
			videoCodec.stop();
			videoCodec.release();
		}

		Log.d(TAG, "play() finish");
	}
}
