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

package org.red5.stream.http.servlet;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.red5.logging.Red5LoggerFactory;
import org.red5.service.httpstream.SegmenterService;
import org.red5.service.httpstream.model.Segment;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

/**
 * Servlet implementation class TransportSegmentFeeder. This servlet handles
 * requests of the extension ".tsx". Segment data is fed down the output stream
 * without needing to request additional segments.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class TransportSegmentFeeder extends HttpServlet {

	private static final long serialVersionUID = 92227417L;

	private static Logger log = Red5LoggerFactory.getLogger(TransportSegmentFeeder.class);

	private static SegmenterService service;

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		log.debug("Segment feed requested");
		// get red5 context and segmenter
		if (service == null) {
			ApplicationContext appCtx = (ApplicationContext) getServletContext().getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
			service = (SegmenterService) appCtx.getBean("segmenter.service");
		}
		// get the requested stream / segment
		String servletPath = request.getServletPath();
		String streamName = servletPath.split("\\.")[0];
		log.debug("Stream name: {}", streamName);
		if (service.isAvailable(streamName)) {
			response.setContentType("video/MP2T");
			// data segment
			Segment segment = null;
			// setup buffers and output stream
			byte[] buf = new byte[188];
			ByteBuffer buffer = ByteBuffer.allocate(188);
			ServletOutputStream sos = response.getOutputStream();
			// loop segments
			while ((segment = service.getSegment(streamName)) != null) {
				do {
					buffer = segment.read(buffer);
					// log.trace("Limit - position: {}", (buffer.limit() - buffer.position()));
					if ((buffer.limit() - buffer.position()) == 188) {
						buffer.get(buf);
						// write down the output stream
						sos.write(buf);
					} else {
						log.info("Segment result has indicated a problem");
						// verifies the currently requested stream segment
						// number against the currently active segment
						if (service.getSegment(streamName) == null) {
							log.debug("Requested segment is no longer available");
							break;
						}
					}
					buffer.clear();
				} while (segment.hasMoreData());
				log.trace("Segment {} had no more data", segment.getIndex());
				// flush
				sos.flush();
				// segment had no more data
				segment.cleanupThreadLocal();
			}
			buffer.clear();
			buffer = null;
		} else {
			// let requester know that stream segment is not available
			response.sendError(404, "Requested segmented stream not found");
		}
	}

}
