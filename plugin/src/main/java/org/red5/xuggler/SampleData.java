package org.red5.xuggler;

import java.nio.ShortBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import com.xuggle.xuggler.Global;

/**
 * Holder of audio samples.
 * 
 * @author Paul
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