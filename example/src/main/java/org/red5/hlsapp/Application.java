package com.red5.hlsapp;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.stream.ClientBroadcastStream;
import org.red5.service.httpstream.MuxService;
import org.red5.service.httpstream.SegmenterService;
import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class Application extends MultiThreadedApplicationAdapter implements ApplicationContextAware {

	private static Logger log = Red5LoggerFactory.getLogger(Application.class, "hlsapp");
	
	private ApplicationContext applicationContext;
	
	@Override
	public boolean appStart(IScope scope) {
		return super.appStart(scope);
	}

	@Override
	public void appStop(IScope scope) {
		super.appStop(scope);
	}

	@Override
	public void appDisconnect(IConnection conn) {
		log.debug("appDisconnect");
		// ensure that the recorded stream was completed or reject it here
		if (conn.hasAttribute("streamName")) {
			@SuppressWarnings("unused")
			String streamName = conn.getStringAttribute("streamName");

		}
		super.appDisconnect(conn);
	}	
	
	@Override
	public boolean start(IScope scope) {
		// create and start a muxer
		MuxService muxer = (MuxService) applicationContext.getBean("mux.service");
		muxer.start(scope);
		return super.start(scope);
	}

	@Override
	public void stop(IScope scope) {
		// stop the muxer
		MuxService muxer = (MuxService) applicationContext.getBean("mux.service");
		muxer.stop(scope);		
		super.stop(scope);
	}

	@Override
	public void streamRecordStart(IBroadcastStream stream) {
		log.debug("streamRecordStart - stream name: {}", stream.getPublishedName());
		// save the record / stream name
		Red5.getConnectionLocal().setAttribute("streamName", stream.getPublishedName());
		super.streamRecordStart(stream);
	}

	@Override
	public void streamPublishStart(final IBroadcastStream stream) {
		final String streamName = stream.getPublishedName();
		log.debug("streamPublishStart - stream name: {}", streamName);
		IConnection conn = Red5.getConnectionLocal();
		// save the record / stream name
		conn.setAttribute("streamName", streamName);
		super.streamPublishStart(stream);
		if (stream instanceof ClientBroadcastStream) {					
			Thread creator = new Thread(new Runnable() {
				public void run() {
					while (scope.getBroadcastScope(streamName) == null) {
						log.debug("Stream: {} is not available yet...", streamName);
						try {
							Thread.sleep(500L);
						} catch (InterruptedException e) {
						}
					}
					// get the segmenter
					SegmenterService segmenter = (SegmenterService) applicationContext.getBean("segmenter.service");
					// check that the stream is not already being segmented
					if (!segmenter.isAvailable(streamName)) {
						// start segmenting utilizing included RTMP reader
						segmenter.start(scope, stream, true);
					}					
				}
			});
			creator.setDaemon(true);
			creator.start();
		}		
	}

	@Override
	public void streamBroadcastClose(IBroadcastStream stream) {
		log.debug("streamBroadcastClose - stream name: {}", stream.getPublishedName());
		super.streamBroadcastClose(stream);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

}
