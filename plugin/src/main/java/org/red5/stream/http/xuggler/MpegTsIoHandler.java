package org.red5.stream.http.xuggler;

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

import java.nio.ByteBuffer;

import org.red5.logging.Red5LoggerFactory;
import org.red5.service.httpstream.SegmentFacade;
import org.red5.service.httpstream.model.Segment;
import org.red5.xuggler.Message;
import org.red5.xuggler.handler.IEventIOHandler;
import org.slf4j.Logger;

public class MpegTsIoHandler implements IEventIOHandler {

	protected Logger log = Red5LoggerFactory.getLogger(this.getClass());

	private final SegmentFacade facade;

	// store the latest PAT data
	private byte[] patData;

	// store the latest PMT data
	private byte[] pmtData;

	// store the latest SPS/PPS
	@SuppressWarnings("unused")
	private ByteBuffer spData = null;

	public MpegTsIoHandler(String url, SegmentFacade facade) {
		log.trace("ctor url: {} facade: {}", url, facade);
		this.facade = facade;
	}

	public Message read() throws InterruptedException {
		return null;
	}

	public int write(Message message) throws InterruptedException {
		log.debug("write");
		int written = 0;
		// get current segment
		Segment segment = facade.getSegment();
		// get message data
		ByteBuffer data = message.getData();
		if (data != null) {
			final Message.Type type = message.getType();
			log.trace("[{}] Writing {} byte {} message to segment", facade, data.limit(), type);
			switch (type) {
				case CONFIG_PAT:
					// keep track of the latest PAT data
					if (patData == null) {
						patData = new byte[188];
					}
					data.mark();
					data.get(patData);
					data.reset();

					break;
				case CONFIG_PMT:
					// keep track of the latest PMT data
					if (pmtData == null) {
						pmtData = new byte[188];
					}
					data.mark();
					data.get(pmtData);
					data.reset();

					break;
				case CONFIG:

					break;
				case HEADER:

					break;
				case END_STREAM:
					log.debug("[{}] End of stream", facade);
					if (segment != null) {
						segment.setLast(true);
						segment.close();
					}
					break;
				default:
					if (segment != null) {
						// first is pat
						if (!segment.isPatWritten()) {
							if (patData != null) {
								// write the PAT
								if (segment.write(ByteBuffer.wrap(patData)) == patData.length) {
									segment.setPatWritten(true);
									// now pmt
									if (!segment.isPmtWritten()) {
										if (pmtData != null) {
											// write the PMT
											if (segment.write(ByteBuffer.wrap(pmtData)) == pmtData.length) {
												segment.setPmtWritten(true);
											} else {
												log.warn("[{}] Write of PMT to segment failed", facade);
											}
										} else {
											log.warn("[{}] Could not write null PMT", facade);
										}
									}
								} else {
									log.warn("[{}] Write of PAT to segment failed", facade);
								}
							} else {
								log.warn("[{}] Could not write null PAT", facade);
							}
						}
						// write the data to the segment				
						if ((written = segment.write(data)) > 0) {
							log.trace("[{}] Write to segment {} success", facade, segment.getIndex());
						} else {
							log.warn("[{}] Write to segment {} failed", facade, segment.getIndex());
						}
					} else {
						log.debug("[{}] Segment not available", facade);
					}
			}
		}
		return written;
	}

	public boolean hasPAT() {
		return patData != null;
	}

	public void setPAT(byte[] pat) {
		this.patData = pat;
	}

	public boolean hasPMT() {
		return pmtData != null;
	}

	public void setPMT(byte[] pmt) {
		this.pmtData = pmt;
	}

}
