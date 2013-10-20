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

package org.red5.xuggler.handler;

import org.red5.xuggler.Message;

/**
 * Called by the UrlProtocolHandler to read and write Events. 
 */
public interface IEventIOHandler {
	
	/**
	 * Called by a handler to get the next message.
	 * 
	 * @return The next IAVMessage; blocks until a message is ready.
	 * @throws InterruptedException if interrupted while waiting
	 */
	Message read() throws InterruptedException;

	/**
	 * Writes the given message.
	 * 
	 * @param msg The message to write
	 * @return number of bytes written
	 * @throws InterruptedException if interrupted while waiting
	 */
	int write(Message msg) throws InterruptedException;
	
}
