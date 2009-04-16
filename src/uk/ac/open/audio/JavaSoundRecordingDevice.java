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

import javax.sound.sampled.*;

/**
 * Provides a wrapper around Java Sound so that we can record audio in a
 * reliable cross-platform manner (without crashing).
 */
class JavaSoundRecordingDevice extends RecordingDevice
{
	private TargetDataLine recordingLine;
	private RecordingThread currentlyRecording=null;
	private int javaSoundBuffer;

	private boolean paused=false;

	/**
	 * Sets up a 16-bit mono 16 kHz recording device with the given sample rate.
	 * @throws AudioException If there's a problem initialising audio
	 */
	JavaSoundRecordingDevice() throws AudioException
	{
		AudioFormat format=new AudioFormat(16000.0f,16,1,true,false);
		javaSoundBuffer=16000;

		// Initiate recording and playback
		try
		{
			recordingLine=AudioSystem.getTargetDataLine(format);
		}
		catch (Exception e)
		{
			throw new AudioException(e);
		}
	}

	/**
	 * @param h Handler that receives callbacks when data is available
	 * @throws AudioException If we can't get a line
	 * @throws IllegalStateException If already recording
	 */
	@Override
	public synchronized void record(RecordingDevice.Handler h)
		throws AudioException, IllegalStateException
	{
		if(currentlyRecording!=null)
		{
			throw new IllegalStateException("This device is already recording");
		}
		try
		{
		  recordingLine.open(recordingLine.getFormat(),javaSoundBuffer);
		  recordingLine.start();
		  currentlyRecording=new RecordingThread(h);
		}
		catch(LineUnavailableException e)
		{
			throw new AudioException(e);
		}
	}

	@Override
	public synchronized void pause() throws IllegalStateException
	{
		if(currentlyRecording==null)
		{
			throw new IllegalStateException("Not currently recording");
		}
		currentlyRecording.pauseRecording();
		paused=true;
	}

	@Override
	public synchronized void resume() throws IllegalStateException
	{
		if(!paused)
		{
			throw new IllegalStateException("Not currently paused");
		}
		currentlyRecording.resumeRecording();
		paused=false;
	}

	@Override
	public synchronized boolean isPaused()
	{
		return paused;
	}

	private class RecordingThread extends Thread
	{
		private RecordingDevice.Handler h;
		private boolean stopRequested=false;

		RecordingThread(RecordingDevice.Handler h)
		{
			super("Audio recording thread");
			setPriority(MIN_PRIORITY);
			this.h=h;
			start();
		}

		@Override
		public void run()
		{
			try
			{
		    // Buffer is 1/10th second
		    byte[] buffer;
	    	buffer=new byte[RecordingDevice.TYPICALBUFFERSIZE];
		    boolean stopped;
		    do
		    {
	        int maxLevel=0,bytesRead;

		    	synchronized(JavaSoundRecordingDevice.this)
		    	{
		        // Read recorded data
		        bytesRead=recordingLine.read(buffer,0,buffer.length);
		        stopped=bytesRead==0 && stopRequested;

		        // Do level analysis based on high bytes
		        for(int i=1;i<bytesRead;i+=2)
		        {
		        	int level=Math.abs((int)buffer[i]);
		        	maxLevel=Math.max(level,maxLevel);
		        }
		    	}

		    	// On OS X 10.4, when the line is stopped, 'read' returns immediately
		    	// with zero bytes. There appears no way to determine that the line
		    	// is not running on that platform. Consequently we poll 'read'
		    	// in this event, because the line is paused, and might start again.
		    	if(bytesRead==0 && !stopRequested)
		    	{
		    		Thread.sleep(100);
		    		continue;
		    	}

		    	h.recordingBlock(buffer, bytesRead, maxLevel, stopped);
				}
		    while(!stopped);
			}
			catch(Throwable t)
			{
				h.recordingError(t);
			}
			finally
			{
				synchronized(JavaSoundRecordingDevice.this)
				{
					recordingLine.close();
					currentlyRecording=null;
					JavaSoundRecordingDevice.this.notifyAll();
				}
			}
	  }

		private void stopRecording()
		{
			stopRequested=true;
			recordingLine.stop();
			recordingLine.drain();
		}

		private void pauseRecording()
		{
			recordingLine.stop();
		}

		private void resumeRecording()
		{
			recordingLine.start();
		}
	}

	@Override
	public synchronized void stop()
	{
		if(currentlyRecording==null) return;
		currentlyRecording.stopRecording();
		while(currentlyRecording!=null)
		{
			try
			{
				wait();
			}
			catch (InterruptedException e)
			{
			}
		}
		paused=false;
	}
}
