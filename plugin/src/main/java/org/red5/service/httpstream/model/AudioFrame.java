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

public class AudioFrame implements MediaFrame {

	private ByteBuffer data;
	
	private long timestamp;	
	
	public AudioFrame(byte[] audioData) {
		data = ByteBuffer.wrap(audioData);
	}

	public AudioFrame(ByteBuffer audioData) {
		byte[] buf = new byte[audioData.limit()];
		audioData.get(buf);
		data = ByteBuffer.wrap(buf);
		audioData.clear();
	}	
	
	public boolean isAudio() {
		return true;
	}

	public boolean isVideo() {
		return false;
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
		AudioFrame other = (AudioFrame) obj;
		if (timestamp != other.timestamp)
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "AudioFrame [timestamp=" + timestamp + "]";
	}
	
}
