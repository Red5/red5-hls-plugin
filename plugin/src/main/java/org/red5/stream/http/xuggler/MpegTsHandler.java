/*
 * RED5 HLS plugin - https://github.com/mondain/red5-hls-plugin
 * 
 * Copyright 2006-2013 by respective authors (see below). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.stream.http.xuggler;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import org.red5.logging.Red5LoggerFactory;
import org.red5.xuggler.Message;
import org.slf4j.Logger;

import com.xuggle.xuggler.ISimpleMediaFile;
import com.xuggle.xuggler.io.IURLProtocolHandler;

/**
 * An implementation of IURLProtocolHandler that converts into a format that ffmpeg can read.
 * 
 * {@link http://en.wikipedia.org/wiki/MPEG_transport_stream}
 * {@link http://wiki.multimedia.cx/index.php?title=MPEG-2_Transport_Stream}
 * {@link http://neuron2.net/library/mpeg2/iso13818-1.pdf}
 * {@link http://www.ffmpeg.org/ffmpeg-formats.html#mpegts}
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class MpegTsHandler implements IURLProtocolHandler {

	private final Logger log = Red5LoggerFactory.getLogger(this.getClass());

	//file used for debugging byte stream
	private RandomAccessFile raf;

	private MpegTsIoHandler handler;

	private String url;

	@SuppressWarnings("unused")
	private int openFlags;

	private int pmtPid;

	// Only package members can create
	MpegTsHandler(MpegTsIoHandler handler, ISimpleMediaFile metaInfo, String url, int flags) {
		log.debug("ctor handler: {} file: {} url: {} flags: {}", handler, metaInfo, url, flags);
		this.handler = handler;
		this.url = url;
		this.openFlags = flags;
//		if (log.isTraceEnabled()) {
//			// write to a file for debugging
//			try {
//				raf = new RandomAccessFile(String.format("test%s.ts", System.currentTimeMillis()), "rwd");
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//		}
	}

	public int open(String url, int flags) {
		log.debug("open {} flags: {}", url, flags);
		int retval = -1;
		try {
			// and send a header message
			handler.write(new Message(Message.Type.HEADER, null));
			// For an open, we assume the ProtocolManager has done it's job correctly and we're working on the 
			// right input and output streams.
			this.url = url;
			this.openFlags = flags;
			retval = 0;
		} catch (Exception ex) {
			log.warn("Exception during open: {}", ex);
		}
		log.trace("open({}, {}); {}", new Object[] { url, flags, retval });
		return retval;
	}

	/*
	 * These following methods all wrap the unsafe methods in try {} catch {}
	 * blocks to ensure we don't pass an exception back to the native C++
	 * function that calls these.
	 */
	public int close() {
		log.debug("Close {}", url);
		int retval = -1;
		try {
			// As a convention, we send a IMediaDataWrapper object wrapping NULL for end of streams
			handler.write(new Message(Message.Type.END_STREAM, null));
			retval = 0;
		} catch (Exception ex) {
			log.warn("Exception during close: {}", ex);
		}
		if (raf != null) {
			//close debugging file
			try {
				raf.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		log.trace("close({}); {}", url, retval);
		return retval;
	}

	public int read(byte[] buf, int size) {
		int retval = -1;
		try {
			retval = 0;
		} catch (Exception ex) {
			log.warn("Exception during read: {}", ex);
		}
		log.trace("read({}, {}); {}", new Object[] { url, size, retval });
		return retval;
	}

	public long seek(long offset, int whence) {
		log.trace("seek({}, {}, {});", new Object[] { url, offset, whence });
		// Unsupported
		return -1;
	}

	public int write(byte[] buf, int size) {
		log.debug("Write size: {}", size);
		int retval = -1;
		if (raf != null) {
			//write to a file for debugging
			try {
				raf.write(buf);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			// expect chunks of 188 bytes from ffmpeg
			// 204 and 208 are also valid but not supported by this version
			if (size % 188 == 0) {
				log.debug("Data appears to be of the correct size");
			}
			// make sure we break the data into 188 byte chunks
			int chunks = size / 188;
			log.debug("Chunks: {}", chunks);
			// look for sync byte
			if (buf[0] == 0x47) {
				// get first pid
				int pid = ((buf[1] << 8) | (buf[2] & 0xff)) & 0x1fff;
				log.trace("PID: {}", pid);
				if (chunks == 1) {
					if (!handler.hasPAT() && pid == 0) {
						byte[] pat = new byte[188];
						System.arraycopy(buf, 0, pat, 0, 188);
						handler.setPAT(pat);
					} else if (!handler.hasPMT() && pid == 4096) {
						byte[] pmt = new byte[188];
						System.arraycopy(buf, 0, pmt, 0, 188);
						handler.setPMT(pmt);						
					}		
				} else if (chunks == 2) {
					int offset = 0;
					if (pid == 17) {
						offset = 188;
						// get second pid
						pid = ((buf[offset + 1] << 8) | (buf[offset + 2] & 0xff)) & 0x1fff;						
						log.trace("PID: {}", pid);						
					}
					if (!handler.hasPAT() && pid == 0) {
						byte[] pat = new byte[188];
						System.arraycopy(buf, 0, pat, 0, 188);
						handler.setPAT(pat);
					}					
				} else if (chunks > 2) {
					// handle sync with chunks of 3 or more
					int offset = 0;
					if (pid == 17) {
						offset = 188;
						// get second pid
						pid = ((buf[offset + 1] << 8) | (buf[offset + 2] & 0xff)) & 0x1fff;						
						log.trace("PID: {}", pid);						
					}
					if (!handler.hasPAT() && pid == 0) {
						byte[] pat = new byte[188];
						System.arraycopy(buf, 0, pat, 0, 188);
						handler.setPAT(pat);
					}
					// get next pid
					offset += 188;
					pid = ((buf[offset + 1] << 8) | (buf[offset + 2] & 0xff)) & 0x1fff;						
					log.trace("PID: {}", pid);						
					if (!handler.hasPMT() && pid == 4096) {
						byte[] pmt = new byte[188];
						System.arraycopy(buf, 0, pmt, 0, 188);
						handler.setPMT(pmt);						
					}					
				}
			}
			// wrap the bytes that FFMPEG just sent us
			ByteBuffer buffer = ByteBuffer.wrap(buf);
			handler.write(new Message(Message.Type.DATA, buffer));

//			// break it up into chunks
//			byte[] chunk = new byte[188];
//			for (int i = 0; i < chunks; i++) {
//				buffer.get(chunk);
//				// append the new data to the end of our buffer
//				ByteBuffer chunkBuffer = ByteBuffer.wrap(chunk);
//				Message.Type type = parsePacket(chunkBuffer);
//				if (type != Message.Type.NULL) {
//					//send off to the handler
//					handler.write(new Message(type, chunkBuffer));
//					//if (log.isTraceEnabled()) {
//					//	log.trace("Chunk: {}", new String(chunk));
//					//}
//				} else {
//					chunkBuffer.clear();
//				}
//			}
			
			buffer.clear();
			// return that we read size
			retval = size;
		} catch (Exception ex) {
			log.warn("Exception during write: {}", ex);
		}
		log.trace("write({}, {}); {}", new Object[] { url, size, retval });
		return retval;
	}

	/**
	 * Packets are normally 188 bytes but may consist of 204 bytes if a 16 byte
	 * Reed-Solomon error correction data block is included.
	 * <br />
	 * <br />
	 * The simple MPEG transport decoder for in-the-clear programs needs to:
	 * <ul>
	 * <li>read the PAT to find the PMT for a desired program</li>
	 * <li>demultiplex the packets that carry the desired PMT</li>
	 * <li>read the PMT</li>
	 * <li>demultiplex the packets (with PIDs specified in the PMT) into the various elemental streams</li>
	 * </ul>
	 * <br />
	 * Stream types:
	 * <ul>
	 * <li>0x01 11172 Video (mpeg-1)</li>
	 * <li>0x02 13818-2 Video (mpeg-2)</li>
	 * <li>0x03 11172 Audio (mpeg-1)</li>
	 * <li>0x04 13818-3 Audio (mpeg-2)</li>
	 * <li>0x0A 13818-6 Type A</li>
	 * <li>0x0B 13818-6 Type B</li>
	 * <li>0x0C 13818-6 Type C</li>
	 * <li>0x0D 13818-6 Type D</li>
	 * <li>0x0F 13818-7 Audio ADTS / AAC</li>
	 * <li>0x11 14496-3 Audio LATM</li>
	 * <li>0x15 - 0x19 Metadata</li>
	 * <li>0x1A IPMP Mpeg-2</li>
	 * <li>0x1B AVC / h.264 Video</li>
	 * </ul>
	 * 
	 * @param in
	 * @return
	 */
	@SuppressWarnings("unused")
	private Message.Type parsePacket(ByteBuffer in) {
		log.debug("parsePacket");
		//log.trace("parsePacket: {}", in);
		//used to return the packet type
		Message.Type type = Message.Type.DATA;
		in.mark();
		// Sync byte #0 is always 0x47
		byte sync = in.get();
		//log.trace("Packet sync: " + (sync == 0x47));
		if (sync == 0x47) {
			// Flags - 3 bits
			byte flags = in.get();
			// Transport error indicator (TEI)
			int tei = flags & 0x0001;
			// Payload unit start indicator - 1 = start of PES data or PSI, 0 otherwise
			int psi = (flags >> 1) & 0x0001;
			// Transport priority - 1 = higher priority than packets with same PID
			int tp = (flags >> 2) & 0x0001;
			log.trace("Packet info - tei: {} psi: {} tp: {}", new Object[] { tei, psi, tp });
			byte flags2 = in.get();
			// Packet id (PID) - 13 bits
			int pid = ((flags << 8) | (flags2 & 0xff)) & 0x1fff;
			log.trace("Packet id: {}", Integer.toHexString(pid));
			// additional flags
			byte flags3 = in.get();
			int scramblingControl = (flags3 >> 6) & 0x03;
			boolean hasAdaptationField = ((flags3 >> 2) & 0x0001) == 1; //(flags3 & 0x20) == 1;
			boolean hasPayloadData = ((flags3 >> 3) & 0x0001) == 1; //(flags3 & 0x10) == 1;
			// Continuity counter - 4 bits		
			int continuityCount = flags3 & 0x0f;
			log.trace("Packet info - scrambling: 0x{} adaptation: {} payload: {} continuity counter: {}", new Object[] { Integer.toHexString(scramblingControl),
					hasAdaptationField, hasPayloadData, continuityCount });
			// PSI http://en.wikipedia.org/wiki/Program-specific_information
			switch (pid) {
				case 0x0100: //256
					//fairly sure this is an h.264 video packet
					log.trace("Got video");
					//assume they are all key frames for now
					type = Message.Type.VIDEO;
					break;
				case 0x0101: //257
					log.trace("Got audio");
					type = Message.Type.AUDIO;
					break;
				case 0x0000:
					log.trace("Got Program Association Table"); //PAT
					type = Message.Type.CONFIG_PAT;
					processPAT(in);
					break;
				case 0x1000: // 4096:1000 is default for ffmpeg source data
					log.trace("Got Program Map Table"); //PMT
					type = Message.Type.CONFIG_PMT;
					processPMT(in);
					break;
				case 0x0001:
					log.trace("Got Conditional Access Table"); //CAT
					type = Message.Type.CONFIG;
					break;
				case 0x0002:
					log.trace("Got Transport Stream Description Table"); //TSDT
					type = Message.Type.CONFIG;
					break;
				case 0x1fff: //8191
					log.trace("Got Null packet");
					type = Message.Type.NULL;
					break;
				case 0x0010: //16
					log.trace("Got NIT");
					break;
				case 0x0011:
					// service information
					
					break;
				default:
					// if the PMT's PID is not default
					if (pid == pmtPid) {
						// found PMT
						type = Message.Type.CONFIG_PMT;
						processPMT(in);
					} else {
						if (pid >= 0x0003 && pid <= 0x000f) {
							log.trace("Got PID in reserved range");
						} else if (pid >= 0x0010 && pid <= 0x1ffe) {
							log.trace("Got PID from other range");
						} else {
							log.trace("Got unknown PID");
						}
					}
					type = Message.Type.OTHER;
			}
		}
		in.reset();
		return type;
	}

	private void processPAT(ByteBuffer in) {
		log.trace("processPAT: {}", in);
		byte pointer = in.get();
		byte tableId = in.get();
		int sectionLength = readUnsignedShort(in) & 0x03ff; // ignoring misc and reserved bits
		log.trace("PAT pointer: {} table id: {} section length: {}", pointer, tableId, sectionLength);
		int remaining = sectionLength;
		// skip tsid + version/cni + sec# + last sec#
		in.position(in.position() + 5);
		remaining -= 5;
		while (remaining > 4) {
			log.debug("Program number: {}", readUnsignedShort(in)); // program number
			pmtPid = readUnsignedShort(in) & 0x1fff; // 13 bits
			log.debug("PMT pid: {}", pmtPid);
			remaining -= 4;
			//return; // immediately after reading the first pmt ID, if we don't we get the LAST one
		}
		// ignore the CRC (4 bytes)
	}

	private void processPMT(ByteBuffer in) {
		log.trace("processPMT: {}", in);
		byte pointer = in.get();
		byte tableId = in.get();
		if (tableId != 0x02) {
			log.debug("PAT pointed to PMT that isn't PMT");
			return; // don't try to parse it
		}
		int sectionLength = readUnsignedShort(in) & 0x03ff; // ignoring section syntax and reserved
		log.trace("PMT pointer: {} table id: {} section length: {}", pointer, tableId, sectionLength);
		int remaining = sectionLength;
		// skip program num, reserved, version, cni, section num, last section num, reserved, PCR PID
		in.position(in.position() + 7);
		remaining -= 7;
		// program info length
		int piLength = readUnsignedShort(in) & 0x0fff;
		remaining -= 2;
		// prog info
		in.position(in.position() + piLength);
		remaining -= piLength;
		while (remaining > 4) {
			byte type = in.get();
			int pid = readUnsignedShort(in) & 0x1fff;
			int esiLen = readUnsignedShort(in) & 0x0fff;
			remaining -= 5;
			in.position(in.position() + esiLen);
			remaining -= esiLen;
			log.debug("data type 0x{} in PMT", Integer.toHexString(type));
			switch (type) {
				case 0x1b: // H.264 video
					log.debug("Video pid: {}", Integer.toHexString(pid));
					break;
				case 0x0f: // AAC Audio / ADTS
					log.debug("Audio pid: {}", Integer.toHexString(pid));
					break;
				// need to add MP3 Audio  (3 & 4)
				default:
					log.debug("Unsupported type 0x{} in PMT", Integer.toHexString(type));
					break;
			}
		}
		// and ignore CRC
	}

	private static int readUnsignedShort(ByteBuffer in) {
		short val = in.getShort();
		int unsignedShort = val >= 0 ? val : 0x10000 + val;
		return unsignedShort;
	}

	@SuppressWarnings("unused")
	private static boolean isBitSet(byte b, int bit) {
		return (b & (1 << bit)) != 0;
	}

	public boolean isStreamed(String url, int flags) {
		boolean retval = true;
		log.trace("isStreamed({}, {}); {}", new Object[] { url, flags, retval });
		return retval;
	}

	public String toString() {
		return this.getClass().getName() + ':' + url;
	}

}
