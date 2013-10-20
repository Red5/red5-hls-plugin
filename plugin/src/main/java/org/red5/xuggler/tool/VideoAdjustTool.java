package org.red5.xuggler.tool;

import org.red5.service.httpstream.SegmentFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuggle.mediatool.MediaToolAdapter;
import com.xuggle.mediatool.event.ICloseEvent;
import com.xuggle.mediatool.event.IVideoPictureEvent;
import com.xuggle.xuggler.IPixelFormat.Type;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;

/**
 * Video frame dimension adjustment.
 * 
 * @author Paul
 */
public class VideoAdjustTool extends MediaToolAdapter implements GenericTool {

	private Logger log = LoggerFactory.getLogger(VideoAdjustTool.class);

	private IVideoResampler resampler = null;

	private int width;

	private int height;

	private Type pixelType = Type.YUV420P;

	private SegmentFacade facade;

	public VideoAdjustTool(int width, int height) {
		log.trace("Video width: {} height: {}", width, height);
		this.width = width;
		this.height = height;
		for (IVideoResampler.Feature feature : IVideoResampler.Feature.values()) {
			if (!IVideoResampler.isSupported(feature)) {
				log.warn("VideoResampler {} feature is not supported", feature);
			}
		}
	}

	@Override
	public void onVideoPicture(IVideoPictureEvent event) {
		log.debug("Adjust onVideo");
		IVideoPicture in = event.getPicture();
		log.debug("Video ts: {}", in.getFormattedTimeStamp());
		int inWidth = in.getWidth();
		int inHeight = in.getHeight();
		if (inHeight != height || inWidth != width) {
			log.debug("VideoAdjustTool onVideoPicture");
			log.trace("Video timestamp: {} pixel type: {}", event.getTimeStamp(), in.getPixelType());
			log.trace("Video in: {} x {} out: {} x {}", new Object[] { inWidth, inHeight, width, height });
			if (resampler == null) {
				resampler = IVideoResampler.make(width, height, pixelType, inWidth, inHeight, in.getPixelType());
				log.debug("Video resampler: {}", resampler);
			}
			if (resampler != null) {
				IVideoPicture out = IVideoPicture.make(pixelType, width, height);
				if (resampler.resample(out, in) >= 0) {
					//check complete
					if (out.isComplete()) {
						// queue video
						facade.queueVideo(out, event.getTimeStamp(), event.getTimeUnit());
						in.delete();
					} else {
						log.warn("Resampled picture was not marked as complete");
					}
				} else {
					log.warn("Resample failed");
				}
				out.delete();
			} else {
				log.debug("Resampler was null");
			}
			log.debug("VideoAdjustTool onVideoPicture - end");
		} else {
			// queue video
			facade.queueVideo(in, event.getTimeStamp(), event.getTimeUnit());
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
