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
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.red5.logging.Red5LoggerFactory;
import org.red5.service.httpstream.SegmenterService;
import org.red5.service.httpstream.model.Segment;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

/**
 * Provides an http stream playlist in m3u8 format.
 * 
 * HTML status codes used by this servlet:
 * <pre>
 *  400 Bad Request
 *  406 Not Acceptable
 *  412 Precondition Failed
 *  417 Expectation Failed
 * </pre>
 * 
 * @see
 * {@link http://tools.ietf.org/html/draft-pantos-http-live-streaming-03}
 * {@link http://developer.apple.com/iphone/library/documentation/NetworkingInternet/Conceptual/StreamingMediaGuide/HTTPStreamingArchitecture/HTTPStreamingArchitecture.html#//apple_ref/doc/uid/TP40008332-CH101-SW2}
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class PlayList extends HttpServlet {

	private static final long serialVersionUID = 978137413L;

	private static Logger log = Red5LoggerFactory.getLogger(PlayList.class);

	private static SegmenterService service;

	// number of segments that must exist before displaying any in the playlist
	private int minimumSegmentCount = 2;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		String minimumSegmentCountParam = getInitParameter("minimumSegmentCount");
		if (!StringUtils.isEmpty(minimumSegmentCountParam)) {
			minimumSegmentCount = Integer.valueOf(minimumSegmentCountParam);
		}
		log.debug("Minimum segment count - param: {} value: {}", minimumSegmentCountParam, minimumSegmentCount);
	}

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
		log.debug("Playlist requested");
		/*
		 * EXT-X-MEDIA-SEQUENCE
		 * Each media file URI in a Playlist has a unique sequence number.  The sequence number 
		 * of a URI is equal to the sequence number of the URI that preceded it plus one. The 
		 * EXT-X-MEDIA-SEQUENCE tag indicates the sequence number of the first URI that appears
		 * in a Playlist file.
		 * 
		 	#EXTM3U
		 	#EXT-X-ALLOW-CACHE:NO
			#EXT-X-MEDIA-SEQUENCE:0
			#EXT-X-TARGETDURATION:10
			#EXTINF:10,
			http://media.example.com/segment1.ts
			#EXTINF:10,
			http://media.example.com/segment2.ts
			#EXTINF:10,
			http://media.example.com/segment3.ts
			#EXT-X-ENDLIST
			
			Using one large file, testing with ipod touch, this worked (149 == 2:29)
			#EXTM3U
			#EXT-X-TARGETDURATION:149
			#EXT-X-MEDIA-SEQUENCE:0
			#EXTINF:149, no desc
			out0.ts
			#EXT-X-ENDLIST
			
			Using these encoding parameters:
			ffmpeg -i test.mp4 -re -an -vcodec libx264 -b 96k -flags +loop -cmp +chroma -partitions +parti4x4+partp8x8+partb8x8 
			-subq 5 -trellis 1 -refs 1 -coder 0 -me_range 16 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -bt 200k -maxrate 96k 
			-bufsize 96k -rc_eq 'blurCplx^(1-qComp)' -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -level 30 -aspect 320:240 -g 30 -async 2 
			-s 320x240 -f mpegts out.ts

			Suggested by others for 128k
			ffmpeg -d -i 'rtmp://123.123.117.16:1935/live/abcdpc2 live=1' -re -g 250 -keyint_min 25 -bf 0 -me_range 16 -sc_threshold 40 -cmp 256 -coder 0 -trellis 0 -subq 6 -refs 5 -r 25 -c:a libfaac -ab:a 48k -async 1 -ac:a 2 -c:v libx264 -profile baseline -s:v 320x180 -b:v 96k -aspect:v 16:9 -map 0 -ar 22050 -vbsf h264_mp4toannexb -flags -global_header -f segment -segment_time 10 -segment_format mpegts /dev/shm/stream128ios%09d.ts 2>/dev/null
		 */
		// get red5 context and segmenter
		if (service == null) {
			ApplicationContext appCtx = (ApplicationContext) getServletContext().getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
			service = (SegmenterService) appCtx.getBean("segmenter.service");
		}
		// path
		String servletPath = request.getServletPath();
		//get the requested stream
		final String streamName = servletPath.substring(1, servletPath.indexOf(".m3u8"));
		log.debug("Request for stream: {} playlist", streamName);
		//check for the stream
		if (service.isAvailable(streamName)) {
			log.debug("Stream: {} is available", streamName);
			// get the segment count
			int count = service.getSegmentCount(streamName);
			log.debug("Segment count: {}", count);
			// check for minimum segment count and if we dont match or exceed
			// wait for (minimum segment count * segment duration) before returning
			if (count < minimumSegmentCount) {
				log.debug("Starting wait loop for segment availability");
				long maxWaitTime = minimumSegmentCount * service.getSegmentTimeLimit();
				long start = System.currentTimeMillis();
				do {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
					}
					if ((System.currentTimeMillis() - start) >= maxWaitTime) {
						log.info("Maximum segment wait time exceeded for {}", streamName);
						break;
					}
				} while ((count = service.getSegmentCount(streamName)) < minimumSegmentCount);
			}
			/*
			HTTP streaming spec section 3.2.2
			Each media file URI in a Playlist has a unique sequence number.  The sequence number of a URI is equal to the sequence number
			of the URI that preceded it plus one. The EXT-X-MEDIA-SEQUENCE tag indicates the sequence number of the first URI that appears 
			in a Playlist file.
			*/
			// get the completed segments
			Segment[] segments = service.getSegments(streamName);
			if (segments != null && segments.length > 0) {
				//write the playlist
				PrintWriter writer = response.getWriter();
				// set proper content type
				response.setContentType("application/x-mpegURL");
				// for the m3u8 content
				StringBuilder sb = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-ALLOW-CACHE:NO\n");
				// get segment duration in seconds
				long segmentDuration = service.getSegmentTimeLimit() / 1000;
				// create the heading
				sb.append(String.format("#EXT-X-TARGETDURATION:%s\n#EXT-X-MEDIA-SEQUENCE:%s\n", segmentDuration, segments[0].getIndex()));
				// loop through them
				for (int s = 0; s < segments.length; s++) {
					Segment segment = segments[s];
					// get sequence number
					int sequenceNumber = segment.getIndex();
					log.trace("Sequence number: {}", sequenceNumber);
					sb.append(String.format("#EXTINF:%.1f, segment\n%s_%s.ts\n", segment.getDuration(), streamName, sequenceNumber));
					// are we on the last segment?
					if (segment.isLast()) {
						log.debug("Last segment");
						sb.append("#EXT-X-ENDLIST\n");
						break;
					}
				}
				final String m3u8 = sb.toString();
				log.debug("Playlist for: {}\n{}", streamName, m3u8);
				writer.write(m3u8);
				writer.flush();
			} else {
				log.trace("Minimum segment count not yet reached, currently at: {}", count);
				response.setIntHeader("Retry-After", 60);
				response.sendError(503, "Not enough segments available for " + streamName);
			}
		} else {
			log.debug("Stream: {} is not available", streamName);
			response.sendError(404, "No playlist for " + streamName);
		}
	}

}
