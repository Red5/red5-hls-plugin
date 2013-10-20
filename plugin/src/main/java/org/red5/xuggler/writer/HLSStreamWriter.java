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

package org.red5.xuggler.writer;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.red5.service.httpstream.SegmentFacade;
import org.red5.service.httpstream.SegmenterService;
import org.red5.service.httpstream.model.Segment;
import org.red5.stream.http.xuggler.MpegTsHandlerFactory;
import org.red5.stream.http.xuggler.MpegTsIoHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuggle.xuggler.Configuration;
import com.xuggle.xuggler.Global;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IContainerFormat;
import com.xuggle.xuggler.IError;
import com.xuggle.xuggler.IMetaData;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.ISimpleMediaFile;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;

/**
 * An writer that encodes and decodes media to containers. Based on MediaWriter class from Xuggler.
 * 
 * <table border="1">
 * <tr><td>AAC-LC</td><td>"mp4a.40.2"</td></tr>
 * <tr><td>HE-AAC</td><td>"mp4a.40.5"</td></tr>
 * <tr><td>MP3</td><td>"mp4a.40.34"</td></tr>
 * <tr><td>H.264 Baseline Profile level 3.0</td><td>"avc1.42001e" or avc1.66.30<br />
 * Note: Use avc1.66.30 for compatibility with iOS versions 3.0 to 3.12.</td></tr>
 * <tr><td>H.264 Baseline Profile level 3.1</td><td>"avc1.42001f"</td></tr>
 * <tr><td>H.264 Main Profile level 3.0</td><td>"avc1.4d001e" or avc1.77.30<br />
 * Note: Use avc1.77.30 for compatibility with iOS versions 3.0 to 3.12.</td></tr>
 * <tr><td>H.264 Main Profile level 3.1</td><td>"avc1.4d001f"</td></tr>
 * </table>
 * 
 * #EXT-X-STREAM-INF:PROGRAM-ID=1, BANDWIDTH=3000000, CODECS="avc1.4d001e,mp4a.40.5"
 * 
 * http://developer.apple.com/library/ios/#documentation/networkinginternet/conceptual/streamingmediaguide/FrequentlyAskedQuestions/FrequentlyAskedQuestions.html
 * 
 * Segment handling
 * http://www.ffmpeg.org/ffmpeg-formats.html#toc-mpegts
 * http://www.ffmpeg.org/ffmpeg-formats.html#toc-segment_002c-stream_005fsegment_002c-ssegment
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Gavriloaie Eugen-Andrei(crtmpserver@gmail.com)
 * @author Andy Shaules (bowljoman@gmail.com)
 */
public class HLSStreamWriter implements IStreamWriter {

	private static final Logger log = LoggerFactory.getLogger(HLSStreamWriter.class);

	static {
		com.xuggle.ferry.JNIMemoryManager.setMemoryModel(com.xuggle.ferry.JNIMemoryManager.MemoryModel.NATIVE_BUFFERS);
	}

	/** The default time base. */
	private static final IRational DEFAULT_TIMEBASE = IRational.make(1, (int) Global.DEFAULT_PTS_PER_SECOND);

	private SegmentFacade facade;

	private final String outputUrl;

	// the container
	private IContainer container;

	// the container format
	private IContainerFormat containerFormat;

	private IStream audioStream;

	private IStream videoStream;

	private IStreamCoder audioCoder;

	private IStreamCoder videoCoder;

	private ISimpleMediaFile outputStreamInfo;

	// true if the writer should ask FFMPEG to interleave media
	private boolean forceInterleave = false;

	private boolean audioComplete = false;

	private boolean videoComplete = false;

	private volatile double audioDuration;

	private volatile double videoDuration;

	private int videoBitRate = 360000;

	private long prevAudioTime = 0L;

	private long prevVideoTime = 0L;

	/**
	 * Create a MediaWriter which will require subsequent calls to {@link #addVideoStream} and/or {@link #addAudioStream} to configure the
	 * writer.  Streams may be added or further configured as needed until the first attempt to write data.
	 *
	 * @param streamName the stream name of the source
	 */
	public HLSStreamWriter(String streamName) {
		outputUrl = MpegTsHandlerFactory.DEFAULT_PROTOCOL + ':' + streamName;
	}

