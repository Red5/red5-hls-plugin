package org.red5.xuggler.reader;

import java.io.IOException;

public interface GenericReader extends Runnable {

	public void init() throws IOException;

	public void stop();

	public boolean isAudioEnabled();

	public boolean isVideoEnabled();

	//public void addListener(IMediaTool mediaListener);

	public void setInputUrl(String inputUrl);

	public String getInputUrl();

	public int getInputWidth();

	public int getInputHeight();

	public int getInputChannels();

	public int getInputSampleRate();
	
}
