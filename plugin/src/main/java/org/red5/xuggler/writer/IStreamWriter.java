package org.red5.xuggler.writer;

import java.util.concurrent.TimeUnit;

import org.red5.service.httpstream.SegmentFacade;

import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.ISimpleMediaFile;
import com.xuggle.xuggler.IVideoPicture;

public interface IStreamWriter {

	public abstract int addAudioStream(int streamId, ICodec codec, int channelCount, int sampleRate);

	public abstract int addVideoStream(int streamId, ICodec codec, IRational frameRate, int width, int height);

	public abstract void encodeAudio(short[] samples, long timeStamp, TimeUnit timeUnit);

	public abstract void encodeVideo(IVideoPicture picture);

	public abstract void setup(SegmentFacade facade, ISimpleMediaFile outputStreamInfo);

	public abstract void open();

	public abstract void close();

	public abstract void start();
	
}
