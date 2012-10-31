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

import javax.sound.sampled.AudioFormat;

/** Base class for simply playback device */
public abstract class PlaybackDevice
{
	/** Audio format for playback. */
	public enum Format
	{
		/** 16 kHz mono little-endian */
		MONO_16KHZ(new AudioFormat(16000.0f,16,1,true,false)),
		/** 44.1 kHz mono little-endian */
		MONO_44KHZ(new AudioFormat(44100.0f,16,1,true,false)),
		/** 44.1 kHz stereo little-endian */
		STEREO_44KHZ(new AudioFormat(44100.0f,16,2,true,false));

		private AudioFormat audio;

		Format(AudioFormat audio)
		{
			this.audio=audio;
		}

		/** @return JavaSound format object */
		public AudioFormat getAudioFormat()
		{
			return audio;
		}

		/** @return Sample rate in Hz */
		public int getSampleRate()
		{
			return (int)audio.getSampleRate();
		}

		/** @return True if format is stereo */
		public boolean isStereo()
		{
			return audio.getChannels() == 2;
		}

		/**
		 * Returns the approximate number of milliseconds for the given number of
		 * bytes.
		 * @param bytes Byte count
		 * @return Millisecond estimate
		 */
		public int convertBytesToMs(int bytes)
		{
			int bytesPerSecond=audio.getChannels()*(int)audio.getSampleRate()*audio.getSampleSizeInBits()/8;
			return (bytes * 1000)/bytesPerSecond;
		}

		/** @return Suitable size for JavaSound buffers (1.5 seconds) */
		public int getJavaSoundBufferSize()
		{
			// 1.5 seconds
			return 3 * ((audio.getChannels() * (int)audio.getSampleRate() *
				audio.getSampleSizeInBits()) / 16);
		}

		/** @return Number of bytes per frame */
		public int getBytesPerFrame()
		{
			return audio.getFrameSize();
		}

		/**
		 * @param frames Number of frames
		 * @return Millisecond estimate
		 */
		public long convertFramesToMs(int frames)
		{
			int framesPerSecond=(int)audio.getFrameRate();
			return (frames*1000) / framesPerSecond;
		}
	}

	/**
	 * If you want to play things back without setting up your own playback
	 * thread, this interface may be useful.
	 */
	public interface Handler
	{
		/**
		 * Called when a new data block is required because the sound system is
		 * about to run out.
		 * @return Full data buffer, or null if playback has reached the end.
		 */
		public byte[] playbackBlock();

		/**
		 * Called when playback stops because the system ran out of blocks.
		 */
		public void playbackStopped();

		/**
		 * Called if an error occurs during playback. If the error causes playback
		 * to stop, playbackStopped will also be called.
		 * @param t Error
		 */
		public void playbackError(Throwable t);
	}

	private Format format;

	/**
	 * Initialises device.
	 * @param format Audio format
	 */
	public PlaybackDevice(Format format)
	{
		this.format=format;
	}

	/** @return Audio format */
	public Format getFormat()
	{
		return format;
	}

	/**
	 * Adds a buffer to playback. Note that this method may block or throw an
	 * exception if the buffer is full.
	 * @param buffer Buffer to add
	 * @param bytes Number of bytes (from start of buffer) to use
	 * @throws AudioException If buffer is full (but some implementations might
	 *   block instead, so don't over-fill the buffer either way)
	 */
	public abstract void add(byte[] buffer,int bytes) throws AudioException;

	/**
	 * Plays data, using the provided handler to get the actual data when
	 * required.
	 * @param h Handler that provides data to play
	 * @param waitForStart If true, waits for the player to actually start
	 *   (note that if you don't do this there are issues when calling pause)
	 * @throws IllegalArgumentException If already playing
	 */
	public abstract void play(Handler h, boolean waitForStart)
		throws IllegalArgumentException;

	/**
	 * Starts playback.
	 * @throws IllegalStateException If already playing
	 * @throws AudioException Any problem with the audio system
	 */
	public abstract void start() throws AudioException, IllegalStateException;

	/**
	 * Stops playback. Does nothing if playback is not in progress.
	 * @throws AudioException Any problem with the audio system
	 */
	public abstract void stop() throws AudioException;

	/**
	 * Waits until existing blocks finish playing back. Behaves appropriately if
	 * the automatic player is operating.
	 * @throws IllegalStateException If not playing
	 * @throws AudioException Any problem with the audio system
	 */
	public abstract void waitForEnd() throws AudioException, IllegalStateException;

	/**
	 * Closes line, freeing resources.
	 */
	public abstract void close();

	/**
	 * Pauses existing playback.
	 * @throws IllegalStateException If not playing
	 * @throws AudioException Any problem with the audio system
	 */
	public abstract void pause() throws IllegalStateException, AudioException;

	/**
	 * Resumes paused playback.
	 * @throws IllegalStateException If not playing and paused
	 * @throws AudioException Any problem with the audio system
	 */
	public abstract void resume() throws IllegalStateException, AudioException;

	/** @return True if player is currently paused */
	public abstract boolean isPaused();

	/** @return True if player is currently playing */
	public abstract boolean isPlaying();

	/**
	 * @param format Audio format
	 * @param forceCrossPlatform True if the Java Sound version should always
	 *   be used, even if there's a native one
	 * @return New PlaybackDevice of appropriate nature for platform.
	 * @throws AudioException If anything goes wrong
	 */
	public static PlaybackDevice construct(Format format, boolean forceCrossPlatform) throws AudioException
	{
		if(RecordingDevice.usingMacLibrary() && !forceCrossPlatform)
		{
			System.err.println("[uk.ac.open.audio.PlaybackDevice] Using Mac playback");
			try
			{
				return (PlaybackDevice)Class.forName(
					"uk.ac.open.audio.MacPlaybackDevice").getConstructor(
						new Class[] {Format.class}).newInstance(
						new Object[] {format});
			}
			catch (Exception e)
			{
				if(e instanceof AudioException)
				{
					throw (AudioException)e;
				}
				else
				{
					throw new AudioException(e);
				}
			}
		}
		else
		{
			System.err.println("[uk.ac.open.audio.PlaybackDevice] Using Java playback");
			return new JavaSoundPlaybackDevice(format);
		}
	}

}