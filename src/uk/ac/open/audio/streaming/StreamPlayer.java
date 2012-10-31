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
package uk.ac.open.audio.streaming;

import java.awt.*;
import java.io.*;
import java.util.*;

import uk.ac.open.audio.*;

import static uk.ac.open.audio.streaming.StreamPlayer.State.*;

/**
 * Handles streaming, delaying as necessary, and playing audio; and replay from
 * downloaded version at later date.
 */
public class StreamPlayer
{
	/** Constant used when length of audio for playback is unknown. */
	public final static int UNKNOWN=-1;

	private final static int BUFFERSIZE=2048;
	private final static int AUDIOBLOCKBUFFER=5;
	private final static int MINSAMPLESBEFORESTART=4410; // 100ms of audio

	/**
	 * Minimum size of audio data blocks (blocks read from decoder are combined
	 * until they reach this size)
	 */
	private final static int MINDECODEDBLOCKSIZE=4608;

	/**
	 * If non-zero, limits downloading to this many bytes per second.
	 */
	private static int ARTIFICIALDELAY_BPS=0;

	/**
	 * If enabled, logs additional messages.
	 */
	private static boolean DETAILED_LOG = false;

	/**
	 * Sets download delay simulation.
	 * @param bps Simulated bytes per second
	 */
	public static void simulateDownloads(int bps)
	{
		ARTIFICIALDELAY_BPS=bps;
		if(bps==0)
		{
			log("Download delay disabled", false);
		}
		else
		{
			log("Simulating downloads at " + ARTIFICIALDELAY_BPS + " bytes/s", false);
		}
	}

	/**
	 * Displays an error message in standard format.
	 * @param message Message
	 * @param detail If true, only logs when detailed logging is on
	 */
	private static void log(String message, boolean detail)
	{
		if(detail && !DETAILED_LOG)
		{
			return;
		}
		System.err.println("[uk.ac.open.audio.streaming.StreamPlayer] " + message);
	}

	private final Class<? extends StreamableDecoder> decoderClass;

	private final LinkedList<DataBlock> data=new LinkedList<DataBlock>();
	private boolean downloadFinished=false,playFinished=false;
	private long lastBlock;
	private final LinkedList<AudioBlock> nextAudio=new LinkedList<AudioBlock>();
	private boolean isFromMemory;

	// Current statistics
	private final int length;
	private int totalSamplesDecoded,firstFrameBytes,firstFrameSamples;
	private double averageBytesPerSecondPlayback=0.0;
	private double recentBytesPerSecondDownload=0.0;

	private boolean close;

	/** States that the stream can be in. */
	public enum State
	{
		/** Downloading but not yet ready to play */
		WAITBEFOREPLAY,
		/** Downloaded enough that you should be able to start playing */
		READYTOPLAY,
		/** Completely downloaded */
		FULLYLOADED,
		/** Fully loaded, but audio buffer is empty */
		BUFFEREMPTY,
		/** Closed (no more playback possible) */
	  CLOSED
	}

	private State currentState = WAITBEFOREPLAY;

	private AudioDecoder audioDecoder;
	private Downloader downloader;
	private AnnoyingTimer annoyingTimer;

	/** A block of audio data. */
	public static class AudioBlock
	{
		private byte[] data;
		private double percentagePlayed;

		private AudioBlock(byte[] data, double percentagePlayed)
		{
			super();
			this.data=data;
			this.percentagePlayed=percentagePlayed;
		}

		/**
		 * @return Audio data
		 */
		public byte[] getData()
		{
			return data;
		}

		/**
		 * @return Percentage of data played so far, up to and including this
		 *   audio block
		 */
		public double getPercentagePlayed()
		{
			return percentagePlayed;
		}
	}


	private synchronized void setState(State newState)
	{
		currentState=newState;
		h.streamChangedState(currentState);
		log("Stream state: " + newState, true);
	}

	private final Handler h;

	/**
	 * Handler receives callbacks from the StreamPlayer when it changes state.
	 */
	public interface Handler
	{
		/**
		 * Called if there is an error. The player will not progress
		 * any further.
		 * @param t Error
		 */
		public void streamError(Throwable t);

		/**
		 * Called when the stream changes to a different state.
		 * @param s New state
		 */
		public void streamChangedState(State s);

