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

package org.red5.stream.util;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

import org.red5.logging.Red5LoggerFactory;
import org.red5.service.httpstream.SegmentFacade;
import org.red5.xuggler.SampleData;
import org.slf4j.Logger;

import com.xuggle.xuggler.Global;

/**
 * Muxer for 1..n audio streams.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Gavriloaie Eugen-Andrei(crtmpserver@gmail.com)
 */
public class AudioMux {

	private static Logger log = Red5LoggerFactory.getLogger(AudioMux.class);

	// end of media flag
	private boolean eofm;
	
	private int samplingRate;

	private int maxChannelsCount = 0;

	private boolean insertSilence;

	private static final short SILENCE_VALUE = (short) 0;

	private static final int MERGE = 1;

	private static final int LEFT_RIGHT = 2;

	private static final int VOLUME = 3;

	private int method = MERGE;

	private CopyOnWriteArraySet<Track> tracks = new CopyOnWriteArraySet<Track>();
	
	private SegmentFacade facade;

	/**
	 * @param samplingRate - the sampling rate the all the tracks will have
	 * @param insertSilence
	 */
	public AudioMux(int samplingRate, boolean insertSilence) {
		log.debug("AudioMux - samplingRate: {} insertSilence: {}", samplingRate, insertSilence);
		this.samplingRate = samplingRate;
		this.insertSilence = insertSilence;
	}
	
	/**
	 * Start the mux for the given name. The name in most cases refers to a scope or room.
	 * 
	 * @param name
	 */
	public void start(String name) {
		Thread feeder = new Thread(new Runnable() {
			public void run() {
				double audioHead = 0; //where we are with the audio feeding process
				do {
					audioHead += feedAudio(100000000, audioHead);
					if (!hasSamples()) {
						try {
							Thread.sleep(200);
						} catch (InterruptedException e) {
						}
					}
					log.trace("audioHead: {}", audioHead / 1000000d);
				} while (!eofm);				
			}
		}, "AudioMux@" + name);
		feeder.setDaemon(true);
		feeder.start();
	}
	
	public void stop() {
		eofm = true;
		clear();
		facade.getSegment().setLast(true);
	}

	/**
	 * Perform muxing of tracks and feed them to the facade.
	 * 
	 * @param feedAmount
	 * @param clock
	 * @return
	 */
	private double feedAudio(double feedAmount, double clock) {
		int requestedSamplesAmount = (int) ((feedAmount / 1000000d) * samplingRate);
		short[] samples = popMuxedData(requestedSamplesAmount);
		if (samples == null) {
			return 0;
		}
		facade.queueAudio(samples, (int) clock, Global.DEFAULT_TIME_UNIT);
		double result = samples.length / 2;
		result = (double)result / (double) samplingRate;
		result *= 1000000d;
		return result;
	}		
	
	/**
	 * Creates a new track.
	 * 
	 * @param channelsCount - the number of channels present on the track
	 * @return the index of the newly created track
	 */
	public Track addTrack(int channelsCount) {
		Track track = new Track(samplingRate, channelsCount);
		track.setStreamName(tracks.size() + "");
		tracks.add(track);
		maxChannelsCount = maxChannelsCount < channelsCount ? channelsCount : maxChannelsCount;
		return track;
	}
	
	/**
	 * Creates a new track with an associated stream name.
	 * 
	 * @param channelsCount - the number of channels present on the track
	 * @param streamName Streams name
	 * @return the index of the newly created track
	 */
	public Track addTrack(int channelsCount, String streamName) {
		log.debug("Adding audio track for {}", streamName);
		Track track = new Track(samplingRate, channelsCount);
		track.setStreamName(streamName);
		tracks.add(track);
		maxChannelsCount = maxChannelsCount < channelsCount ? channelsCount : maxChannelsCount;
		return track;
	}

