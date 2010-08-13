/*
Copyright 2009 The Open University
http://www.open.ac.uk/lts/projects/audioapplets/

This file is part of the "Open University audio applets" project.

The "Open University audio applets" project is free software: you can
redistribute it and/or modify it under the terms of the GNU General Public
License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

The "Open University audio applets" project is distributed in the hope that it
will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with the "Open University audio applets" project.
If not, see <http://www.gnu.org/licenses/>.
*/
package uk.ac.open.audio;

/** Recording device using Mac native code. */
public class MacRecordingDevice extends RecordingDevice
{
	private final static int NONE=-1;

	private int device;
	private RecordingThread currentlyRecording=null;
	private int sampleRate;
	private boolean stereo;

	private boolean paused;

	/**
	 * @throws AudioException Any error in setup
	 */
	MacRecordingDevice() throws AudioException
	{
		device=MacAudio.recordingInit();
		sampleRate = MacAudio.recordingGetRate(device);
		stereo = MacAudio.recordingIsStereo(device);
	}

	@Override
	public synchronized boolean isPaused()
	{
		return paused;
	}

	@Override
	public synchronized void pause() throws AudioException, IllegalStateException
	{
		if(currentlyRecording==null)
		{
			throw new IllegalStateException("Not currently recording");
		}
		MacAudio.recordingStop(device);
		paused=true;
	}

	@Override
	public synchronized void record(Handler h) throws AudioException, IllegalStateException
	{
		if(currentlyRecording!=null)
		{
			throw new IllegalStateException("This device is already recording");
		}
		if(device==NONE)
		{
			device=MacAudio.recordingInit();
		}
		currentlyRecording=new RecordingThread(h);
		MacAudio.recordingStart(device);
	}

	@Override
	public synchronized void resume() throws AudioException, IllegalStateException
	{
		if(!paused)
		{
			throw new IllegalStateException("Not currently paused");
		}
		MacAudio.recordingStart(device);
		paused=false;
	}

	@Override
	public synchronized void stop()
	{
		try
		{
			MacAudio.recordingStop(device);
			MacAudio.recordingClose(device);
		}
		catch(MacAudioException e)
		{
			// Ignore exceptions when closing
			e.printStackTrace();
		}
		currentlyRecording.close();
		device=NONE;
		paused=false;
	}

	private class RecordingThread extends ClosableThread
	{
		private RecordingDevice.Handler h;

		RecordingThread(RecordingDevice.Handler h)
		{
			super("Audio recording thread",MacRecordingDevice.this);
			setPriority(MIN_PRIORITY);
			this.h=h;
			start();
		}

		@Override
		protected void runInner()
		{
			try
			{
				while(true)
				{
		    	synchronized(MacRecordingDevice.this)
		    	{
		    		MacRecordingDevice.this.wait(95);
			    	if(shouldClose())
			    	{
			    		currentlyRecording=null;
			    		return;
			    	}

			    	// Get data
			    	byte[] audio=MacAudio.recordingGetData(device);
			    	if(audio.length==0)
			    	{
			    		continue;
			    	}

			    	int maxLevel=0;

			    	// Convert format
			    	byte[] output = AudioUtil.convert(audio, audio.length, stereo, false,
			    		sampleRate, 16000);

		        // Do level analysis based on high bytes
		        for(int i=1;i<output.length;i+=2)
		        {
		        	int level=Math.abs((int)output[i]);
		        	maxLevel=Math.max(level,maxLevel);
		        }
			    	h.recordingBlock(output, output.length, maxLevel, false);
		    	}
				}
			}
			catch(Throwable t)
			{
				h.recordingError(t);
			}
			finally
			{
				h.recordingBlock(new byte[0],0,0,true);
			}
	  }
	}


}