		/**
		 * Called periodically to update statistics.
		 * @param download Bytes downloaded
		 * @param length Total length of download (or UNKNOWN)
		 * @param percentage Percentage downloaded. Will be -1 if length wasn't
		 *   set.
		 * @param downloadPerSecond Recent download per second (bytes)
		 * @param audioPerSecond Average playback per second (bytes)
		 * @param estimatedWait How long (in ms) the user is expected to need to wait
		 *   before the player enters READYTOPLAY state. Will be 0 if already in
		 *   that state.
		 */
		public void updateStats(int download,int length,double percentage,int downloadPerSecond,
				int audioPerSecond,int estimatedWait);
	}

	private class DataBlock
	{
		private final byte[] data;
		private final int ms;

		private DataBlock(byte[] data, int ms)
		{
			this.data=data;
			this.ms=ms;
		}

		byte[] getData()
		{
			return data;
		}

		int getTime()
		{
			return ms;
		}
	}

	/**
	 * Constructs the player and begins streaming.
	 * @param input
	 * @param length Expected length of input in bytes (may be UNKNOWN)
	 * @param decoderClass
	 * @param h
	 */
	public StreamPlayer(InputStream input, int length, Class<? extends StreamableDecoder> decoderClass, Handler h)
	{
		this.h=h;
		this.decoderClass=decoderClass;
		this.length=length;
		this.isFromMemory=input instanceof ByteArrayInputStream;

		lastBlock=System.currentTimeMillis();
		new Downloader(input);
		new AudioDecoder();
	}

	/** Rewinds ready to play the stream back from the start again. */
	public void rewind()
	{
		audioDecoder.close();
		new AudioDecoder();
	}

	private class Downloader extends Thread
	{
		private final InputStream input;

		Downloader(InputStream input)
		{
			super("Downloader thread");
			this.input=input;
			setPriority(NORM_PRIORITY-1);
			synchronized(StreamPlayer.this)
			{
				downloader=this;
			}
			start();
		}

		void close()
		{
			// Uses main object close flag
			while(downloader!=null)
			{
				try
				{
					synchronized(StreamPlayer.this)
					{
						StreamPlayer.this.wait(50);
					}
				}
				catch (InterruptedException e)
				{
				}
			}
		}

		@Override
		public void run()
		{
			try
			{
				while(true)
				{
					byte[] buffer=new byte[BUFFERSIZE];

					// Read full buffer
					int pos=0;
					do
					{
						int read=input.read(buffer,pos,buffer.length-pos);
						if(close)
						{
							return;
						}
						if(read==-1)
						{
							input.close();
							if(pos!=0)
							{
								// Partial block
								byte[] partial=new byte[pos];
								System.arraycopy(buffer,0,partial,0,pos);
								addBlock(partial,true);
							}
							else
							{
								addBlock(null, true);
							}
							return;
						}
						pos+=read;
					}
					while(pos!=buffer.length);

					// Add buffer to list
					addBlock(buffer,false);
				}
			}
			catch(Throwable t)
			{
				h.streamError(t);
			}
			finally
			{
				synchronized(StreamPlayer.this)
				{
					downloader=null;
				}
			}
		}
	}

	private static int getAppropriatePlaybackDelay(
		int bytesDownloaded,int bytesLength,
		int firstFrameBytes,int firstFrameSamples,
		int totalSamplesDecoded,
		double averageBPSPlayback, double recentBPSDownload)
	{
		int msDelay=999999;

		// Check if we already downloaded the whole file, if so we can start now
		if(bytesDownloaded >= bytesLength && totalSamplesDecoded > 0)
		{
			return 0;
		}

		// If we're downloading faster than playback, then we can potentially start
		// for that reason
		double speedFactor = recentBPSDownload / averageBPSPlayback;
		double estimatedSecondsDownloaded=bytesDownloaded/averageBPSPlayback;

		// Require 1 second buffer at 2x speed, etc
		if( speedFactor > 2.2)
		{
			msDelay=(int)(1000 * (1-estimatedSecondsDownloaded)/speedFactor);
		}
		else if(speedFactor > 1.7)
		{
			msDelay=(int)(1000 * (5-estimatedSecondsDownloaded)/speedFactor);
		}
		else if(speedFactor > 1.4)
		{
			msDelay=(int)(1000 * (10-estimatedSecondsDownloaded)/speedFactor);
		}
		else if(speedFactor > 1.1)
		{
			msDelay=(int)(1000 * (30-estimatedSecondsDownloaded)/speedFactor);
		}
		// If we aren't downloading faster than playback then we can still start
		// before the end
		else if(bytesLength!=UNKNOWN)
		{
			// Number of seconds of audio played and estimated for total file
			double secondsPlayed=(double)totalSamplesDecoded/44100;
			double secondsTotal=(bytesLength-firstFrameBytes)/averageBPSPlayback+
			  (double)firstFrameSamples/44100;

			// Desired factor
			double factor=1.3;

			// Number of seconds it will take until the ratio between 'time it will
			// take to playback samples we haven't played yet' and 'time it will take
			// to download the rest' is 1.3
			double secondsUntilFactor=(secondsTotal-secondsPlayed-
					(factor*(double)(bytesLength-bytesDownloaded)/recentBPSDownload))/-factor;

			msDelay=(int)(secondsUntilFactor*1000);
		}

		msDelay=Math.max(msDelay,0);
		return msDelay;
	}

