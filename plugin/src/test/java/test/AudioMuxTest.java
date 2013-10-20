package test;

import java.io.*;

import org.junit.Test;
import org.red5.stream.util.AudioMux;

public class AudioMuxTest {

	@Test
	public void test() throws IOException {
	
		File file1 = new File("1.pcm");
		File file2 = new File("2.pcm");
		
		if (!file1.exists() || !file2.exists()) {
			System.err.println("This test will not run without the two PCM files (1.pcm and 2.pcm)");
			return;
		}
	
		DataInputStream d1 = new DataInputStream(new FileInputStream(file1));
		DataInputStream d2 = new DataInputStream(new FileInputStream(file2));

		DataOutputStream ds = new DataOutputStream(new FileOutputStream("test.pcm"));

		AudioMux m = new AudioMux(44100, false);
		m.addTrack(2);
		m.addTrack(2);

		int size = 10 * 1024 * 1024;
		for (int s = 0; s < size; s++) {
			short[] s1 = new short[64];
			short[] s2 = new short[64];
			for (int ss = 0; ss < 64; ss++) {
				s1[ss] = d1.readShort();
				s1[ss] = (short) (((s1[ss] & 0xffff) >> 8) | ((s1[ss] & 0xffff) << 8));
				s2[ss] = d2.readShort();
				s2[ss] = (short) (((s2[ss] & 0xffff) >> 8) | ((s2[ss] & 0xffff) << 8));
			}
			m.pushData("0", s1);
			m.pushData("1", s2);

			short result[] = m.popMuxedData(-1);

			for (int i = 0; i < result.length; i++) {
				result[i] = (short) (((result[i] & 0xffff) >> 8) | ((result[i] & 0xffff) << 8));
				ds.writeShort(result[i]);
			}
			ds.flush();
			if (s % 100 == 0) {
				System.out.println("Sample: " + s);
			}
		}
		ds.close();
	
	}
}