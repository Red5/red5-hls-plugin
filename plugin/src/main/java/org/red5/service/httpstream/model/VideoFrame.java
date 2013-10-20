package org.red5.service.httpstream.model;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2008 by respective authors (see below). All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation; either version 2.1 of the License, or (at your option) any later 
 * version. 
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along 
 * with this library; if not, write to the Free Software Foundation, Inc., 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA 
 */

import java.nio.ByteBuffer;

import org.red5.io.IoConstants;
import org.red5.server.net.rtmp.event.VideoData.FrameType;
import org.red5.server.stream.codec.VideoCodec;

public class VideoFrame implements MediaFrame {

	private ByteBuffer data;

	private int width;

	private int height;

	private int fps;

	private long timestamp;

	private FrameType frameType = FrameType.UNKNOWN;

	public VideoFrame(byte[] videoData) {
		data = ByteBuffer.wrap(videoData);
	}

	public VideoFrame(ByteBuffer videoData) {
		byte[] buf = new byte[videoData.limit()];
		videoData.get(buf);
		data = ByteBuffer.wrap(buf);
		videoData.clear();
	}

	public void init() {
		// determine what type of video data we have
		if (data.limit() > 0) {
			byte flgs = data.get();
			data.rewind();
			detectFrameType();
			if ((flgs & 0x0f) == VideoCodec.H263.getId()) {
				//log.debug("h.263 / Sorenson");
			} else if ((flgs & 0x0f) == VideoCodec.AVC.getId()) {
				//log.debug("h.264 / AVC");
				// keyframe
				if (frameType == FrameType.KEYFRAME) {
					//log.debug("Keyframe");
					byte AVCPacketType = data.get();
					// rewind
					data.rewind();
					if (AVCPacketType == 0) {
						//log.debug("AVC decoder configuration found");
					}
				}
			} else {
				// unsupported video frame type
			}
		}
	}

	/**
	 * Detects the frame type. This may only work on data originating from a Flash source.
	 */
	public void detectFrameType() {
		if (data != null && data.limit() > 0) {
			data.mark();
			int firstByte = (data.get(0)) & 0xff;
			data.reset();
			int frameType = (firstByte & IoConstants.MASK_VIDEO_FRAMETYPE) >> 4;
			if (frameType == IoConstants.FLAG_FRAMETYPE_KEYFRAME) {
				this.frameType = FrameType.KEYFRAME;
			} else if (frameType == IoConstants.FLAG_FRAMETYPE_INTERFRAME) {
				this.frameType = FrameType.INTERFRAME;
			} else if (frameType == IoConstants.FLAG_FRAMETYPE_DISPOSABLE) {
				this.frameType = FrameType.DISPOSABLE_INTERFRAME;
			}
		}
	}

	public boolean isAudio() {
		return false;
	}

	public boolean isVideo() {
		return true;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public ByteBuffer getData() {
		return data.asReadOnlyBuffer();
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public int getFps() {
		return fps;
	}

	public void setFps(int fps) {
		this.fps = fps;
	}

	/**
	 * @return the frameType
	 */
	public FrameType getFrameType() {
		return frameType;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (timestamp ^ (timestamp >>> 32));
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		VideoFrame other = (VideoFrame) obj;
		if (timestamp != other.timestamp)
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "VideoFrame [width=" + width + ", height=" + height + ", fps=" + fps + ", timestamp=" + timestamp + ", frameType=" + frameType + "]";
	}

}