	/**
	 * Called by the downloader when a new block becomes available.
	 * @param block Block
	 * @param finished True if this is the last block
	 */
	private synchronized void addBlock(byte[] block,boolean finished)
	{
		long now=System.currentTimeMillis();
		if(ARTIFICIALDELAY_BPS!=0 && !isFromMemory)
		{
			try
			{
				long until=now+(block.length*1000)/ARTIFICIALDELAY_BPS;

				while(now<until)
				{
					wait(until-now);
					now=System.currentTimeMillis();
				}

				// Special pause feature - place mouse in top right of screen to cause
				// network congestion
				while(MouseInfo.getPointerInfo().getLocation().equals(new Point(0,0)))
				{
					wait(500);
				}
			}
			catch (InterruptedException e)
			{
			}
		}
		if(block!=null) data.add(new DataBlock(block,(int)(now-lastBlock)));
		lastBlock=now;
		notifyAll();

		if(finished)
		{
			downloadFinished=true;
			notifyAll();
		}
		// Only send the 'fully loaded' state if we're already in READYTOPLAY
		if(finished && currentState==READYTOPLAY)
		{
			h.updateStats(data.size()-1*BUFFERSIZE+(block==null ? 0 : block.length),length,100.0,
					(int)recentBytesPerSecondDownload,(int)averageBytesPerSecondPlayback,
					0);
			setState(FULLYLOADED);
			return;
		}

		// Download percentage
		int bytesDownloaded = getDataSize();
		double percentageDownloaded=-1.0;

		if(length!=UNKNOWN)
		{
			percentageDownloaded=100.0*bytesDownloaded/length;
		}

		// Take average of last 20 block times to work out DL speed
		int count=0,totalTime=0;
		for(ListIterator<DataBlock> iterator=data.listIterator(data.size());
		  iterator.hasPrevious() && count<20; count++)
	  {
			totalTime+=iterator.previous().getTime();
	  }
		recentBytesPerSecondDownload=(count*BUFFERSIZE)*1000.0/totalTime;

		// Are we ready to start playing? Require at least two blocks, 100ms of
		// audio in the bank, and we must know the averages...
		int estimatedDelay=getAppropriatePlaybackDelay();
		boolean readyToPlay=estimatedDelay==0;

		h.updateStats(bytesDownloaded,length,percentageDownloaded,
				(int)recentBytesPerSecondDownload,(int)averageBytesPerSecondPlayback,
				estimatedDelay);

		// Note that it may yo-yo between these states, so a player shouldn't really
		// stop playing if state switches to WAITBEFOREPLAY - only stop when audio
		// runs out. But start again when READYTOPLAY hits again.
		if(currentState==WAITBEFOREPLAY && readyToPlay)
		{
			setState(READYTOPLAY);
			if(finished)
			{
				setState(FULLYLOADED);
			}
		}
		else if(currentState==READYTOPLAY && !readyToPlay)
		{
			setState(WAITBEFOREPLAY);
		}
		else if(currentState==WAITBEFOREPLAY && finished)
		{
			// Not ready to play yet, so can't send that state. Instead start a timer
			// and try again in 50ms.
			new AnnoyingTimer();
		}
	}

