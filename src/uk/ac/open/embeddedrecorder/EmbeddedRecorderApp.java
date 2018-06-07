/*
git diff Copyright 2009 The Open University
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
package uk.ac.open.embeddedrecorder;

import java.awt.Color;
import java.net.URL;

import javax.swing.*;

/** Application for testing embedded audio recorder */
public class EmbeddedRecorderApp extends JFrame
{
	/**
	 * Constructs.
	 * @throws Exception If URLs are wrong
	 */
	public EmbeddedRecorderApp() throws Exception
	{
		super("Embedded recording test");
		EmbeddedRecorder recorder = new EmbeddedRecorder(
				new URL("http://sm449.vledev.open.ac.uk/moodle/ultest.php"),
				new URL("http://lyceum.open.ac.uk/temp/30s.mp3"),
				null,//new URL("http://lyceum.open.ac.uk/temp/12s.mp3"),
				new URL("http://lyceum.open.ac.uk/temp/20s.mp3"),
				null,//new URL("http://lyceum.open.ac.uk/temp/adpcm.wav"),
				EmbeddedRecorder.Order.LISTENFIRST,
				null,
				null,
				null,
				"Listen","Record","Play back","Model","Stop","Cancel",
				new Color(0,51,102),new Color(184,219,255),new Color(219,237,255),
				new Color(204,153,0),new Color(240,225,179),new Color(247,240,217),
				new Color(219,237,255), false);
		getContentPane().add(recorder);
		pack();
		setVisible(true);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
	}

	/**
	 * @param args Ignored
	 * @throws Exception Also ignored
	 */
	public static void main(String[] args) throws Exception
	{
		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		new EmbeddedRecorderApp();
	}
}