	/**
	 * Pushes data into mux.
	 * 
	 * @param streamName - the stream name originating the audio to be pushed
	 * @param samples - actual data
	 */
	public boolean pushData(String streamName, short[] samples) {
		if (!tracks.isEmpty()) {
			for (Track track : tracks) {
				if (track.getStreamName().equals(streamName)) {
					return track.pushData(samples);
				}
			}
		}
		return false;
	}

	/**
	 * Muxes all the available channels and extracts whatever comes out
	 * @return it will return the muxed samples. It might return null if not enough data is available
	 */
	public short[] popMuxedData(int maxSamplesCount) {
		/*
		 * Example:
		 * Suppose we have 3 tracks. One mono, one stereo and one with 3 channels (stereo + bass enhancement)
		 * All tracks have different number of samples available, just to make the life a little bit complex
		 * Actually, this is closer to reality because source A might have x samples per frame while source B might have y
		 * 
		 * Lij = left, track i, sample j
		 * Bij = bass, track i, sample j
		 * Rij = right, track i, sample j
		 * 
		 * Track1 - stereo+bass: L11 B11 R11 L12 B12 R12 L13 B13 R13 L14 B14 R14 L15 B15 R15
		 * Track2 - mono:        L21         L22         L23         L24         
		 * Track3 - stereo:      L31     R31 L32     R32 L33     R33 L34     R34 L35     R35 L36     R36
		 * 
		 * Result (I put each sample on separate row): 
		 * 	(L11+L21+L31)/3 B11 (R11+R31)/2
		 * 	(L12+L22+L32)/3 B12 (R12+R32)/2
		 * 	(L13+L23+L33)/3 B13 (R13+R33)/2
		 * 	(L14+L34)/2     B14 (R14+R34)/2
		 * 	(L15+L35)/2     B15 (R15+R35)/2
		 * 	L36             0   R36
		 * 
		 * Facts:
		 *  - all tracks MUST have the same sampling rate
		 *  - resulted stuff will have 6 samples (maximum from all tracks)
		 *  - resulted stuff will have 3 channels (maximum number of channels from each track)
		 *  - to combine tracks, we use simple arithmetic mean value
		 */
		int resultSize = computeResultSize();
		if (resultSize <= 0) {
			return null;
		}
		if (maxSamplesCount > 0) {
			resultSize = resultSize < maxSamplesCount ? resultSize : maxSamplesCount;
		}
		short result[] = new short[resultSize * maxChannelsCount];
		int muxedResult = 0;
		int contributingTracksCount = 0;
		Short sample = 0;
		for (int sampleIdx = 0; sampleIdx < resultSize; sampleIdx++) {
			for (int channelIdx = 0; channelIdx < maxChannelsCount; channelIdx++) {
				switch (method) {
					case MERGE: {
						muxedResult = 0;
						contributingTracksCount = 0;
						for (Track track : tracks) {
							sample = track.getSample(sampleIdx, channelIdx);
							if (sample != null) {
								double tmp = sample * 1.0d; // 1.0d is volume adjust value
								if (tmp < Short.MIN_VALUE) {
									tmp = Short.MIN_VALUE;
								} else if (tmp > Short.MAX_VALUE) {
									tmp = Short.MAX_VALUE;
								}
								muxedResult += (short) tmp;
								contributingTracksCount++;
							}
						}
						// prevent divide by zero error
						if (contributingTracksCount > 0) {
							muxedResult = muxedResult / contributingTracksCount;
							result[sampleIdx * maxChannelsCount + channelIdx] = (short) muxedResult;
						} else {
							result[sampleIdx * maxChannelsCount + channelIdx] = SILENCE_VALUE;
						}
						break;
					}
					case LEFT_RIGHT: {
						result[sampleIdx * maxChannelsCount + channelIdx] = tracks.iterator().next().getSample(sampleIdx, channelIdx);
						break;
					}
					case VOLUME: {
						result[sampleIdx * maxChannelsCount + channelIdx] = (short) (tracks.iterator().next().getSample(sampleIdx, channelIdx) * 2.5);
						break;
					}

				}
			}
		}
		for (Track track : tracks) {
			track.ignoreSamples(resultSize);
		}
		return result;
	}