	/**
	 * Thread which is only there to delay playback attempts if the download
	 * completes so quickly that the decode thread hasn't done anything yet.
	 */
	private final class AnnoyingTimer extends ClosableThread
	{
		public AnnoyingTimer()
		{
			super("Annoying timer",StreamPlayer.this);
			synchronized(StreamPlayer.this)
			{
				annoyingTimer=this;
			}
			start();
		}

		@Override
		protected void runInner()
		{
			synchronized(StreamPlayer.this)
			{
				try
				{
					while(true)
					{
						try
						{
							StreamPlayer.this.wait(50);
						}
						catch (InterruptedException e)
						{
						}

						if(shouldClose()) return;

						int delay=getAppropriatePlaybackDelay();
						if(delay==0)
						{
							setState(READYTOPLAY);
							setState(FULLYLOADED);
							return;
						}
					}
				}
				finally
				{
					annoyingTimer=null;
				}
			}
		}
	}

	private int getDataSize()
	{
		if(data.size() >= 1)
		{
			// All blocks except last one are full blocks
			return (data.size() - 1) * BUFFERSIZE + data.getLast().getData().length;
		}
		else
		{
			return 0;
		}
	}

	/**
	 * @return 0 if ready to start playing now, otherwise UNKNOWN or a time
	 *   in milliseconds
	 */
	private int getAppropriatePlaybackDelay()
	{
		int estimatedDelay =
			(data.size()>=2 && totalSamplesDecoded>MINSAMPLESBEFORESTART &&
			averageBytesPerSecondPlayback!=0) ? 0 : UNKNOWN;
		if(estimatedDelay==0)
		{
			// Okay, we're ready to play but ONLY if the download speeds are okay
			estimatedDelay=getAppropriatePlaybackDelay(
				getDataSize(),length,firstFrameBytes,firstFrameSamples,
				totalSamplesDecoded,
				averageBytesPerSecondPlayback,recentBytesPerSecondDownload);
		}
		return estimatedDelay;
	}

	/**
	 * InputStream that reads from the data blocks available here.
	 */
	private class BlockInputStream extends InputStream
	{
		private int blockPos=0,innerPos=0, totalPos=0;

		@Override
		public int read() throws IOException
		{
			byte[] data=new byte[1];
			if(read(data,0,1)!=1)
				return -1;
			else
				return data[0];
		}

		@Override
		public int read(byte[] b) throws IOException
		{
			return read(b,0,b.length);
		}

		int getTotalPos()
		{
			return totalPos;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException
		{
			synchronized(StreamPlayer.this)
			{
				// Do we have any data at all? If not, wait until we do
				while(blockPos==data.size())
				{
					if(downloadFinished) return -1;
					try
					{
						StreamPlayer.this.wait();
					}
					catch (InterruptedException e)
					{
					}
				}

				// OK we have some data.
				int totalRead=0;
				while(blockPos<data.size())
				{
					DataBlock current=data.get(blockPos);

					// How much data is available in current block?
					int available=current.getData().length-innerPos;

					// We have more (or the same) available than needed
					if(available>=len)
					{
						System.arraycopy(current.getData(),innerPos,b,off,len);
						innerPos+=len;
						totalRead+=len;
						if(innerPos==current.getData().length)
						{
							innerPos=0;
							blockPos++;
						}
						totalPos+=totalRead;
						return totalRead;
					}

					// We have less available than needed
					System.arraycopy(current.getData(),innerPos,b,off,available);
					totalRead+=available;
					innerPos=0;
					off+=available;
					len-=available;
					blockPos++;

					// Repeat as long as the next block is available
				}

				// Keep track of total amount of data read
				totalPos+=totalRead;
				return totalRead;
			}
		}
	}

	/**
	 * @return True if {@link #getNextAudio} will return without throwing an
	 *   exception (either more audio available, or end of stream)
	 */
	public synchronized boolean hasNextAudio()
	{
		if(nextAudio.isEmpty() && currentState == FULLYLOADED && !playFinished)
		{
			setState(BUFFEREMPTY);
		}
		return playFinished || !nextAudio.isEmpty();
	}

