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

/** Playback device using the low-level Mac audio. */
public class MacPlaybackDevice extends PlaybackDevice
{
	private final static int MACLATENCY=50;

	private int device;
	private boolean paused=false, playing=false;
	private PlayThread currentPlayer=null;
	private int sampleRate;
	private boolean stereo;


	/**
	 * @param format Audio format
	 * @throws MacAudioException If a device can't be allocated
	 */
	public MacPlaybackDevice(Format format) throws MacAudioException
	{
		super(format);
		device = MacAudio.playbackInit();
		sampleRate = MacAudio.playbackGetRate(device);
		stereo = MacAudio.playbackIsStereo(device);
	}

	@Override
	public void add(byte[] buffer, int bytes) throws MacAudioException
	{
		// Change rate of buffer
		byte[] converted = AudioUtil.convert(buffer,bytes,
			getFormat().isStereo(),stereo,getFormat().getSampleRate(),sampleRate);

		// Add data
		MacAudio.playbackAddData(device,converted);
	}

	@Override
	public void close()
	{
		try
		{
			stop();
			MacAudio.playbackClose(device);
		}
		catch(MacAudioException e)
		{
			System.err.println("[uk.ac.open.audio.MacPlaybackDevice] " +
				"Error when closing audio device");
			e.printStackTrace();
		}
	}

	@Override
	public synchronized boolean isPaused()
	{
		return paused;
	}

	@Override
	public synchronized boolean isPlaying()
	{
		return playing;
	}

	@Override
	public synchronized void pause() throws IllegalStateException, MacAudioException
	{
		if(!playing)
		{
			throw new IllegalStateException("Not currently playing");
		}
		if(paused)
		{
			throw new IllegalStateException("Already paused");
		}
		paused=true;
		MacAudio.playbackStop(device);
		notifyAll();
	}

	@Override
	public void play(Handler h, boolean waitForStart)
		throws IllegalArgumentException
	{
		synchronized(this)
		{
			if(currentPlayer!=null)
			{
				throw new IllegalStateException("Already playing");
			}
			currentPlayer=new PlayThread(h);
		}
		if(waitForStart)
		{
			synchronized(playerStartSynch)
			{
				try
				{
					playerStartSynch.wait();
				}
				catch (InterruptedException e)
				{
				}
			}
		}
	}

	@Override
	public synchronized void resume() throws IllegalStateException, MacAudioException
	{
		if(!paused)
		{
			throw new IllegalStateException("Not currently paused");
		}
		paused=false;
		notifyAll();
		MacAudio.playbackStart(device);
	}

	@Override
	public synchronized void start() throws IllegalStateException, MacAudioException
	{
		if(playing)
		{
			throw new IllegalStateException("Already playing");
		}
		MacAudio.playbackStart(device);
		playing=true;
	}

	@Override
	public synchronized void stop() throws MacAudioException
	{
		if(!playing)
		{
			return;
		}
		if(!paused)
		{
			MacAudio.playbackStop(device);
		}
		MacAudio.playbackReset(device);
		playing=false;
		paused=false;
		notifyAll();
	}

	@Override
	public synchronized void waitForEnd() throws IllegalStateException, MacAudioException
	{
		if(!playing)
		{
			throw new IllegalStateException("Not playing");
		}
		if(currentPlayer!=null)
		{
			if(currentPlayer==Thread.currentThread())
			{
				throw new IllegalStateException(
					"Cannot call waitForEnd from within player thread!");
			}
			while(currentPlayer!=null)
			{
				try
				{
					wait();
				}
				catch (InterruptedException e)
				{
				}
			}
		}
		else
		{
			drain();
		}
	}

	/**
	 * Wait for playback to come to an end
	 * @throws MacAudioException
	 */
	private synchronized void drain() throws MacAudioException
	{
		try
		{
			while(MacAudio.playbackGetUnplayedSize(device) > 0)
			{
				MacPlaybackDevice.this.wait(10);
			}
			// To allow stuff to get out of the hardware
			MacPlaybackDevice.this.wait(10);
		}
		catch(InterruptedException e)
		{
		}
	}

	private final Object playerStartSynch=new Object();

	private class PlayThread extends Thread
	{
		private final Handler h;

		private PlayThread(Handler h)
		{
			super("Playback thread");
			this.h=h;

			setPriority(MIN_PRIORITY);
			start();
		}

		@Override
		public void run()
		{
			try
			{
				int latencyBytes = ((sampleRate * (stereo?4:2)) * MACLATENCY) / 1000;

				boolean started=false;

				while(true)
				{
					synchronized(MacPlaybackDevice.this)
					{
						// If somebody has manually stopped playback, bail
						if(started && !playing)
						{
							return;
						}

						// Don't do anything while paused
						while(paused)
						{
							MacPlaybackDevice.this.wait();
							if(started && !playing)
							{
								return;
							}
						}

						// Wait until we need more audio (less than latency)
						while(MacAudio.playbackGetUnplayedSize(device) > latencyBytes)
						{
							MacPlaybackDevice.this.wait(10);
							if(started && !playing)
							{
								return;
							}
						}
					}

					// Get block from handler
					byte[] block=h.playbackBlock();

					// Add block to buffer
					if(block!=null)
					{
						add(block,block.length);
					}

					// If we've sent enough blocks, start playback
					if(!started && MacAudio.playbackGetUnplayedSize(device) > latencyBytes)
					{
						started=true;
						MacPlaybackDevice.this.start();
						synchronized(playerStartSynch)
						{
							playerStartSynch.notifyAll();
						}
					}

					// If block is last one, wait for end of playback then stop and end
					// this thread
					if(block==null)
					{
						drain();
						MacPlaybackDevice.this.stop();
						return;
					}
				}
			}
			catch(Throwable t)
			{
				h.playbackError(t);
			}
			finally
			{
				synchronized(MacPlaybackDevice.this)
				{
					currentPlayer=null;
					MacPlaybackDevice.this.notifyAll();
					h.playbackStopped();
				}
			}
		}
	}


}