	/**
	 * Computes the maximum number of samples available
	 * @return
	 */
	private int computeResultSize() {
		int result = insertSilence ? 0 : Integer.MAX_VALUE;
		int samplesCount = 0;
		for (Track track : tracks) {
			samplesCount = track.getSamplesCount();
			log.debug("Track {}", track);
			if (insertSilence) {
				result = result > samplesCount ? result : samplesCount;
			} else {
				result = result < samplesCount ? result : samplesCount;
			}
		}
		log.debug("Result size: {}", result);
		return result;
	}

	public int size() {
		int samples = 0;
		for (Track track : tracks) {
			samples += track.getSamplesCount();
		}
		return samples;
	}

	public boolean hasSamples() {
		return size() > 0;
	}
	
	public boolean isFinished() {
		return eofm;
	}

	public void clear() {
		tracks.clear();
	}

	public void removeTrack(Track track) {
		tracks.remove(track);
	}

	/**
	 * Remove a streams track.
	 * 
	 * @param streamName
	 */
	public void removeTrack(String streamName) {
		for (Track track : tracks) {
			if (track.getStreamName().equals(streamName)) {
				log.debug("Removing audio track for {}", streamName);
				removeTrack(track);
				break;
			}
		}		
	}	
	
	/**
	 * @return the facade
	 */
	public SegmentFacade getFacade() {
		return facade;
	}

	/**
	 * @param facade the facade to set
	 */
	public void setFacade(SegmentFacade facade) {
		this.facade = facade;
	}

	/**
	 * Represents an audio track.
	 */
	public class Track {

		private String streamName;
		
		private int channelsCount;

		private volatile int count;

		private volatile ConcurrentLinkedQueue<SampleData> buffer;

		private volatile SampleData data;

		public Track(int samplingRate, int channelsCount) {
			this.channelsCount = channelsCount;
			buffer = new ConcurrentLinkedQueue<SampleData>();
		}

		/**
		 * @return the streamName
		 */
		public String getStreamName() {
			return streamName;
		}

		/**
		 * @param streamName the streamName to set
		 */
		public void setStreamName(String streamName) {
			this.streamName = streamName;
		}

		public int getSamplesCount() {
			int i = count;
			return i > 0 ? i / channelsCount : 0;
		}

		public Short getSample(int sampleIndex, int channelIdx) {
			if (count / channelsCount <= sampleIndex) {
				return null;
			}
			if (channelIdx >= channelsCount) {
				return null;
			}
			if ((sampleIndex * channelsCount + channelIdx) >= count) {
				return null;
			}
			// if there is no currently selected sample data
			if (data == null) {
				// get the next set of samples
				data = buffer.poll();
				// if its null now this means we have no more data
				if (data == null) {
					return null;
				}
			}
			Short sample = data.getNextShort();
			if (sample == null && !buffer.isEmpty()) {
				data = buffer.poll();
				if (data != null) {
					sample = data.getNextShort();
				}
			}
			return sample;
		}

		public boolean pushData(short[] samples) {
			log.debug("Samples: {} current count: {}", samples.length, count);
			// add to the buffer
			if (buffer.size() > 1000) {
				return false;
			}
			boolean result = buffer.add(SampleData.build(samples));
			if (result) {
				// increment sample counter
				count += samples.length;
			}
			return result;
		}

		public void ignoreSamples(int samplesCount) {
			if (count > 0) {
				int shortsCount = samplesCount * channelsCount;
				// decrement sample counter
				count -= shortsCount;
			}
		}

		@Override
		public String toString() {
			return "Track [streamName=" + streamName + ", channelsCount=" + channelsCount + "]";
		}

	}
	
}