	/**
	 * Retrieves the next audio block, if available. Does not block.
	 * @return Next audio block (44.4 kHz 16-bit little-endian) or null if
	 *   reached end of stream
	 * @throws AudioException If no more audio available
	 */
	public synchronized AudioBlock getNextAudio() throws AudioException
	{
		if(nextAudio.isEmpty())
		{
			if(playFinished)
			{
				return null;
			}
			throw new AudioException("No more audio available");
		}
		AudioBlock data=nextAudio.removeFirst();
		notifyAll();
		return data;
	}

	/** Decoder thread that decodes one audio frame ahead */
	private class AudioDecoder extends ClosableThread
	{
		private AudioDecoder()
		{
			super("Audio decoder",StreamPlayer.this);
			audioDecoder=this;
			playFinished=false;
			totalSamplesDecoded=0;
			firstFrameBytes=0;
			firstFrameSamples=0;
			nextAudio.clear();
			start();

		}

		@Override
		public void close()
		{
			synchronized(StreamPlayer.this)
			{
				super.close();
				audioDecoder=null;
			}
		}

		@Override
		public void runInner()
		{
			StreamableDecoder decoder;
			try
			{
				decoder=decoderClass.newInstance();
				BlockInputStream blockInput=new BlockInputStream();
				decoder.init(blockInput);

				while(true)
				{
					// Wait until we need to retrieve some audio
					synchronized(StreamPlayer.this)
					{
						while(nextAudio.size()>=AUDIOBLOCKBUFFER && !shouldClose())
						{
							if(currentState == BUFFEREMPTY)
							{
								setState(FULLYLOADED);
							}
							StreamPlayer.this.wait();
						}
						if(shouldClose())
						{
							blockInput.close();
							return;
						}
					}
					// Get some new audio
					byte[] audio = null;
					while(true)
					{
						try
						{
							byte[] newAudio = decoder.decode();

							if(newAudio == null)
							{
								break;
							}
							// Sometimes decoders return 0-length data blocks
							if(newAudio.length == 0)
							{
								continue;
							}
							if(audio == null)
							{
								audio = newAudio;
							}
							else
							{
								byte[] combinedAudio = new byte[audio.length + newAudio.length];
								System.arraycopy(audio, 0, combinedAudio, 0, audio.length);
								System.arraycopy(newAudio, 0, combinedAudio, audio.length, newAudio.length);
								audio = combinedAudio;
							}
							if(audio.length >= MINDECODEDBLOCKSIZE) break;
						}
						catch(Throwable t)
						{
							t.printStackTrace();
							playFinished=true;
							blockInput.close();
							return;
						}
					}

					int totalBytesDecoded=blockInput.getTotalPos();
					double percentagePlayed;
					if(length==UNKNOWN)
					{
						percentagePlayed=UNKNOWN;
					}
					else
					{
						percentagePlayed=100.0*totalBytesDecoded/length;
					}
					if(audio!=null) totalSamplesDecoded+=audio.length/4;

					// Tell anyone waiting that we have new audio
					synchronized(StreamPlayer.this)
					{
						if(firstFrameBytes==0)
						{
							firstFrameBytes=totalBytesDecoded;
							firstFrameSamples=totalSamplesDecoded;
						}
						else if(totalSamplesDecoded>firstFrameSamples)
						{
							averageBytesPerSecondPlayback=
								(totalBytesDecoded-firstFrameBytes) /
								((totalSamplesDecoded-firstFrameSamples) / 44100.0);
						}
						if(audio!=null)
						{
							nextAudio.addLast(new AudioBlock(audio,percentagePlayed));
						}
						StreamPlayer.this.notifyAll();
						if(audio==null)
						{
							if(currentState == BUFFEREMPTY)
							{
								setState(FULLYLOADED);
							}
							else if(currentState == WAITBEFOREPLAY)
							{
								setState(READYTOPLAY);
								setState(FULLYLOADED);
							}
							playFinished=true;
							blockInput.close();
							return;
						}
					}
				}
			}
			catch (Exception e)
			{
				h.streamError(e);
			}
		}
	}

	/** @return Current player state */
	public synchronized State getState()
	{
		return currentState;
	}

	/**
	 * Closes threads and aborts any download.
	 */
	public synchronized void close()
	{
		close=true;
		if(downloader!=null)
		{
			downloader.close();
		}
		if(annoyingTimer!=null)
		{
			annoyingTimer.close();
		}
		if(audioDecoder!=null)
		{
			audioDecoder.close();
		}
	}
}
