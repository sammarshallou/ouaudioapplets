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
 * Wrapper around Java Sound so we can play back audio in a reliable
 * cross-platform manner.
 */
class JavaSoundPlaybackDevice extends PlaybackDevice
{
	private final static int PLAYLATENCYMS;
	static
	{
		String l=System.getProperty("latency");
		if(l==null)
		{
			PLAYLATENCYMS=50;
		}
		else
		{
			PLAYLATENCYMS=Integer.parseInt(l);
			System.err.println("Playback latency set to "+PLAYLATENCYMS);
		}
	}

	private SourceDataLine playbackLine;

	private boolean paused=false,playing=false;

	private PlayThread currentPlayer=null;

	/**
	 * Initialises device.
	 * @param f Audio format
	 * @throws AudioException
	 */
	public JavaSoundPlaybackDevice(Format f) throws AudioException
	{
		super(f);
		AudioFormat af=f.getAudioFormat();

		// Initiate recording and playback
		try
		{
	    playbackLine=AudioSystem.getSourceDataLine(af);
		}
		catch (Exception e)
		{
			throw new AudioException(e);
		}
		try
		{
			playbackLine.open(af, f.getJavaSoundBufferSize());
		}
		catch (LineUnavailableException e)
		{
			throw new AudioException(e);
		}
	}

	@Override
	public synchronized void add(byte[] buffer,int bytes)
	{
		playbackLine.write(buffer, 0, bytes);
	}

	@Override
	public void play(Handler h, boolean waitForStart) throws IllegalArgumentException
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
			boolean started = false;
			long sentFrames=0;
			try
			{
				while(true)
				{
					// If somebody has manually stopped playback, bail
					if(started && !playing)
					{
						return;
					}

					if(paused)
					{
						// Get time pause begins
						playbackLine.stop();
						while(paused)
						{
							// If somebody has paused playback, pause this thread too
							synchronized(JavaSoundPlaybackDevice.this)
							{
								try
								{
									JavaSoundPlaybackDevice.this.wait();
								}
								catch (InterruptedException e)
								{
								}
							}
						}

						// Pause over, restart
						playbackLine.start();
					}

					// Get block from handler
					byte[] block=h.playbackBlock();
  				// 32 bytes of the block = 2 * 16 samples = 1ms @ 16 kHz
					int blockTime=block==null ? 0 : getFormat().convertBytesToMs(block.length);

					// Add block to buffer
					if(block!=null)
					{
						add(block,block.length);
						sentFrames+=block.length/getFormat().getBytesPerFrame();
					}

					// If we've sent enough blocks, start playback
					if(!started && (getFormat().convertFramesToMs((int)sentFrames) >
						blockTime+PLAYLATENCYMS || block==null))
					{
						started = true;
						JavaSoundPlaybackDevice.this.start();
						synchronized(playerStartSynch)
						{
							playerStartSynch.notifyAll();
						}
					}

					// If block is last one, wait for end of playback then stop and end
					// this thread
					if(block==null)
					{
						playbackLine.drain();
						JavaSoundPlaybackDevice.this.stop();
						return;
					}

					// Wait (don't generate more blocks yet) if we've sent enough data
					// that after the current block runs out we'd still have PLAYLATENCYMS
					// left.
					long spareTime = getFormat().convertFramesToMs(
						(int)(sentFrames - playbackLine.getLongFramePosition()));
					long delay = spareTime - (blockTime+PLAYLATENCYMS);

					if(delay>10) // Extra limit here is a minimum 'wait' time
					{
						try
						{
							synchronized(JavaSoundPlaybackDevice.this)
							{
								JavaSoundPlaybackDevice.this.wait(delay);
							}
						}
						catch (InterruptedException e)
						{
						}
					}
				}
			}
			finally
			{
				synchronized(JavaSoundPlaybackDevice.this)
				{
					currentPlayer=null;
					JavaSoundPlaybackDevice.this.notifyAll();
					h.playbackStopped();
				}
			}
		}
	}

	@Override
	public synchronized void start() throws IllegalStateException
	{
		if(playing)
		{
			throw new IllegalStateException("Already playing");
		}
		playbackLine.start();
		playing=true;
	}

	@Override
	public synchronized void stop()
	{
		if(!playing)
		{
			return;
		}
		playbackLine.stop();
		playbackLine.flush();
		playing=false;
		paused=false;
		notifyAll();
	}

	@Override
	public synchronized void waitForEnd() throws IllegalStateException
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
			playbackLine.drain();
		}
	}

	@Override
	public synchronized void close()
	{
		stop();
		playbackLine.close();
	}

	@Override
	public synchronized void pause() throws IllegalStateException
	{
		if(!playing)
		{
			throw new IllegalStateException("Not currently playing");
		}
		paused=true;
		notifyAll();
	}

	@Override
	public synchronized void resume() throws IllegalStateException
	{
		if(!paused)
		{
			throw new IllegalStateException("Not currently paused");
		}
		paused=false;
		notifyAll();
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
}
