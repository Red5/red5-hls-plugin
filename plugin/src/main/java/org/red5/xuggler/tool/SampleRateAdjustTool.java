package org.red5.xuggler.tool;

import org.red5.service.httpstream.SegmentFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuggle.mediatool.MediaToolAdapter;
import com.xuggle.mediatool.event.IAudioSamplesEvent;
import com.xuggle.mediatool.event.ICloseEvent;
import com.xuggle.xuggler.IAudioResampler;
import com.xuggle.xuggler.IAudioSamples;

/**
 * Create a tool which adjusts the sample rate of audio.
 * 
 * @author Paul
 */
public class SampleRateAdjustTool extends MediaToolAdapter implements GenericTool {

	private Logger log = LoggerFactory.getLogger(SampleRateAdjustTool.class);

	private IAudioResampler resampler;

	// sample rate to adjust to
	private int rate;

	private int channels;

	private SegmentFacade facade;
	
	/** 
	 * Construct a sample rate adjustor.
	 * 
	 * @param rate
	 */
	public SampleRateAdjustTool(int rate, int channels) {
		log.trace("Sample rate: {} channels: {}", rate, channels);
		this.rate = rate;
		this.channels = channels;
	}

	/** {@inheritDoc} */
	@Override
	public void onAudioSamples(IAudioSamplesEvent event) {
		IAudioSamples samples = event.getAudioSamples();
		if (samples.getSampleRate() != rate || samples.getChannels() != channels) {
			log.debug("SampleRateAdjustTool onAudioSamples");
    		if (resampler == null) {
    			// http://build.xuggle.com/view/Stable/job/xuggler_jdk5_stable/javadoc/java/api/com/xuggle/xuggler/IAudioResampler.html
    			resampler = IAudioResampler.make(channels, samples.getChannels(), rate, samples.getSampleRate(), IAudioSamples.Format.FMT_S16, samples.getFormat());
    			log.info("Resampled formats - input: {} output: {}", resampler.getInputFormat(), resampler.getOutputFormat());
    		}
    		long sampleCount = samples.getNumSamples();
    		if (resampler != null && sampleCount > 0) {
    			log.trace("In - samples: {} rate: {} channels: {}", sampleCount, samples.getSampleRate(), samples.getChannels());
    			IAudioSamples out = IAudioSamples.make(sampleCount, channels);
    			resampler.resample(out, samples, sampleCount);
    			log.trace("Out - samples: {} rate: {} channels: {}", out.getNumSamples(), out.getSampleRate(), out.getChannels());
    			// queue audio
    			facade.queueAudio(out, event.getTimeStamp(), event.getTimeUnit());
    			//out.delete();
    			samples.delete();
    		} else {
    			facade.queueAudio(samples, event.getTimeStamp(), event.getTimeUnit());
    		}
    		log.debug("SampleRateAdjustTool onAudioSamples - end");
		} else {
			facade.queueAudio(samples, event.getTimeStamp(), event.getTimeUnit());
		}
	}

	/* (non-Javadoc)
	 * @see com.xuggle.mediatool.MediaToolAdapter#onClose(com.xuggle.mediatool.event.ICloseEvent)
	 */
	@Override
	public void onClose(ICloseEvent event) {
		close();
		super.onClose(event);
	}

	public void close() {
		if (resampler != null) {
			resampler.delete();
		}
	}

	/**
	 * @param facade the facade to set
	 */
	public void setFacade(SegmentFacade facade) {
		this.facade = facade;
	}
	
}
