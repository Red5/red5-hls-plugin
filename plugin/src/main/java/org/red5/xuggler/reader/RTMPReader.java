package org.red5.xuggler.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.MediaToolAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.mediatool.event.IAudioSamplesEvent;
import com.xuggle.mediatool.event.ICloseEvent;
import com.xuggle.mediatool.event.IVideoPictureEvent;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IError;

/**
 * Reads media data from an RTMP source.
 * 
 * @author Paul
 */
public class RTMPReader extends MediaToolAdapter implements GenericReader {

	private Logger log = LoggerFactory.getLogger(RTMPReader.class);

	private IMediaReader reader;

	private String inputUrl;

	private int inputWidth;

	private int inputHeight;

	private int inputSampleRate;

	private int inputChannels;

	private boolean audioEnabled = true;

	private boolean videoEnabled = true;

	private boolean keyFrameReceived;

	// time at which we started reading
	private long startTime;

	// total samples read
	private volatile long audioSamplesRead;

	// total frames read
	private volatile long videoFramesRead;

	private boolean closed = true;

	public RTMPReader() {
	}

	public RTMPReader(String url) {
		inputUrl = url;
	}

	public void init() {
		log.debug("Input url: {}", inputUrl);
		/*
		IContainerFormat format = IContainerFormat.make();
		format.setInputFormat("flv");
		IContainer container = IContainer.make(format);
		container.setReadRetryCount(0);
		container.setInputBufferLength(0);
		container.setProperty("strict", "experimental");
		container.setProperty("analyzeduration", 0); // int = specify how many microseconds are analyzed to probe the input (from 0 to INT_MAX)
		if (container.open(inputUrl, IContainer.Type.READ, null, false, false) < 0) {
		    throw new RuntimeException("Unable to open read container");
		}		
		reader = ToolFactory.makeReader(container);
		*/

		// url only
		reader = ToolFactory.makeReader(inputUrl);
		reader.setCloseOnEofOnly(false);
		reader.setQueryMetaData(true);
		reader.setAddDynamicStreams(false);

		// get the container
		IContainer container = reader.getContainer();
		container.setReadRetryCount(0);
		container.setInputBufferLength(0);
		container.setProperty("analyzeduration", 0); // int = specify how many microseconds are analyzed to probe the input (from 0 to INT_MAX)
		//		container.setProperty("probesize", 1024); // int = set probing size in bytes (from 32 to INT_MAX)
		//		container.setProperty("fpsprobesize", 4); // int = number of frames used to probe
		//		container.setPreload(1);
		//		IContainerFormat format = container.getContainerFormat();
		//		format.setInputFormat("flv");
		//		container.setFormat(format);
		if (videoEnabled) {
			// have the reader create a buffered image that others can reuse
			//reader.setBufferedImageTypeToGenerate(BufferedImage.TYPE_3BYTE_BGR);
		} else {
			reader.setBufferedImageTypeToGenerate(-1);
		}
		//add this as the first listener
		reader.addListener(this);
	}

	public void stop() {
		log.debug("Stop");
		if (reader != null) {
			reader.removeListener(this);
			try {
				reader.close();
			} catch (Exception e) {
				log.warn("Exception closing reader", e);
			} finally {
				closed = true;
			}
			reader = null;
		}
	}

	public void run() {
		log.debug("RTMPReader - run");
		// read and decode packets from the source
		log.trace("Starting reader loop");

		//		IContainer container = reader.getContainer();
		//		IPacket packet = IPacket.make();
		//      while (container.readNextPacket(packet) >= 0 && !packet.isKeyPacket()) {
		//			log.debug("Looking for key packet..");        	
		//      }
		//      packet.delete();

		// track start time
		startTime = System.currentTimeMillis();
		// open for business
		closed = false;
		//
		int packetsRead = 0;
		// error holder
		IError err = null;
		try {
			// packet read loop
			while ((err = reader.readPacket()) == null) {
				long elapsedMillis = (System.currentTimeMillis() - startTime);
				log.debug("Reads - frames: {} samples: {}", videoFramesRead, audioSamplesRead);
				if (log.isTraceEnabled()) {
					log.trace("Reads - packets: {} elapsed: {} ms", packetsRead++, elapsedMillis);
				}
			}
		} catch (Throwable t) {
			log.warn("Exception closing reader", t);
		}
		if (err != null) {
			log.warn("{}", err.toString());
		}
		log.trace("End of reader loop");
		stop();
		log.trace("RTMPReader - end");
	}

	@Override
	public void onAudioSamples(IAudioSamplesEvent event) {
		log.trace("Reader onAudioSamples");
		if (audioEnabled) {
			// increment our count
			audioSamplesRead += event.getAudioSamples().getNumSamples();
			// pass the even up the chain
			super.onAudioSamples(event);
		}
	}

	@Override
	public void onVideoPicture(IVideoPictureEvent event) {
		log.trace("Reader onVideo");
		if (videoEnabled) {
			// look for a key frame
			keyFrameReceived = event.getPicture().isKeyFrame() ? true : keyFrameReceived;
			// once we have had one, proceed
			if (keyFrameReceived) {
				videoFramesRead += 1;
				super.onVideoPicture(event);
			}
		}
	}

	@Override
	public void onClose(ICloseEvent event) {
		log.debug("Reader close");
		super.onClose(event);
	}

	public String getInputUrl() {
		return inputUrl;
	}

	public void setInputUrl(String inputUrl) {
		this.inputUrl = inputUrl;
	}

	/**
	 * @return the inputWidth
	 */
	public int getInputWidth() {
		return inputWidth;
	}

	/**
	 * @return the inputHeight
	 */
	public int getInputHeight() {
		return inputHeight;
	}

	/**
	 * @return the inputSampleRate
	 */
	public int getInputSampleRate() {
		return inputSampleRate;
	}

	/**
	 * @return the inputChannels
	 */
	public int getInputChannels() {
		return inputChannels;
	}

	/**
	 * @return the reader
	 */
	public IMediaReader getReader() {
		return reader;
	}

	/**
	 */
	public void disableAudio() {
		this.audioEnabled = false;
	}

	/**
	 */
	public void disableVideo() {
		this.videoEnabled = false;
	}

	/**
	 * @return the audioEnabled
	 */
	public boolean isAudioEnabled() {
		return audioEnabled;
	}

	/**
	 * @return the videoEnabled
	 */
	public boolean isVideoEnabled() {
		return videoEnabled;
	}

	/**
	 * Returns whether or not the reader is closed.
	 * 
	 * @return true if closed or reader does not exist
	 */
	public boolean isClosed() {
		return closed;
	}

	/**
	 * Returns true if data has been read from the source.
	 * 
	 * @return
	 */
	public boolean hasReadData() {
		return audioSamplesRead > 0 || videoFramesRead > 0;
	}

	/**
	 * @return the keyFrameReceived
	 */
	public boolean isKeyFrameReceived() {
		return keyFrameReceived;
	}

}
