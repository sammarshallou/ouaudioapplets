/*
Copyright 2009 The Open Universiimport javax.swing.JFrame;
s/projects/audioapplets/

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
package uk.ac.open.audiorecorder;

import java.io.IOException;

import javax.swing.JFrame;

import uk.ac.open.audio.RecordingDevice;

/**
 * Application version of audio recorder.
 */
public class AudioRecorderApp extends JFrame
{
	/** Constructs. */
	public AudioRecorderApp()
	{
		super("Audio recording test");
		getContentPane().add(new MainPanel());
		pack();
		setVisible(true);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
	}

	/**
	 * @param args Ignored
	 */
	public static void main(String[] args)
	{
		try
		{
			RecordingDevice.macInstall(null, null);
		}
		catch (IOException e)
		{
			System.err.println("Error installing OS X extension");
			e.printStackTrace();
		}
		new AudioRecorderApp();
	}
}
