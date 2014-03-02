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

package org.red5.service.httpstream;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.red5.logging.Red5LoggerFactory;
import org.red5.service.httpstream.model.Segment;
import org.red5.stream.util.AudioMux;
import org.red5.stream.util.BufferUtils;
import org.red5.xuggler.reader.RTMPReader;
import org.red5.xuggler.tool.SampleRateAdjustTool;
import org.red5.xuggler.tool.VideoAdjustTool;
import org.red5.xuggler.writer.HLSStreamWriter;
import org.slf4j.Logger;

import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IAudioSamples.Format;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.ISimpleMediaFile;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.SimpleMediaFile;

/**
 * Common location for segment related objects.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SegmentFacade {

	private static Logger log = Red5LoggerFactory.getLogger(SegmentFacade.class);

	protected final long creationTime;
	
	protected final WeakReference<SegmenterService> segmenterReference;

	protected final String streamName;

	// reads the source stream data
	private RTMPReader reader;

	// writes the output
	private HLSStreamWriter writer;

	// provides audio mux/mix service
	private AudioMux mux;
	
	// queue of segments
	private ConcurrentLinkedQueue<Segment> segments = new ConcurrentLinkedQueue<Segment>();

	// queue for data coming from xuggler
	private ConcurrentLinkedQueue<IQueuedData> dataQueue = new ConcurrentLinkedQueue<IQueuedData>();

	// lock to protect the segment
	private final ReentrantLock lock = new ReentrantLock(true);

	// segment currently being written to
	private volatile Segment segment;

	// segment index counter
	private AtomicInteger counter = new AtomicInteger();

	private AtomicBoolean queueWorkerRunning = new AtomicBoolean(false);

	private Future<?> queueWorkerFuture;

	// length of a segment in milliseconds
	private long segmentTimeLimit;

	// where to write segment files
	private String segmentDirectory;

	// whether to use files or memory for segments
	private boolean memoryMapped;

	// maximum number of segments to keep available per stream
	private int maxSegmentsPerFacade;

	private String outputAudioCodec;

	private String outputVideoCodec;

	private int outputWidth = 352;

	private int outputHeight = 288;

	private double outputFps = 20d;

	private int outputAudioChannels = 2;

	private int outputSampleRate = 44100;
	
	private ICodec audioCodec;

	private ICodec videoCodec;

	public SegmentFacade(SegmenterService segmenter, String streamName) {
		log.debug("Segment facade for: {}", streamName);
		// created at
		creationTime = System.currentTimeMillis();
		// set ref to our parent
		segmenterReference = new WeakReference<SegmenterService>(segmenter);
		this.streamName = streamName;
	}

	/**
	 * Initializes a reader to provide a/v data. If a reader is required, it must be initialized before calling initWriter().
	 */
	public void initReader() {
		log.debug("Initialize reader for {}", streamName);
		reader = new RTMPReader("rtmp://127.0.0.1:1935/hlsapp/" + streamName + " live=1 buffer=1");
		// initialize reader
		reader.init();		
	}
	
	/**
	 * Initializes a writer for HLS segments.
	 */
	public void initWriter() {
		log.debug("Initialize writer for {}", streamName);
		// setup our writer
		writer = new HLSStreamWriter(streamName);
		// create a description of the output
		ISimpleMediaFile outputStreamInfo = new SimpleMediaFile();
		// codecs
		log.debug("Output codecs - audio: {} video: {}", outputAudioCodec, outputVideoCodec);
		audioCodec = ICodec.findEncodingCodecByName(outputAudioCodec);
		if (audioCodec == null || !audioCodec.canEncode()) {
			log.error("Audio encoding not supported for {}", outputAudioCodec);
		}
		videoCodec = ICodec.findEncodingCodecByName(outputVideoCodec);
		if (videoCodec == null || !videoCodec.canEncode()) {
			log.error("Video encoding not supported for {}", outputVideoCodec);
		}
		// audio
		if (audioCodec != null) {
			outputStreamInfo.setHasAudio(true);
			outputStreamInfo.setAudioCodec(audioCodec.getID());
			outputStreamInfo.setAudioChannels(outputAudioChannels);
			outputStreamInfo.setAudioSampleRate(outputSampleRate);
			//outputStreamInfo.setAudioBitRate(audioBitRate);
			outputStreamInfo.setAudioSampleFormat(Format.FMT_S16);
			IRational timeBase = IRational.make(1, outputSampleRate);
			outputStreamInfo.setAudioTimeBase(timeBase);
		} else {
			log.debug("Audio is disabled");
			outputStreamInfo.setHasAudio(false);
		}
		// video
		if (videoCodec != null) {
			outputStreamInfo.setHasVideo(true);
			outputStreamInfo.setVideoCodec(videoCodec.getID());
			outputStreamInfo.setVideoWidth(outputWidth);
			outputStreamInfo.setVideoHeight(outputHeight);
			//outputStreamInfo.setVideoBitRate(videoBitRate);
			outputStreamInfo.setVideoGlobalQuality(0);
		} else {
			log.debug("Video is disabled");
			outputStreamInfo.setHasVideo(false);
		}
		writer.setup(this, outputStreamInfo);
		// open the writer so we can configure the coders
		log.debug("Opening writer");
		writer.open();
		// add streams
		if (videoCodec != null) {
			writer.addVideoStream(0, videoCodec, IRational.make(outputFps * 1.0d), outputWidth, outputHeight);
		}
		if (audioCodec != null) {
			writer.addAudioStream(0, audioCodec, outputAudioChannels, outputSampleRate);
		}
		// open the coders and write the header
		log.debug("Starting writer");
		writer.start();
		// after the writer is started, add adjustments to an existing reader
		if (reader != null) {
			// add audio adjustment tool
			log.trace("Adding audio adjustment");
			SampleRateAdjustTool srat = new SampleRateAdjustTool(outputSampleRate, outputAudioChannels);
			srat.setFacade(this);
			reader.addListener(srat);
			// add video adjustment tool
			log.trace("Adding video adjustment");
			VideoAdjustTool vat = new VideoAdjustTool(outputWidth, outputHeight);
			vat.setFacade(this);
			reader.addListener(vat);
			// start the reader
			segmenterReference.get().submitJob(reader);
		}
		// spawn the queue worker
		log.debug("Spawning and scheduling the queue worker");
		queueWorkerFuture = segmenterReference.get().submitJob(new QueueWorker(), 33L);
	}

	public int getSegmentCount() {
		log.trace("Total segments: {}", segments.size());
		return isComplete() ? segments.size() : (segments.size() > 0 ? segments.size() - 1 : 0);
	}

	public int getActiveSegmentIndex() {
		return segment.getIndex();
	}

	/**
	 * Whether or not this facade is on its last segment.
	 * 
	 * @return
	 */
	public boolean isComplete() {
		return segment != null ? segment.isLast() : false;
	}

	/**
	 * Whether or not this facade is receiving data.
	 * 
	 * @return
	 */
	public boolean isReceivingData() {
		if (dataQueue.isEmpty()) {
			if (reader != null && reader.isClosed()) {
				log.debug("No more data being received, reader is closed");
				return false;
			} else if (mux != null && mux.isFinished()) {
				return false;
			}
		}		
		return true;
	}
	
	/**
	 * Whether or not a timeout from the start of streaming has elapsed. This is used in conjunction with
	 * isReceivingData() to determine if a stream is alive.
	 * 
	 * @return
	 */
	private boolean isTimedOut() {
		return (System.currentTimeMillis() - creationTime) > 120000L; // 2 min timeout
	}
	
	/**
	 * Creates and returns a new segment.
	 * 
	 * @return segment
	 */
	public Segment createSegment() {
		lock.lock();
		// clean up current segment if it exists
		if (segment != null) {
			log.debug("Close segment {}? Duration: {}", segment.getIndex(), segment.getDuration());
			// verify that this is not a "new" segment
			if (segment.getDuration() == 0d) {
				return segment;
			}
			// closing current segment
			segment.close();
		}
		try {
			log.debug("createSegment for {}", streamName);
			// create a segment - default is memory mapped
			segment = new Segment(segmentDirectory, streamName, counter.getAndIncrement(), memoryMapped);
			// add to the map for lookup
			if (segments.add(segment)) {
				log.trace("Segment {} added, total: {}", segment.getIndex(), segments.size());
			}
		} finally {
			lock.unlock();
		}
		// enforce segment list length
		if (segments.size() > maxSegmentsPerFacade) {
			// get current segments index minus max
			int index = segment.getIndex() - maxSegmentsPerFacade;
			for (Segment seg : segments) {
				if (seg.getIndex() <= index) {
					log.trace("Removing segment: {}", seg.getIndex());
					segments.remove(seg);
					// access to the segment is no longer required
					seg.dispose();
				}
			}
		}
		return segment;
	}

	/**
	 * Returns the active segment.
	 * 
	 * @return segment currently being written to
	 */
	public Segment getSegment() {
		boolean acquired = false;
		try {
			acquired = lock.tryLock(1, TimeUnit.SECONDS);
			if (acquired) {
				log.debug("getSegment for {} current segment: {}", streamName, segment);
				return segment;
			} else {
				log.trace("Lock was not acquired within the timeout");
			}
		} catch (Exception e) {
			log.warn("Exception trying lock", e);
		} finally {
			if (acquired) {
				lock.unlock();
			}
		}
		return null;
	}

	/**
	 * Returns a segment matching the requested index.
	 * 
	 * @return segment matching the index or null
	 */
	public Segment getSegment(int index) {
		Segment result = null;
		if (index < counter.get()) {
			for (Segment seg : segments) {
				if (seg.getIndex() == index) {
					result = seg;
					break;
				}
			}
		} else {
			log.warn("No segment available");
		}
		return result;
	}

	public Segment[] getSegments() {
		// make room for all but the last / current segment
		Segment[] segs = new Segment[getSegmentCount()];
		log.debug("Segments to return: {}", segs.length);
		if (segs.length > 0) {
			int s = 0;
			for (Segment seg : segments) {
				int idx = seg.getIndex();
				log.debug("Segment index: {}", idx);
				try {
					segs[s++] = seg;
				} catch (ArrayIndexOutOfBoundsException aiob) {
					// this happens when we have an active segment
				}
			}
		} else {
			log.warn("Not enough segments available");
		}
		return segs;
	}

	public Segment popSegment() {
		return segments.poll();
	}

	/**
	 * Queue the audio data from non-xuggler source.
	 * 
	 * @param samples audio data to queue
	 * @param timeStamp 
	 * @param timeUnit
	 */
	public void queueAudio(short[] samples, long timeStamp, TimeUnit timeUnit) {
		log.trace("Queue audio");
		dataQueue.add(new QueuedAudioData(samples, timeStamp, timeUnit));
	}

	/**
	 * Queue the audio data from xuggler.
	 * 
	 * @param samples audio data to queue
	 * @param timeStamp 
	 * @param timeUnit
	 */
	public void queueAudio(IAudioSamples samples, long timeStamp, TimeUnit timeUnit) {
		log.trace("Queue audio");
		// convert from AudioSamples to short array
		ByteBuffer buf = samples.getByteBuffer();
		byte[] decoded = new byte[buf.limit()];
		buf.get(decoded);
		buf.flip();
		short[] isamples = BufferUtils.byteToShortArray(decoded, 0, decoded.length, true);
		// queue them up for writing
		dataQueue.add(new QueuedAudioData(isamples, timeStamp, timeUnit));
		// make a copy for group mux if one exists
		if (mux != null) {
			mux.pushData(streamName, isamples);
		}
	}

	/**
	 * Queue the video data from xuggler.
	 * 
	 * @param pic video picture to queue
	 * @param timeStamp 
	 * @param timeUnit
	 */
	public void queueVideo(IVideoPicture pic, long timeStamp, TimeUnit timeUnit) {
		log.trace("Queue video");
		dataQueue.add(new QueuedVideoData(pic, timeStamp, timeUnit));
	}

	/**
	 * @param outputAudioCodec the outputAudioCodec to set
	 */
	public void setOutputAudioCodec(String outputAudioCodec) {
		this.outputAudioCodec = outputAudioCodec;
	}

	/**
	 * @param outputVideoCodec the outputVideoCodec to set
	 */
	public void setOutputVideoCodec(String outputVideoCodec) {
		this.outputVideoCodec = outputVideoCodec;
	}

	/**
	 * @return the segmentTimeLimit
	 */
	public long getSegmentTimeLimit() {
		return segmentTimeLimit;
	}

	/**
	 * @param segmentTimeLimit the segmentTimeLimit to set
	 */
	public void setSegmentTimeLimit(long segmentTimeLimit) {
		this.segmentTimeLimit = segmentTimeLimit;
	}

	/**
	 * @return the segmentDirectory
	 */
	public String getSegmentDirectory() {
		return segmentDirectory;
	}

	/**
	 * @param segmentDirectory the segmentDirectory to set
	 */
	public void setSegmentDirectory(String segmentDirectory) {
		this.segmentDirectory = segmentDirectory;
	}

	/**
	 * @return the memoryMapped
	 */
	public boolean isMemoryMapped() {
		return memoryMapped;
	}

	/**
	 * @param memoryMapped the memoryMapped to set
	 */
	public void setMemoryMapped(boolean memoryMapped) {
		this.memoryMapped = memoryMapped;
	}

	/**
	 * @return the maxSegmentsPerFacade
	 */
	public int getMaxSegmentsPerFacade() {
		return maxSegmentsPerFacade;
	}

	/**
	 * @param maxSegmentsPerFacade the maxSegmentsPerFacade to set
	 */
	public void setMaxSegmentsPerFacade(int maxSegmentsPerFacade) {
		this.maxSegmentsPerFacade = maxSegmentsPerFacade;
	}
	
	public void setAudioMux(AudioMux mux) {
		this.mux = mux;
	}	

	@Override
	public String toString() {
		return streamName;
	}

	/**
	 * Interface for queued data originated from Xuggler
	 */
	interface IQueuedData {
		long getTimeStamp();

		TimeUnit getTimeUnit();
	}

	/**
	 * Queued audio data originated from Xuggler
	 */
	private final class QueuedAudioData implements IQueuedData {

		final short[] samples;

		final long timeStamp;

		final TimeUnit timeUnit;

		@SuppressWarnings("unused")
		QueuedAudioData(IAudioSamples isamples, long timeStamp, TimeUnit timeUnit) {
			ByteBuffer buf = isamples.getByteBuffer();
			byte[] decoded = new byte[buf.limit()];
			buf.get(decoded);
			buf.flip();
			this.samples = BufferUtils.byteToShortArray(decoded, 0, decoded.length, true);
			this.timeStamp = timeStamp;
			this.timeUnit = timeUnit;
		}

		QueuedAudioData(short[] isamples, long timeStamp, TimeUnit timeUnit) {
			this.samples = new short[isamples.length];
			System.arraycopy(isamples, 0, samples, 0, isamples.length);
			this.timeStamp = timeStamp;
			this.timeUnit = timeUnit;
		}

		/**
		 * @return the samples
		 */
		public short[] getSamples() {
			return samples;
		}

		/**
		 * @return the timeUnit
		 */
		public TimeUnit getTimeUnit() {
			return timeUnit;
		}

		public long getTimeStamp() {
			return timeStamp;
		}

	}

	/**
	 * Queued video data originated from Xuggler
	 */
	private final class QueuedVideoData implements IQueuedData {

		final byte[] picture;

		final int width;

		final int height;

		final long timeStamp;

		final TimeUnit timeUnit;

		QueuedVideoData(IVideoPicture pic, long timeStamp, TimeUnit timeUnit) {
			ByteBuffer buf = pic.getByteBuffer();
			picture = new byte[buf.limit()];
			buf.get(picture);
			buf.flip();
			//this.type = pic.getPixelType();
			this.width = pic.getWidth();
			this.height = pic.getHeight();
			this.timeStamp = timeStamp;
			this.timeUnit = timeUnit;
		}

		/**
		 * @return the picture
		 */
		public IVideoPicture getVideoPicture() {
			IVideoPicture pic = IVideoPicture.make(IPixelFormat.Type.YUV420P, width, height);
			pic.put(picture, 0, 0, picture.length);
			pic.setComplete(true, IPixelFormat.Type.YUV420P, width, height, timeStamp);
			return pic;
		}

		/**
		 * @return the timeUnit
		 */
		public TimeUnit getTimeUnit() {
			return timeUnit;
		}

		public long getTimeStamp() {
			return timeStamp;
		}

	}

	/**
	 * Routes the queued Xuggler derived data to the segments.
	 */
	private final class QueueWorker implements Runnable {

		public void run() {
			// ensure the job is not already running
			if (queueWorkerRunning.compareAndSet(false, true)) {
				log.trace("QueueWorker - run");
				try {
					if (!dataQueue.isEmpty()) {
						IQueuedData q = null;
						while ((q = dataQueue.poll()) != null) {
							if (q instanceof QueuedAudioData && audioCodec != null) {
								// send audio to the hls writer
								writer.encodeAudio(((QueuedAudioData) q).getSamples(), q.getTimeStamp(), q.getTimeUnit());
							} else if (q instanceof QueuedVideoData && videoCodec != null) {
								// send video to the hls writer
								writer.encodeVideo(((QueuedVideoData) q).getVideoPicture(), q.getTimeStamp(), q.getTimeUnit());
							}
						}
					} else {
						log.trace("Queue is empty");
					}
				} catch (Exception e) {
					log.warn("Exception handling queue", e);
				} finally {
					// check if we are no longer getting data
					if (!isReceivingData() && isTimedOut()) {
						log.debug("Cancelling queue worker, no more data being received");
						queueWorkerFuture.cancel(true);
						writer.close();
						if (mux != null) {
							// remove the streams audio track from the muxer
							mux.removeTrack(streamName);
						}
					}
					queueWorkerRunning.compareAndSet(true, false);
				}
				log.trace("QueueWorker - end");
			} else {
				log.trace("QueueWorker - already running");
			}
		}

	}

}
