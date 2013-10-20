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

package org.red5.xuggler.reader;

import java.io.IOException;

public interface GenericReader extends Runnable {

	public void init() throws IOException;

	public void stop();

	public boolean isAudioEnabled();

	public boolean isVideoEnabled();

	public void setInputUrl(String inputUrl);

	public String getInputUrl();

	public int getInputWidth();

	public int getInputHeight();

	public int getInputChannels();

	public int getInputSampleRate();
	
}
