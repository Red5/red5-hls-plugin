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

package org.red5.xuggler.tool;

import java.nio.ShortBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuggle.mediatool.MediaToolAdapter;
import com.xuggle.mediatool.event.IAudioSamplesEvent;

/**
 * Create a tool which adjusts the volume of audio by some constant factor.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class VolumeAdjustTool extends MediaToolAdapter implements GenericTool {

	private Logger log = LoggerFactory.getLogger(VolumeAdjustTool.class);

	// the amount to adjust the volume by
	private double volume;

	/** 
	 * Construct a volume adjustor.
	 * 
	 * @param volume the volume muliplier, values between 0 and 1 are recommended.
	 */
	public VolumeAdjustTool(double volume) {
		this.volume = volume;
	}

	/** {@inheritDoc} */
	@Override
	public void onAudioSamples(IAudioSamplesEvent event) {
		log.debug("VolumeAdjustTool onAudioSamples");
		// get the raw audio byes and adjust it's value 
		ShortBuffer buffer = event.getAudioSamples().getByteBuffer().asShortBuffer();
		for (int i = 0; i < buffer.limit(); ++i) {
			buffer.put(i, (short) (buffer.get(i) * volume));
		}
		// call parent which will pass the audio onto next tool in chain
		super.onAudioSamples(event);
		log.debug("VolumeAdjustTool onAudioSamples - end");
	}

	public void close() {
	}

}
