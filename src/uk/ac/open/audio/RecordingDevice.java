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

import java.applet.Applet;
import java.io.*;
import java.net.*;

/** Base class for a recording device. */
public abstract class RecordingDevice
{
	/** Name of JNI file required by Mac version */
	public final static String MACDYNAMICLIBRARYFILE="osxaudio10";

	/** File of actual JNI file */
	private static File macDynamicLibraryFile=null;

	/** True if we've loaded the native library */
	private static boolean macInited=false;

	/**
	 * Interface for code that handles recordings.
	 */
	public interface Handler
	{
		/**
		 * Called whenever a block of data has been recorded. Blocks are usually
		 * about 100ms in length.
		 * @param data Data in 16-bit signed little-endian
		 * @param bytes Length of block in bytes
		 * @param level Estimation of max level in block (range 0-128)
		 * @param stopped True if this is the last block because the recording
		 *   has finished
		 */
		public void recordingBlock(byte[] data,int bytes,int level,boolean stopped);

		/**
		 * Called if an error occurs while recording.
		 * @param t Error
		 */
		public void recordingError(Throwable t);
	}

	/** Size of recording buffer (bytes) */
	public final static int TYPICALBUFFERSIZE=3200;

	/**
	 * @param h
	 * @throws AudioException
	 * @throws IllegalStateException
	 */
	public abstract void record(Handler h) throws AudioException, IllegalStateException;

	/**
	 * Pauses existing recording.
	 * @throws IllegalStateException If not recording
	 * @throws AudioException Other problem
	 */
	public abstract void pause() throws IllegalStateException, AudioException;

	/**
	 * Resumes paused recording.
	 * @throws IllegalStateException If not recording and paused
	 * @throws AudioException Other problem
	 */
	public abstract void resume() throws IllegalStateException, AudioException;

	/**
	 * @return True if recording is currently paused
	 */
	public abstract boolean isPaused();

	/**
	 * Stops recording. (Does nothing if already stopped.)
	 */
	public abstract void stop();

	/**
	 * @param forceCrossPlatform True if the Java Sound version should always
	 *   be used, even if there's a native one
	 * @return New RecordingDevice of appropriate nature for platform.
	 * @throws AudioException If anything goes wrong
	 */
	public static RecordingDevice construct(boolean forceCrossPlatform) throws AudioException
	{
		if(isMac() && macInited && !forceCrossPlatform)
		{
			try
			{
				return (RecordingDevice)Class.forName(
					"uk.ac.open.audio.MacRecordingDevice").newInstance();
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
			return new JavaSoundRecordingDevice();
		}
	}

	/** @return True if user is on Mac */
	public static boolean isMac()
	{
		// Code from http://developer.apple.com/technotes/tn2002/tn2110.html
		String lcOSName=System.getProperty("os.name").toLowerCase();
		return lcOSName.startsWith("mac os x");
	}

	/** @return True if the dynamic library is available */
	public static boolean usingMacLibrary()
	{
		return macInited;
	}

	/**
	 * Installs the dynamic library into user's Java extensions folder
	 * if it doesn't already exist.
	 * @param codeBase Codebase of applet or null if this is not an applet
	 *   (Required because applets can only share the same library if they
	 *   have the same codebase.)
	 * @param mainClass Main class of applet or null if this is not an applet
	 * @throws IOException If the library can't be installed
	 */
	public static synchronized void macInstall(URL codeBase,
		Class<? extends Applet> mainClass)
		throws IOException
	{
		if(!isMac())
		{
			return;
		}

		// Determine expected file location
		File parent=new File(System.getProperty("user.home"),"Library");
		File child1=new File(parent,"Java");
		File child2=new File(child1,"Extensions");
		String codeBasePart=codeBase==null ? "" : "."+
				(codeBase.hashCode() ^ mainClass.hashCode());
    macDynamicLibraryFile=new File(child2,"lib"+MACDYNAMICLIBRARYFILE+codeBasePart+".jnilib");

		if(!macDynamicLibraryFile.exists())
		{
			// Read the library (from classpath)
			URL u=RecordingDevice.class.getResource("lib"+
				RecordingDevice.MACDYNAMICLIBRARYFILE+".jnilib");
			if(u==null)
			{
				throw new IOException(
					"Unable to find OS X extension library");
			}
			URLConnection connection=u.openConnection();
			int size=connection.getContentLength();
			if(size<=0)
			{
				throw new IOException(
					"Error reading metadata for OS X extension library");
			}
			byte[] data=new byte[size];
			int pos=0;
			InputStream input=connection.getInputStream();
			while(pos<size)
			{
				int read=input.read(data,pos,size-pos);
				if(read==-1)
				{
					throw new IOException(
						"Unexpected EOF when unpacking OS X extension library");
				}
				pos+=read;
			}
			input.close();

			// Save the library
			if(!child1.exists())
			{
				if(!child1.mkdir())
				{
					throw new IOException(
						"Failed to create OS X Library/Java folder");
				}
			}
			if(!child2.exists())
			{
				if(!child2.mkdir())
				{
					throw new IOException(
						"Failed to create OS X Library/Java/Extensions folder");
				}
			}

			FileOutputStream output=new FileOutputStream(macDynamicLibraryFile);
			output.write(data);
			output.close();

			System.err.println("[uk.ac.open.audio.RecordingDevice] " +
				"The OS X extension library for audio has been " +
				"installed in "+macDynamicLibraryFile+". This file is required to work around bugs in " +
				"Apple's operating system.");
		}

		if(!macInited)
		{
			try
			{
				// System.load (not loadLibrary) is required. If you do loadLibrary,
				// it won't load a file that was put there within the same Java session.
				System.load(macDynamicLibraryFile.getAbsolutePath());
				macInited=true;
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
}