	public void setup(final SegmentFacade facade, ISimpleMediaFile outputStreamInfo) {
		log.debug("setup {}", outputUrl);
		this.outputStreamInfo = outputStreamInfo;
		this.facade = facade;
		// output to a custom handler
		outputStreamInfo.setURL(outputUrl);
		// setup our mpeg-ts io handler
		MpegTsIoHandler outputHandler = new MpegTsIoHandler(outputUrl, facade);
		MpegTsHandlerFactory.getFactory().registerStream(outputHandler, outputStreamInfo);
		// create a container
		container = IContainer.make();
		log.trace("Container buffer length: {}", container.getInputBufferLength());
		// create format 
		containerFormat = IContainerFormat.make();
		containerFormat.setOutputFormat("mpegts", outputUrl, null);
	}

	/** 
	 * Add a audio stream.  The time base defaults to {@link #DEFAULT_TIMEBASE} and the audio format defaults to {@link
	 * #DEFAULT_SAMPLE_FORMAT}.  The new {@link IStream} is returned to provide an easy way to further configure the stream.
	 * 
	 * @param streamId a format-dependent id for this stream
	 * @param codec the codec to used to encode data, to establish the codec see {@link com.xuggle.xuggler.ICodec}
	 * @param channelCount the number of audio channels for the stream
	 * @param sampleRate sample rate in Hz (samples per seconds), common values are 44100, 22050, 11025, etc.
	 *
	 * @return audio index
	 *
	 * @throws IllegalArgumentException if inputIndex < 0, the stream id < 0, the codec is NULL or if the container is already open.
	 * @throws IllegalArgumentException if width or height are <= 0
	 * 
	 * @see IContainer
	 * @see IStream
	 * @see IStreamCoder
	 * @see ICodec
	 */
	@SuppressWarnings("deprecation")
	public int addAudioStream(int streamId, ICodec codec, int channelCount, int sampleRate) {
		log.debug("addAudioStream {}", outputUrl);
		// validate parameters
		if (channelCount <= 0) {
			throw new IllegalArgumentException("Invalid channel count " + channelCount);
		}
		if (sampleRate <= 0) {
			throw new IllegalArgumentException("Invalid sample rate " + sampleRate);
		}
		// add the new stream at the correct index
		audioStream = container.addNewStream(streamId);
		if (audioStream == null) {
			throw new RuntimeException("Unable to create stream id " + streamId + ", codec " + codec);
		}
		// configure the stream coder
		audioCoder = audioStream.getStreamCoder();
		audioCoder.setStandardsCompliance(IStreamCoder.CodecStandardsCompliance.COMPLIANCE_EXPERIMENTAL);
		audioCoder.setCodec(codec);
		audioCoder.setTimeBase(IRational.make(1, sampleRate));
		audioCoder.setChannels(channelCount);
		audioCoder.setSampleRate(sampleRate);
		audioCoder.setSampleFormat(IAudioSamples.Format.FMT_S16);
		switch (sampleRate) {
			case 44100:
				if (channelCount == 2) {
					audioCoder.setBitRate(128000);
				} else {
					audioCoder.setBitRate(64000);
				}
				break;
			case 22050:
				if (channelCount == 2) {
					audioCoder.setBitRate(96000);
				} else {
					audioCoder.setBitRate(48000);
				}
				break;
			default:
				audioCoder.setBitRate(32000);
				break;
		}
		audioCoder.setBitRateTolerance((int) (audioCoder.getBitRate() / 2));
		audioCoder.setGlobalQuality(0);
		log.trace("Bitrate: {} tolerance: {}", audioCoder.getBitRate(), audioCoder.getBitRateTolerance());
		log.trace("Time base: {} sample rate: {} stereo: {}", audioCoder.getTimeBase(), sampleRate, channelCount > 1);
		log.debug("Added:\n{}", audioStream);
		// return the new audio stream
		return audioStream.getIndex();
	}

