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

package org.red5.service.httpstream.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

/**
 * Represents an MPEG-TS file segment.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class Segment implements Comparable<Segment> {

	private static Logger log = Red5LoggerFactory.getLogger(Segment.class);

	private final static int CHUNK_SIZE = 188;

	// directory where segment files are written
	private String segmentDirectory = "";

	// name of the segment
	private String name;

	// segment index number
	private int index;

	// creation time
	private long created = System.currentTimeMillis();
	
	// segment duration in seconds
	private double duration;

	// queue for holding data if using memory mapped i/o
	private volatile IoBuffer buffer;

	// lock used when writing or slicing the buffer
	private volatile ReentrantLock lock = new ReentrantLock();

	// physical ts file
	private volatile RandomAccessFile file;

	// allocate a channel to write the file
	private volatile FileChannel channel;

	// whether or not this is the last segment
	private volatile boolean last;

	// whether or not the segment is closed
	private boolean closed;
	
	// number of chunks written to this segment
	private int chunksWritten = 0;

	// whether or not the PAT has been written
	private boolean patWritten;
	
	// whether or not the PMT has been written
	private boolean pmtWritten;
	
	// whether or not the SPS/PPS has been written
	private boolean spWritten;
	
	// holds a threads file channel
	private ThreadLocal<FileChannel> readChannelHolder = new ThreadLocal<FileChannel>() {
		@Override
		protected FileChannel initialValue() {
			String fileName = String.format("%s%s_%s.ts", segmentDirectory, name, index);
			log.debug("initialValue - read channel: {}", fileName);
			try {
				RandomAccessFile fileForReading = new RandomAccessFile(fileName, "r");
				// get the channel
				return fileForReading.getChannel();
			} catch (IOException e) {
				log.warn("Exception in get channel", e);
			}
			return null;
		}
	};

	// holds a threads buffer iterator
	private ThreadLocal<Integer> readPositionHolder = new ThreadLocal<Integer>() {
		@Override
		protected Integer initialValue() {
			log.debug("initialValue - buffer: {}", buffer);
			return 0;
		}
	};

	public Segment(String segmentDirectory, String name, int index, boolean memoryMapped) {
		this.segmentDirectory = segmentDirectory;
		this.name = name;
		this.index = index;
		if (memoryMapped) {
			log.debug("Using memory mapped files");
			//TODO need a good way to guess the initial amount of bytes needed
			buffer = IoBuffer.allocate(CHUNK_SIZE * (1024 * 4), false);
			//direct buffers cannot be auto-expanded
			buffer.setAutoExpand(true);
			buffer.setAutoShrink(true);
		} else {
			log.debug("Using disk based files");
			try {
				file = new RandomAccessFile(String.format("%s%s_%s.ts", segmentDirectory, name, index), "rwd");
				// get the channel
				channel = file.getChannel();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	public String getSegmentDirectory() {
		return segmentDirectory;
	}

	public String getName() {
		return name;
	}

	public int getIndex() {
		return index;
	}

	public long getCreated() {
		return created;
	}

	public boolean isMemoryMapped() {
		return channel == null;
	}

	public boolean isLast() {
		return last;
	}

	public void setLast(boolean last) {
		this.last = last;
	}

	/**
	 * @return the patWritten
	 */
	public boolean isPatWritten() {
		return patWritten;
	}
	
	/**
	 * @param patWritten the patWritten to set
	 */
	public void setPatWritten(boolean patWritten) {
		this.patWritten = patWritten;
	}

	/**
	 * @return the pmtWritten
	 */
	public boolean isPmtWritten() {
		return pmtWritten;
	}

	/**
	 * @param pmtWritten the pmtWritten to set
	 */
	public void setPmtWritten(boolean pmtWritten) {
		this.pmtWritten = pmtWritten;
	}

	/**
	 * @return the spWritten
	 */
	public boolean isSpWritten() {
		return spWritten;
	}

	/**
	 * @param spWritten the spWritten to set
	 */
	public void setSpWritten(boolean spWritten) {
		this.spWritten = spWritten;
	}

	public void setDuration(double duration) {
		this.duration = duration;
	}
	
	public double getDuration() {
		return duration;
	}	
	
	public ByteBuffer read() {
		ByteBuffer buf = null;
		if (buffer != null) {
			Integer readPos = readPositionHolder.get();
			log.trace("Current buffer position: {}", readPos);
			int newPos = readPos + CHUNK_SIZE;
			int currentPosition = buffer.position();
			if (newPos < currentPosition) {
				byte[] chunk = new byte[CHUNK_SIZE];
				if (lock.tryLock()) {
					try {
						currentPosition = buffer.position();
						IoBuffer slice = buffer.getSlice(readPos, CHUNK_SIZE);
						//log.trace("Slice - size: {} {}", slice.limit(), slice.getHexDump());
						buffer.position(currentPosition);
						slice.get(chunk);
						slice.free();
					} finally {
						lock.unlock();
					}
					buf = ByteBuffer.wrap(chunk);
				}
				//set back to thread local
				readPositionHolder.set(newPos);				
			} else {
				//set previous value back in th
				readPositionHolder.set(readPos);
			}
		} else {
			FileChannel readChannel = readChannelHolder.get();
			try {
				//size the buffer for an mpegts chunk
				buf = ByteBuffer.allocate(CHUNK_SIZE);
				//read from the file
				readChannel.read(buf);
				//flip
				buf.flip();
			} catch (IOException e) {
				e.printStackTrace();
			}
			//set back to thread local
			readChannelHolder.set(readChannel);
		}
		return buf;
	}

	public ByteBuffer read(ByteBuffer buf) {
		if (buffer != null) {
			Integer readPos = readPositionHolder.get();
			log.trace("Current buffer read position: {}", readPos);
			int newPos = readPos + CHUNK_SIZE;
			int currentPosition = buffer.position();
			if (newPos < currentPosition) {
				byte[] chunk = new byte[CHUNK_SIZE];
				if (lock.tryLock()) {
					try {
						currentPosition = buffer.position();
						IoBuffer slice = buffer.getSlice(readPos, CHUNK_SIZE);
						//log.trace("Slice - size: {} {}", slice.limit(), slice.getHexDump());
						buffer.position(currentPosition);
						slice.get(chunk);
						slice.free();
					} finally {
						lock.unlock();
					}
					buf.put(chunk);
					buf.flip();
				}
				//set back to thread local
				readPositionHolder.set(newPos);				
			} else {
				//set previous value back in th
				readPositionHolder.set(readPos);
				//set the position to the end as an indicator
				buf.position(CHUNK_SIZE - 1);
			}
		} else {
			FileChannel readChannel = readChannelHolder.get();
			try {
				//read from the file
				readChannel.read(buf);
				buf.flip();
			} catch (IOException e) {
				e.printStackTrace();
			}
			//set back to thread local
			readChannelHolder.set(readChannel);
		}
		return buf;
	}

	public boolean hasMoreData() {
		boolean hasMore = false;
		if (buffer != null) {
			Integer readPos = readPositionHolder.get();
			hasMore = (readPos + CHUNK_SIZE) < buffer.position();
			readPositionHolder.set(readPos);
		} else {
			FileChannel readChannel = readChannelHolder.get();
			try {
				hasMore = (readChannel.size() - readChannel.position()) >= CHUNK_SIZE;
			} catch (IOException e) {
				e.printStackTrace();
			}
			//set back to thread local
			readChannelHolder.set(readChannel);
		}
		return hasMore;
	}

	public void cleanupThreadLocal() {
		if (buffer != null) {
			readPositionHolder.remove();
		} else {
			FileChannel readChannel = readChannelHolder.get();
			try {
				readChannel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			readChannelHolder.remove();
		}
	}

	public int write(ByteBuffer data) {
		log.debug("write");
		int written = 0;
		if (!closed) {
			//if memory mapped, add to the buffer
			if (buffer != null) {
				log.trace("Current buffer size before write: {}", buffer.capacity());
				if (lock.tryLock()) {
					try {
						buffer.put(data);
						chunksWritten++;
						written = data.limit();
					} catch (BufferOverflowException bofe) {
						//not sure how this happens when its set for auto-expand, but it is
						log.warn("Error writing to the buffer", bofe);
					} finally {
						lock.unlock();
					}
				}
			} else {
				if (data != null) {
    				// write to file
    				try {
    					written = channel.write(data);
    					channel.force(true);
    					chunksWritten++;
    				} catch (IOException e) {
    					log.warn("Exception writing channel", e);
    				}
				} else {
					log.debug("Write data was null");
				}
			}
		}
		return written;
	}

	public boolean close() {
		log.debug("Close - name: {} index: {}", name, index);
		closed = true;
		log.debug("Chunks written: {}", chunksWritten);
		boolean result = false;
		if (buffer != null) {
			//write to a file for debugging
//			RandomAccessFile raf = null;
//			if (log.isTraceEnabled()) {
//				try {
//					byte[] chunk = new byte[CHUNK_SIZE];
//					raf = new RandomAccessFile(String.format("%s%s.ts", name, index), "rwd");
//					log.debug("Close - buffer position: {}", buffer.position());
//					if (lock.tryLock()) {
//						IoBuffer slice = null;
//						try {
//							buffer.mark();
//							slice = buffer.getSlice(0, buffer.position());
//							if (buffer.markValue() != -1) {
//								buffer.reset();
//							}
//						} finally {
//							lock.unlock();
//						}
//						log.debug("Close - after slice buffer position: {}", buffer.position());
//						if (slice != null) {
//							while (slice.position() < slice.limit()) {
//								//log.trace("Pos: {} Limit: {}", slice.position(), slice.limit());
//								slice.get(chunk);
//								raf.write(chunk);
//							}
//							slice.free();
//						}
//					}
//				} catch (Exception e) {
//					e.printStackTrace();
//				} finally {
//					if (raf != null) {
//						try {
//							raf.close();
//						} catch (IOException e) {
//						}
//					}
//				}
//			}
			buffer.clear();
			result = true;
		} else if (channel != null && channel.isOpen()) {
			try {
				channel.close();
				result = true;
				// TODO handle 0 length files
				
			} catch (IOException e) {
				log.warn("Exception closing channel", e);
			}
		}
		return result;
	}

	/**
	 * Should be called only when we are completely finished with this segment and no longer
	 * want it to be available.
	 */
	public void dispose() {
		if (buffer != null) {
			buffer.free();
		} else {
			//delete the associated file
			File tsFile = new File(String.format("%s%s_%s.ts", segmentDirectory, name, index));
			if (tsFile.exists()) {
				if (!tsFile.delete()) {
					tsFile.deleteOnExit();
				}
			}
			tsFile = null;
		}
	}
	
	@Override
	public String toString() {
		return "Segment [name=" + name + ", index=" + index + ", created=" + created + "]";
	}

	@Override
	public int compareTo(Segment otherSegment) {
		int otherIndex = otherSegment.getIndex();
		if (index > otherIndex) {
			return 1;
		} else if (index < otherIndex) {
			return -1;
		}
		return 0;
	}

}
