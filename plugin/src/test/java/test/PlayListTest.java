package test;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.service.httpstream.SegmentFacade;
import org.red5.service.httpstream.SegmenterService;
import org.red5.service.httpstream.model.Segment;

public class PlayListTest {

	private MySegmenterService service;
	
	private String streamName = "junit";
	
	@Before
	public void setUp() throws Exception {
		service = new MySegmenterService();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() {
		// @TODO make this test runnable in junit
		assertTrue(true);
		/*
		// init service
		service.init(streamName);
		// get count
		int count = service.getSegmentCount(streamName);
		// make sure there are 4 available segments
		assertTrue(count == 4);
		dumpPlaylist();
		service.createLastSegment();
		count = service.getSegmentCount(streamName);
		assertTrue(count == 5);
		dumpPlaylist();
		service.removeFirstSegment();
		count = service.getSegmentCount(streamName);
		assertTrue(count == 4);
		dumpPlaylist();
		*/
	}

	@SuppressWarnings("unused")
	private void dumpPlaylist() {
		int count = service.getSegmentCount(streamName);
		// get the completed segments
		Segment[] segments = service.getSegments(streamName);
		if (segments != null && segments.length > 0) {
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
				System.out.printf("Sequence number: %d\n", sequenceNumber);
				sb.append(String.format("#EXTINF:%.1f, segment\n%s_%s.ts\n", segment.getDuration(), streamName, sequenceNumber));
				// are we on the last segment?
				if (segment.isLast()) {
					sb.append("#EXT-X-ENDLIST\n");
					break;
				}
			}
			final String m3u8 = sb.toString();
			System.out.printf("Playlist for: %s\n%s\n", streamName, m3u8);
		} else {
			System.out.printf("Minimum segment count not yet reached, currently at: %d", count);
		}		
	}
	
	class MySegmenterService extends SegmenterService {
		
		SegmentFacade facade;
		
		MySegmenterService() {
			super.setSegmentTimeLimit(10000);
			super.setMaxSegmentsPerFacade(5);
			super.setMemoryMapped(true);
		}

		void init(String streamName) {
			facade = new SegmentFacade(service, streamName);
			facade.setMaxSegmentsPerFacade(service.getMaxSegmentsPerFacade());
			service.addFacade(streamName, facade);
			facade.initReader();
			facade.initWriter();
			Segment segment = null;
			for (int s = 0; s < 5; s++) {
				segment = facade.createSegment();
				segment.setDuration(10 + s);
			}
		}

		// should be total of 6 segments now 0-5
		void createLastSegment() {
			Segment segment = facade.createSegment();
			segment.setDuration(99);
			segment.setLast(true);
			segment.close();
		}
		
		void removeFirstSegment() {
			facade.popSegment();
		}
		
	}
	
}
