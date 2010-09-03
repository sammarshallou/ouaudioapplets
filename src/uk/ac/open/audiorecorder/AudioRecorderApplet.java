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
package uk.ac.open.audiorecorder;

import java.io.IOException;

import javax.swing.*;

import uk.ac.open.audio.RecordingDevice;
import uk.ac.open.tabapplet.*;

/** Applet version of audio recording tool. */
public class AudioRecorderApplet extends TabApplet
{
	private MainPanel main;

	@Override
	protected JComponent getInner()
	{
		if(main == null)
		{
			main = new MainPanel();
		}
		return main;
	}

	@Override
	public void init()
	{
		super.init();
		try
		{
			UIManager.setLookAndFeel(
				UIManager.getSystemLookAndFeelClassName());
			SwingUtilities.updateComponentTreeUI(this);
			RecordingDevice.macInstall(getCodeBase(),AudioRecorderApplet.class);
		}
		catch (IOException e)
		{
			System.err.println("Error installing OS X extension");
			e.printStackTrace();
		}
		catch(Exception e)
		{
			System.err.println("Error setting L&F");
			e.printStackTrace();
		}
	}

	@Override
	protected boolean ignoreFocusChange()
	{
		return main.ignoreFocusChange();
	}

	@Override
	protected TabAppletFocuser getFocuser()
	{
		return main;
	}
}
