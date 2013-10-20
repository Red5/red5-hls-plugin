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

package org.red5.stream.http.xuggler;

import java.util.HashMap;
import java.util.Map;

import org.red5.logging.Red5LoggerFactory;
import org.red5.xuggler.handler.IEventIOHandler;
import org.slf4j.Logger;

import com.xuggle.xuggler.ISimpleMediaFile;
import com.xuggle.xuggler.io.IURLProtocolHandler;
import com.xuggle.xuggler.io.IURLProtocolHandlerFactory;
import com.xuggle.xuggler.io.URLProtocolManager;

/**
 * Used by XUGGLE.IO to get a new URLProtocolHandler for an mpeg-ts URL.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class MpegTsHandlerFactory implements IURLProtocolHandlerFactory {

	private static Logger log = Red5LoggerFactory.getLogger(MpegTsHandlerFactory.class);

	private Map<String, MpegTsIoHandler> streams;

	private Map<String, ISimpleMediaFile> streamsInfo;

	private static MpegTsHandlerFactory singleton = new MpegTsHandlerFactory();

	public static final String DEFAULT_PROTOCOL = "redfivempegts";

	/**
	 * Get the default factory used by XUGGLE.IO for mpeg-ts streams to
	 * {@link #DEFAULT_PROTOCOL} (i.e. {@value #DEFAULT_PROTOCOL}).
	 * 
	 * @return The factory
	 */
	public static MpegTsHandlerFactory getFactory() {
		return getFactory(DEFAULT_PROTOCOL);
	}

	/**
	 * Register a factory that XUGGLE.IO will use for the given protocol.
	 * 
	 * NOTE: Protocol can only contain alpha characters.
	 * 
	 * @param protocolPrefix
	 *            The protocol (e.g. "mpegts").
	 * @return The factory
	 */
	public static MpegTsHandlerFactory getFactory(String protocolPrefix) {
		if (singleton != null) {
			URLProtocolManager manager = URLProtocolManager.getManager();
			manager.registerFactory(protocolPrefix, singleton);
			return singleton;			
		} else {
			throw new NullPointerException("Unexpectedly, there is no factory");
		}
	}

	/**
	 * The Constructor is package-level only so that test functions can use this
	 * without using the Singleton.
	 */
	private MpegTsHandlerFactory() {
		streams = new HashMap<String, MpegTsIoHandler>();
		streamsInfo = new HashMap<String, ISimpleMediaFile>();
	}

	/**
	 * Called by XUGGLE.IO to get a a handler for a given URL. The handler
	 * must have been registered via
	 * {@link #registerStream(IEventIOHandler, ISimpleMediaFile)}
	 * 
	 * WARNING: It really only makes sense to have one active ProtocolHandler
	 * working on a AVStreamingQueue at a time; it's up to the caller to ensure
	 * this happens; otherwise you may get deadlocks as all the protocol
	 * handlers compete for events.
	 * 
	 * @param protocol
	 *            The protocol (e.g. "mpegts")
	 * @param url
	 *            The url being opened, including the protocol (e.g. "mpegts:stream01")
	 * @param flags
	 *            The flags that FFMPEG is opening the file with.
	 * 
	 */
	public synchronized IURLProtocolHandler getHandler(String protocol, String url, int flags) {
		log.debug("Get handler - protocol: {} url: {}", protocol, url);
		IURLProtocolHandler result = null;
		// Note: We need to remove any protocol markers from the url
		String streamName = URLProtocolManager.getResourceFromURL(url);
		MpegTsIoHandler handler = streams.get(streamName);
		if (handler != null) {
			result = new MpegTsHandler(handler, streamsInfo.get(streamName), url, flags);
		}
		return result;
	}

	/**
	 * Register a stream name with this factory. Any handlers returned by this
	 * factory for this streamName will use the passed buffer.
	 * 
	 * Note that streamInfo is only used when reading from a registered stream.
	 * When writing IRTMPEvent, we will use whatever MetaData XUGGLE tells us
	 * about the stream.
	 * 
	 * @param handler
	 *            The handler for that stream name
	 * @param streamInfo
	 *            A {@link ISimpleMediaFile} containing meta data about what
	 *            kind of stream the {@link IRTMPEventIOHandler} is handling.
	 *            This is used by the handler to construct header and meta-data
	 *            information. Can be null in which case we'll assume both audio
	 *            and video in file. {@link ISimpleMediaFile#getURL()} must
	 *            return a non null value.
	 * @return The IRTMPEventIOHandler previously registered for this
	 *         streamName, or null if none.
	 */
	public synchronized MpegTsIoHandler registerStream(MpegTsIoHandler handler, ISimpleMediaFile streamInfo) {
		log.debug("Register - handler: {} info: {}", handler, streamInfo);
		if (streamInfo == null) {
			throw new IllegalArgumentException("Stream info required");
		}
		String streamURL = streamInfo.getURL();
		if (streamURL == null) {
			throw new IllegalArgumentException("URL required");
		}
		String streamName = URLProtocolManager.getResourceFromURL(streamURL);
		streamsInfo.put(streamName, streamInfo);
		return streams.put(streamName, handler);
	}

	/**
	 * Stop supporting a given streamName.
	 * 
	 * @param streamURL
	 *            The stream url to stop supporting. Current files open will
	 *            continue to to be used, but future opens will get a file not
	 *            found error in ffmpeg.
	 * @return The AVBufferStream previously registered for this streamName, or
	 *         null if none.
	 */
	public synchronized MpegTsIoHandler deleteStream(String streamURL) {
		log.debug("Delete - url: {}", streamURL);
		String streamName = URLProtocolManager.getResourceFromURL(streamURL);
		streamsInfo.remove(streamName);
		return streams.remove(streamName);
	}
}