	/** 
	 * Add a video stream.  The time base defaults to {@link #DEFAULT_TIMEBASE} and the pixel format defaults to {@link
	 * #DEFAULT_PIXEL_TYPE}.  The new {@link IStream} is returned to provide an easy way to further configure the stream.
	 * 
	 * @param inputIndex the index that will be passed to {@link #onVideoPicture} for this stream
	 * @param streamId a format-dependent id for this stream
	 * @param codec the codec to used to encode data, to establish the codec see {@link com.xuggle.xuggler.ICodec}
	 * @param width width of video frames
	 * @param height height of video frames
	 *
	 * @return video index
	 *
	 * @throws IllegalArgumentException if inputIndex < 0, the stream id < 0, the codec is NULL or if the container is already open.
	 * @throws IllegalArgumentException if width or height are <= 0
	 * 
	 * @see IContainer
	 * @see IStream
	 * @see IStreamCoder
	 * @see ICodec
	 */
	@SuppressWarnings("deprecation")
	public int addVideoStream(int streamId, ICodec codec, IRational frameRate, int width, int height) {
		log.debug("addVideoStream {}", outputUrl);
		// validate parameters
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Invalid video frame size [" + width + " x " + height + "]");
		}
		// add the new stream at the correct index
		videoStream = container.addNewStream(streamId);
		if (videoStream == null) {
			throw new RuntimeException("Unable to create stream id " + streamId + ", codec " + codec);
		}
		// configure the stream coder
		videoCoder = videoStream.getStreamCoder();
		videoCoder.setStandardsCompliance(IStreamCoder.CodecStandardsCompliance.COMPLIANCE_EXPERIMENTAL);
		videoCoder.setCodec(codec);
		IRational timeBase = IRational.make(frameRate.getDenominator(), frameRate.getNumerator());
		videoCoder.setTimeBase(timeBase);
		timeBase.delete();
		timeBase = null;
		videoCoder.setWidth(width);
		videoCoder.setHeight(height);
		videoCoder.setPixelType(IPixelFormat.Type.YUV420P);
		if (videoCoder.getCodecID().equals(ICodec.ID.CODEC_ID_H264)) {
			log.debug("H.264 codec detected, attempting configure with preset file");
			videoCoder.setFlag(IStreamCoder.Flags.FLAG_QSCALE, false);
			try {
				InputStream in = SegmenterService.class.getResourceAsStream("mpegts-ipod320.properties");
				Properties props = new Properties();
				props.load(in);
				int retval = Configuration.configure(props, videoCoder);
				if (retval < 0) {
					throw new RuntimeException("Could not configure coder from preset file");
				}
				videoCoder.setProperty("nr", 0);
				videoCoder.setProperty("mbd", 0);
				// g / gop should be less than a segment so at least one key frame is in a segment
				int gops = (int) (frameRate.getValue() / (facade.getSegmentTimeLimit() / 1000)); // (fps / segment length) == gops
				videoCoder.setProperty("g", gops);
				videoCoder.setNumPicturesInGroupOfPictures(gops);
				// previously used with mpeg-ts
				videoCoder.setProperty("level", 30);
				videoCoder.setProperty("async", 2);
			} catch (IOException e) {
				log.warn("Exception attempting to configure", e);
			}
		} else if (videoCoder.getCodecID().equals(ICodec.ID.CODEC_ID_THEORA)) {
			log.debug("Theora codec detected, attempting configure with presets");
			videoCoder.setFlag(IStreamCoder.Flags.FLAG_QSCALE, false);
			try {
				InputStream in = SegmenterService.class.getResourceAsStream("libtheora-default.ffpreset");
				Properties props = new Properties();
				props.load(in);
				int retval = Configuration.configure(props, videoCoder);
				if (retval < 0) {
					throw new RuntimeException("Could not configure coder from preset file");
				}
				videoCoder.setProperty("qscale", 6);
				videoCoder.setProperty("sharpness", 0);
				videoCoder.setProperty("mbd", 2);
			} catch (IOException e) {
				log.warn("Exception attempting to configure", e);
			}
		}
		videoCoder.setBitRate(videoBitRate);
		videoCoder.setBitRateTolerance((int) (videoCoder.getBitRate() / 2));
		videoCoder.setGlobalQuality(0);
		//videoCoder.setFlag(IStreamCoder.Flags.FLAG_QSCALE, true);
		log.trace("Bitrate: {} tolerance: {}", videoCoder.getBitRate(), videoCoder.getBitRateTolerance());
		log.trace("Time base: {} frame rate: {}", videoCoder.getTimeBase(), frameRate.getValue());
		log.trace("GOP: {}", videoCoder.getNumPicturesInGroupOfPictures());
		log.debug("Added:\n{}", videoStream);
		// return the new video stream
		return videoStream.getIndex();
	}

	private long audioTs;

	public void encodeAudio(short[] samples, long timeStamp, TimeUnit timeUnit) {
		log.debug("encodeAudio {}", outputUrl);
		// verify parameters
		if (null == samples) {
			throw new IllegalArgumentException("NULL input samples");
		}
		if (IAudioSamples.Format.FMT_S16 != audioCoder.getSampleFormat()) {
			throw new IllegalArgumentException("stream is not 16 bit audio");
		}
		// establish the number of samples
		long sampleCount = samples.length / audioCoder.getChannels();
		// XXX dont encode until we have 2048 total samples (2048 mono / 4096 stereo)
		audioTs += samples.length;
		log.trace("Audio pts using samples: {} {}", audioTs, (audioCoder.getChannels() * 2));
		// create the audio samples object and extract the internal buffer as an array
		IAudioSamples audioFrame = IAudioSamples.make(sampleCount, audioCoder.getChannels());
		// We allow people to pass in a null timeUnit for audio as a signal that time stamps are unknown.  This is a common
		// case for audio data, and Xuggler should handle it if we set a invalid time stamp on the audio.
		final long timeStampMicro;
		if (timeUnit == null) {
			timeStampMicro = Global.NO_PTS;
		} else {
			timeStampMicro = MICROSECONDS.convert(timeStamp, timeUnit);
		}
		// put the samples into the frame
		audioFrame.put(samples, 0, 0, samples.length);
		// set complete
		audioFrame.setComplete(true, sampleCount, audioCoder.getSampleRate(), audioCoder.getChannels(), audioCoder.getSampleFormat(), audioTs / (audioCoder.getChannels() * 2));
		for (int consumed = 0; consumed < audioFrame.getNumSamples();) {
			// convert the samples into a packet
			IPacket audioPacket = IPacket.make();
			// encode
			int result = audioCoder.encodeAudio(audioPacket, audioFrame, consumed);
			//System.out.printf("Flags a: %08x\n", audioCoder.getFlags());
			if (result < 0) {
				log.error("Failed to encode audio: {} samples: {}", getErrorMessage(result), audioFrame);
				audioPacket.delete();
				break;
			}
			consumed += result;
			audioComplete = audioPacket.isComplete();
			if (audioComplete) {
				log.trace("Audio timestamp {} us sample time: {}", timeStampMicro, (audioTs / 4) / 44.100);
				// write the packet
				writePacket(audioPacket);
				// add the duration of our audio
				double dur = (timeStampMicro + audioPacket.getDuration() - prevAudioTime) / 1000000d;
				audioDuration += dur;
				log.trace("Duration - audio: {}", dur);
				prevAudioTime = timeStampMicro;
				audioPacket.delete();
			} else {
				log.warn("Audio packet was not complete");
			}
		}
		audioFrame.delete();

		/**
		// convert the samples into a packet
		IPacket audioPacket = IPacket.make();
		for (int consumed = 0; consumed < audioFrame.getNumSamples(); ) {
			while ((consumed < audioFrame.getNumSamples()) && (!audioPacket.isComplete())) {
				int result = audioCoder.encodeAudio(audioPacket, audioFrame, consumed);
				//System.out.printf("Flags a: %08x\n", audioCoder.getFlags());
				if (result < 0) {
					log.error("Failed to encode audio: {} samples: {}", getErrorMessage(result), audioFrame);
					audioPacket.delete();
					return;
				}
				consumed += result;
				audioComplete = audioPacket.isComplete();
			}
			if (audioComplete) {				
				log.trace("Audio timestamp {} us", timeStampMicro);
				// write the packet
				writePacket(audioPacket);
				// add the duration of our audio
				double dur = (timeStampMicro + audioPacket.getDuration() - prevAudioTime) / 1000000d;
				audioDuration += dur;
				log.trace("Duration - audio: {}", dur);
				prevAudioTime = timeStampMicro;
				audioPacket.delete();
			} else {
				log.warn("Audio packet was not complete");
			}
		}
		*/
	}

	public void encodeVideo(IVideoPicture picture) {
		encodeVideo(picture, 0L, null);
	}

	public void encodeVideo(IVideoPicture picture, long timeStamp, TimeUnit timeUnit) {
		log.debug("encodeVideo {}", outputUrl);
		// establish the stream, return silently if no stream returned
		if (null != picture) {
			IPacket videoPacket = IPacket.make();
			// encode video picture
			int result = videoCoder.encodeVideo(videoPacket, picture, 0);
			//System.out.printf("Flags v: %08x\n", videoCoder.getFlags());
			if (result < 0) {
				log.error("{} Failed to encode video: {} picture: {}", new Object[] { result, getErrorMessage(result), picture });
				videoPacket.delete();
				return;
			}
			videoComplete = videoPacket.isComplete();
			if (videoComplete) {
				final long timeStampMicro;
				if (timeUnit == null) {
					timeStampMicro = Global.NO_PTS;
				} else {
					timeStampMicro = MICROSECONDS.convert(timeStamp, timeUnit);
				}
				log.trace("Video timestamp {} us", timeStampMicro);
				// write packet
				writePacket(videoPacket);
				// add the duration of our video
				double dur = (timeStampMicro + videoPacket.getDuration() - prevVideoTime) / 1000000d;
				videoDuration += dur;
				log.trace("Duration - video: {}", dur);
				//double videoPts = (double) videoPacket.getDuration() * videoCoder.getTimeBase().getNumerator() / videoCoder.getTimeBase().getDenominator();
				//log.trace("Video pts - calculated: {} reported: {}", videoPts, videoPacket.getPts());
				prevVideoTime = timeStampMicro;
				videoPacket.delete();
			} else {
				log.warn("Video packet was not complete");
			}
		} else {
			throw new IllegalArgumentException("No picture");
		}
	}

	/**
	 * Write packet to the output container
	 * 
	 * @param packet the packet to write out
	 */
	private void writePacket(IPacket packet) {
		log.trace("write packet - duration: {} timestamp: {}", packet.getDuration(), packet.getTimeStamp());
		if (createNewSegment()) {
			log.trace("New segment created: {}", facade.getActiveSegmentIndex());
		}
		if (container.writePacket(packet, forceInterleave) < 0) {
			log.warn("Failed to write packet: {} force interleave: {}", packet, forceInterleave);
		}
		container.flushPackets();
		//packet.delete();
	}

	public void open() {
		log.debug("open {}", outputUrl);
		// create metadata
		IMetaData meta = IMetaData.make();
		meta.setValue("service_provider", "Red5 HLS");
		meta.setValue("title", outputUrl.substring(outputUrl.indexOf(':') + 1));
		meta.setValue("map", "0");
		meta.setValue("segment_time", "" + facade.getSegmentTimeLimit() / 1000);
		meta.setValue("segment_format", "mpegts");
		//meta.setValue("reset_timestamps", "0"); // 1 or 0
		IMetaData metaFail = IMetaData.make();
		// open the container
		if (container.open(outputUrl, IContainer.Type.WRITE, containerFormat, true, false, meta, metaFail) < 0) {
			throw new IllegalArgumentException("Could not open: " + outputUrl);
		} else {
			if (log.isTraceEnabled()) {
				if (metaFail.getNumKeys() > 0) {
					Collection<String> keys = metaFail.getKeys();
					for (String key : keys) {
						log.trace("Failed to set {}", key);
					}
				}
			}
			//			Properties props = new Properties();			
			//			props.setProperty("map", "0");
			//			//props.setProperty("segment_list type", "m3u8");
			//			//props.setProperty("segment_list", "E:\\dev\\server\\red5-server-1.0\\playlist.m3u8");
			//			//props.setProperty("segment_list_flags", "+live");
			//			props.setProperty("segment_time", "4");
			//			props.setProperty("segment_format", "mpegts");
			//			//props.setProperty("reset_timestamps", "0"); // 1 or 0
			//			int rv = Configuration.configure(props, container);
			//			if (rv < 0) {
			//				throw new RuntimeException("Could not configure the container for " + outputUrl + " " + getErrorMessage(rv));	
			//			}			
		}
	}

	@SuppressWarnings("deprecation")
	public void start() {
		log.debug("start {}", outputUrl);
		// open coders
		int rv = -1;
		if (outputStreamInfo.hasAudio()) {
			rv = audioCoder.open();
			if (rv < 0) {
				throw new RuntimeException("Could not open stream " + audioStream + ": " + getErrorMessage(rv));
			}
			log.debug("Audio coder opened");
		}
		if (outputStreamInfo.hasVideo()) {
			rv = videoCoder.open();
			if (rv < 0) {
				throw new RuntimeException("Could not open stream " + videoStream + ": " + getErrorMessage(rv));
			}
			log.debug("Video coder opened");
		}
		// write the header
		rv = container.writeHeader();
		if (rv >= 0) {
			log.debug("Wrote header {}", outputUrl);
		} else {
			throw new RuntimeException("Error " + IError.make(rv) + ", failed to write header to container " + container);
		}
	}

	/** 
	 * Flush any remaining media data in the media coders.
	 */
	public void flush() {
		log.debug("flush {}", outputUrl);
		// flush coders
		if (outputStreamInfo.hasAudio()) {
			if (audioCoder.isOpen()) {
				IPacket packet = IPacket.make();
				while (!packet.isComplete()) {
					if (audioCoder.encodeAudio(packet, null, 0) < 0) {
						break;
					}
				}
				packet.delete();
			}
		}
		// flush video coder
		if (outputStreamInfo.hasVideo()) {
			if (videoCoder.isOpen()) {
				log.debug("Dropped frames: {} predicted pts: {}", videoCoder.getNumDroppedFrames(), videoCoder.getNextPredictedPts());
				IPacket packet = IPacket.make();
				while (!packet.isComplete()) {
					if (videoCoder.encodeVideo(packet, null, 0) < 0) {
						break;
					}
				}
				packet.delete();
			}
		}
		// flush the container
		container.flushPackets();
	}

	/** {@inheritDoc} */
	public void close() {
		log.debug("close {}", outputUrl);
		MpegTsHandlerFactory.getFactory().deleteStream(outputUrl);
		int rv;
		// flush coders
		flush();
		// write the trailer on the output container
		if ((rv = container.writeTrailer()) < 0) {
			log.error("Error {}, failed to write trailer to {}", IError.make(rv), outputUrl);
		}
		// close the coders opened by this MediaWriter
		if (outputStreamInfo.hasVideo()) {
			try {
				if ((rv = videoCoder.close()) < 0) {
					log.error("Error {}, failed close coder {}", getErrorMessage(rv), videoCoder);
				}
			} finally {
				videoCoder.delete();
			}
		}
		if (outputStreamInfo.hasAudio()) {
			try {
				if ((rv = audioCoder.close()) < 0) {
					log.error("Error {}, failed close coder {}", getErrorMessage(rv), audioCoder);
				}
			} finally {
				audioCoder.delete();
			}
		}
		// if we're supposed to, close the container
		if ((rv = container.close()) < 0) {
			throw new RuntimeException("error " + IError.make(rv) + ", failed close IContainer " + container + " for " + outputUrl);
		}
		// get the current segment, if one exists
		Segment segment = facade.getSegment();
		// mark it as "last" and close
		if (segment != null && !segment.isLast()) {
			// mark it as the last
			segment.setLast(true);
			segment.close();
		}
	}

	/**
	 * Decides whether or not to create a new segment based on the current duration of audio or video.
	 * 
	 * @return true if a new segment is created and false otherwise
	 */
	private boolean createNewSegment() {
		// get the current segment, if one exists
		Segment segment = facade.getSegment();
		if (segment != null) {
			log.trace("Segment returned, check durations");
			// convert segment limit to seconds
			double limit = facade.getSegmentTimeLimit() / 1000d;
			log.debug("Segment limit: {} audio: {} video: {}", limit, audioDuration, videoDuration);
			if (audioDuration > limit || videoDuration > limit) {
				log.trace("Duration matched, create new segment");
				// use the greatest of the two durations
				segment.setDuration(Math.max(audioDuration, videoDuration));
				// reset
				audioDuration -= audioDuration;
				videoDuration -= videoDuration;
				// create new segment
				facade.createSegment();
				return true;
			}
		} else {
			log.trace("No segment returned, create first segment");
			// first segment
			facade.createSegment();
			return true;
		}
		return false;
	}

	/**
	 * Get the default time base we'll use on our encoders if one is not specified by the codec.
	 * @return the default time base
	 */
	public IRational getDefaultTimebase() {
		return DEFAULT_TIMEBASE.copyReference();
	}

	/** {@inheritDoc} */
	public String toString() {
		return "HLSStreamWriter[" + outputUrl + "]";
	}

	private static String getErrorMessage(int rv) {
		String errorString = "";
		IError error = IError.make(rv);
		if (error != null) {
			errorString = error.toString();
			error.delete();
		}
		return errorString;
	}

}
