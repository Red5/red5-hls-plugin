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

package org.red5.xuggler;

import java.nio.ShortBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import com.xuggle.xuggler.Global;

/**
 * Holder of audio samples.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SampleData implements Comparable<SampleData> {

	private static AtomicInteger index = new AtomicInteger(1);

	private final int id;

	private final ShortBuffer buffer;

	private final long pts;
	
	private SampleData(short[] buffer) {
		this.id = index.getAndIncrement();
		this.buffer = ShortBuffer.wrap(buffer);
		this.pts = Global.NO_PTS;
	}

	private SampleData(short[] buffer, long pts) {
		this.id = index.getAndIncrement();
		this.buffer = ShortBuffer.wrap(buffer);
		this.pts = pts;
	}

	public Short getNextShort() {
		Short element = null;
		if (buffer.remaining() > 0) {
			element = buffer.get();
		}
		return element;
	}
	
	public int getId() {
		return id;
	}

	/**
	 * Returns an array of samples from the current position.
	 * 
	 * @return
	 */
	public short[] getSamples() {
		short[] samples = new short[buffer.limit()];
		int pos = buffer.position();
		buffer.get(samples);
		buffer.position(pos);
		return samples;
	}

	/**
	 * @return the pts
	 */
	public long getPts() {
		return pts;
	}

	public short[] getSamples(int length) {
		short[] samples = new short[length];
		System.arraycopy(buffer, 0, samples, 0, length);
		return samples;
	}

	public short[] getSamples(int length, int offset) {
		short[] samples = new short[length];
		System.arraycopy(buffer, offset, samples, 0, length);
		return samples;
	}

	public static SampleData build(short[] array) {
		return new SampleData(array);
	}

	public static SampleData build(short[] array, long pts) {
		return new SampleData(array, pts);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "SampleData [id=" + id + ", buffer=" + buffer + "]";
	}

	@Override
	public int compareTo(SampleData other) {
		int otherCreated = other.getId();
		if (id > otherCreated) {
			return 1;
		} else if (id < otherCreated) {
			return -1;
		}
		return 0;
	}

}