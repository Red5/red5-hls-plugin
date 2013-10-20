package org.red5.stream.http.servlet;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2008 by respective authors (see below). All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation; either version 2.1 of the License, or (at your option) any later 
 * version. 
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along 
 * with this library; if not, write to the Free Software Foundation, Inc., 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA 
 */

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.red5.logging.Red5LoggerFactory;
import org.red5.service.httpstream.SegmenterService;
import org.red5.service.httpstream.model.Segment;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

/**
 * Servlet implementation class TransportSegment. This servlet handles requests of the extension ".ts".
 */
public class TransportSegment extends HttpServlet {
	
	private static final long serialVersionUID = 92227411L;

	private static Logger log = Red5LoggerFactory.getLogger(TransportSegment.class);

	private static SegmenterService service;

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		log.debug("Segment requested");
		// get red5 context and segmenter
		if (service == null) {
			ApplicationContext appCtx = (ApplicationContext) getServletContext().getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
			service = (SegmenterService) appCtx.getBean("segmenter.service");
		}
		//get the requested stream / segment
		// http://localhost:5080/httplivestreamingstreaming/test_1.ts
		String servletPath = request.getServletPath();
		String[] path = servletPath.split("\\.");
		log.trace("Path parts: {}", path.length);
		//fail if they request the same segment
		HttpSession session = ((HttpServletRequest) request).getSession(false);
		if (session != null) {
			String stream = (String) session.getAttribute("stream");
			if (path[0].equals(stream)) {
				log.info("Segment {} was already played by this requester", stream);
				return;
			}
			session.setAttribute("stream", path[0]);
		}		
		// look for underscore char
		int digitIndex = path[0].lastIndexOf('_') + 1;
		String streamName = path[0].substring(1, digitIndex - 1);
		int sequenceNumber = Integer.valueOf(path[0].substring(digitIndex));	
		log.debug("Stream name: {} sequence: {}", streamName, sequenceNumber);
		if (service.isAvailable(streamName)) {
			response.setContentType("video/MP2T");
			Segment segment = service.getSegment(streamName, sequenceNumber);
			if (segment != null) {
    			byte[] buf = new byte[188];
    			ByteBuffer buffer = ByteBuffer.allocate(188);
				ServletOutputStream sos = response.getOutputStream();
    			do {
    				buffer = segment.read(buffer);
    				//log.trace("Limit - position: {}", (buffer.limit() - buffer.position()));
    				if ((buffer.limit() - buffer.position()) == 188) {
    					buffer.get(buf);
    					//write down the output stream
    					sos.write(buf);
    				} else {
    					log.info("Segment result has indicated a problem");
    					// verifies the currently requested stream segment number against the  currently active segment
    					if (service.getSegment(streamName, sequenceNumber) == null) {
    						log.debug("Requested segment is no longer available");
    						break;
    					}
    				}
    				buffer.clear();
    			} while (segment.hasMoreData());
    			log.trace("Segment {} had no more data", segment.getIndex());
    			buffer = null;
    			// flush
    			sos.flush();
    			// segment had no more data
    			segment.cleanupThreadLocal();
			} else {
				log.info("Segment for {} was not found", streamName);
			}
		} else {
			//TODO let requester know that stream segment is not available
			response.sendError(404, "Segment not found");
		}
		
	}

}
