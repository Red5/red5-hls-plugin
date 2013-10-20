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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.scope.IScope;
import org.red5.stream.util.AudioMux;
import org.slf4j.Logger;

/**
 * Useful for muxing / mixing the stream audio for an entire scope.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class MuxService {
	
	private static Logger log = Red5LoggerFactory.getLogger(MuxService.class);

	// map of currently available muxes, keyed by scope name
	private static ConcurrentMap<String, AudioMux> muxMap = new ConcurrentHashMap<String, AudioMux>();
	
	/**
	 * Creates and starts a mux for the given scope.
	 * 
	 * @param scope
	 */
	public void start(IScope scope) {
		String scopeName = scope.getName();
		log.debug("Start mux for {} scope", scopeName);
		// if a different output sample rate is needed, add it to the scope attributes
		int outputSampleRate = 44100;
		if (scope.hasAttribute("outputSampleRate")) {
			outputSampleRate = (int) scope.getAttribute("outputSampleRate");
		}
		// create an identifier that will be used as the stream name
		String name = String.format("%s-audio", scopeName);
		// store the stream name on the scope
		scope.setAttribute("audioStreamName", name);
		// create a facade
		SegmenterService segmenter = (SegmenterService) scope.getContext().getBean("segmenter.service");
		SegmentFacade facade = segmenter.start(name, false);
		if (facade != null) {
			AudioMux mux = new AudioMux(outputSampleRate, true);
			muxMap.put(scopeName, mux);
			mux.setFacade(facade);
			// start the mux
			mux.start(scope.getName());
		} else {
			log.warn("Facade creation failed for {}", name);
		}
	}

	/**
	 * Stop the muxer for a given scope.
	 * 
	 * @param scope
	 */
	public void stop(IScope scope) {
		log.debug("Stop mux for {} scope", scope.getName());
		AudioMux mux = muxMap.remove(scope.getName());
		mux.stop();
	}
	
	public AudioMux getAudioMux(String name) {
		return muxMap.get(name);
	}
	
}
