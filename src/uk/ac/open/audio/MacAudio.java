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


/**
 * JNI library to provide Mac-specific audio processing.
 * <p>
 * Note that this library is astonishingly simple. Both recording and playback
 * are in arbitrary quality (whatever the hardware prefers) - you will have to
 * resample in Java. There is no way to select a non-default audio device.
 * You must ensure that you call the 'close' methods.
 * <p>
 * It would be better if some features were implemented in native code, most
 * notably allowing sample rate conversion.
 */
public abstract class MacAudio
{
	/**
	 * Initialises recording device, but does not start recording.
	 * @return Device ID
	 * @throws MacAudioException
	 */
	native static int recordingInit() throws MacAudioException;

	/**
	 * @param device Device ID
	 * @return Sample rate of recording
	 * @throws MacAudioException
	 */
	native static int recordingGetRate(int device) throws MacAudioException;

	/**
	 * @param device Device ID
	 * @return True if recording is stereo, false for mono
	 * @throws MacAudioException
	 */
	native static boolean recordingIsStereo(int device) throws MacAudioException;

	/**
	 * Starts recording on the given device.
	 * @param device Device ID
	 * @throws MacAudioException
	 */
	native static void recordingStart(int device) throws MacAudioException;

	/**
	 * Obtains data that has been recorded.
	 * @param device Device ID
	 * @return Recorded data. If no data has been recorded since last call,
	 *   returns empty byte array.
	 * @throws MacAudioException
	 */
  native static byte[] recordingGetData(int device) throws MacAudioException;

	/**
	 * Stops recording on the given device.
	 * @param device Device ID
	 * @throws MacAudioException
	 */
	native static void recordingStop(int device) throws MacAudioException;

	/**
	 * Resets recording on the given device (throwing away any partial buffers).
	 * Should be called while stopped.
	 * @param device Device ID
	 * @throws MacAudioException
	 */
	native static void recordingReset(int device) throws MacAudioException;

	/**
	 * Closes recording device and releases resources.
	 * @param device Device ID
	 * @throws MacAudioException
	 */
	native static void recordingClose(int device) throws MacAudioException;

	/**
	 * Initialises playback device, but does not start playback.
	 * @return Device ID
	 * @throws MacAudioException
	 */
	native static int playbackInit() throws MacAudioException;

	/**
	 * @param device Device ID
	 * @return Sample rate of playback
	 * @throws MacAudioException
	 */
	native static int playbackGetRate(int device) throws MacAudioException;

	/**
	 * @param device Device ID
	 * @return True if playback is stereo, false for mono
	 * @throws MacAudioException
	 */
	native static boolean playbackIsStereo(int device) throws MacAudioException;

	/**
	 * Starts playback on the given device.
	 * @param device Device ID
	 * @throws MacAudioException
	 */
	native static void playbackStart(int device) throws MacAudioException;

	/**
	 * Adds data to the buffer for playback
	 * @param device Device ID
	 * @param data Data to play
	 * @throws MacAudioException
	 */
  native static void playbackAddData(int device,byte[] data) throws MacAudioException;

  /**
   * Obtains unplayed data size, i.e. amount of data already added for play.
   * This value updates as data is removed from the internal buffer and
   * passed over to the hardware.
   * @param device Device ID
   * @return Size of audio in buffer that hasn't yet been played; zero if we
   *   have run out of buffer, or are just about to
   * @throws MacAudioException
   */
  native static int playbackGetUnplayedSize(int device) throws MacAudioException;

	/**
	 * Stops playback on the given device.
	 * @param device Device ID
	 * @throws MacAudioException
	 */
	native static void playbackStop(int device) throws MacAudioException;

	/**
	 * Resets playback on the given device (throwing away any partial buffers).
	 * Should be called while stopped.
	 * @param device Device ID
	 * @throws MacAudioException
	 */
	native static void playbackReset(int device) throws MacAudioException;

	/**
	 * Closes playback device and releases resources.
	 * @param device Device ID
	 * @throws MacAudioException
	 */
	native static void playbackClose(int device) throws MacAudioException;
}
