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
package uk.ac.open.embeddedrecorder;

import java.awt.Color;
import java.io.IOException;
import java.net.*;
import java.util.regex.*;

import javax.swing.*;

import uk.ac.open.audio.RecordingDevice;
import uk.ac.open.embeddedrecorder.EmbeddedRecorder.Order;
import uk.ac.open.tabapplet.*;

/** Applet version of embedded recorder. */
public class EmbeddedRecorderApplet extends TabApplet
{
	private final static int STR_LISTEN = 0, STR_RECORD = 1, STR_PLAYBACK = 2,
		STR_MODEL = 3, STR_STOP = 4, STR_CANCEL = 5;

	private final static int COL_DARK = 0, COL_LIGHT = 1, COL_FAINT = 2,
		COL_ALTDARK = 3, COL_ALTLIGHT = 4, COL_ALTFAINT = 5, COL_CORNERS = 6;

	private final static Pattern COLOUR = Pattern.compile(
		"#([0-9A-F]{2})([0-9A-F]{2})([0-9A-F]{2})");

	private EmbeddedRecorder recorder;

	/** @return Recorder for this applet */
	EmbeddedRecorder getRecorder()
	{
		return recorder;
	}

	@Override
	protected JComponent getInner()
	{
		return recorder;
	}

	@Override
	public void init()
	{
		super.init();
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		// Get strings
		String[] strings = getRequiredParameter("strings").split(",\\s*");
		if (strings.length != 6)
		{
			throw new IllegalArgumentException("Incorrect 'strings' parameter. " +
				"Must include 6 comma-separated strings. Example \"Listen, Record, " +
				"Play back, Model, Stop, Cancel\".");
		}

		// Get colours
		String[] colourStrings = getRequiredParameter("colours").split(",\\s*");
		if (colourStrings.length != 7)
		{
			throw new IllegalArgumentException("Incorrect 'colours' parameter. " +
				"Must include 7 comma-separated colours. Example \"#003366, " +
				"#B8DBFF,#DBEDFF,#CC9900,#F0E1B3,#F7F0D9,#DBEDFF\".");
		}
		Color[] colours = new Color[colourStrings.length];
		for(int i = 0; i < colours.length; i++)
		{
			Matcher m=COLOUR.matcher(colourStrings[i]);
			if(!m.matches())
			{
				throw new IllegalArgumentException("Invalid colour: "+colours[i]+". " +
					"Expecting HTML colour, example \"#003366\".");
			}

			colours[i]=new Color(Integer.parseInt(m.group(1), 16),
					Integer.parseInt(m.group(2), 16),Integer.parseInt(m.group(3), 16));
		}

		String orderStr = getParameter("order");
		EmbeddedRecorder.Order order = Order.LISTENFIRST;
		if("RECORDFIRST".equals(orderStr))
		{
			order = Order.RECORDFIRST;
		}
		else if(orderStr!=null && !"LISTENFIRST".equals(orderStr))
		{
			throw new IllegalArgumentException("Invalid order: "+orderStr+". " +
				"Expecting RECORDFIRST or LISTENFIRST.");
		}

		try
		{
			RecordingDevice.macInstall(getCodeBase(),EmbeddedRecorderApplet.class);
		}
		catch (IOException e)
		{
			System.err.println("Error installing OS X extension");
			e.printStackTrace();
		}

		boolean crossPlatformAudio;
		String crossPlatformAudioStr = getParameter("crossplatformaudio");
		if(null == crossPlatformAudioStr || crossPlatformAudioStr.equals("n"))
		{
			crossPlatformAudio = false;
		}
		else if(crossPlatformAudioStr.equals("y"))
		{
			crossPlatformAudio = true;
		}
		else
		{
			throw new IllegalArgumentException("Invalid crossplatformaudio: "+
				crossPlatformAudioStr+". Expecting y or n.");
		}

		// Init recorder panel
		try
		{
			recorder=new EmbeddedRecorder(
				new URL(getRequiredParameter("upload")),
				getOptionalURLParameter("listen"),
				getOptionalURLParameter("record"),
				getOptionalURLParameter("model"),
				getOptionalURLParameter("user"),
				order,
				getParameter("group"),
				strings[STR_LISTEN], strings[STR_RECORD], strings[STR_PLAYBACK],
				strings[STR_MODEL], strings[STR_STOP], strings[STR_CANCEL],
				colours[COL_DARK], colours[COL_LIGHT], colours[COL_FAINT],
				colours[COL_ALTDARK], colours[COL_ALTLIGHT], colours[COL_ALTFAINT],
				colours[COL_CORNERS], crossPlatformAudio);
			getContentPane().add(recorder);
		}
		catch(MalformedURLException e)
		{
			throw new IllegalArgumentException("Invalid URL argument: " +
				e.getMessage());
		}

		// Let the recorder know the context so it can get other applets
		// (dynamically) later on
		recorder.setAppletContext(getAppletContext());
	}

	@Override
	public void stop()
	{
		if(recorder!=null)
		{
			recorder.stop();
		}
	}

	@Override
	public void destroy()
	{
		getContentPane().remove(recorder);
		recorder.destroy();
		recorder=null;
	}

	private String getRequiredParameter(String param)
		throws IllegalArgumentException
	{
		String result = getParameter(param);
		if(result == null)
		{
			throw new IllegalArgumentException(
				"Required applet parameter missing: "+param);
		}
		return result;
	}

	private URL getOptionalURLParameter(String param) throws MalformedURLException
	{
		String result = getParameter(param);
		if(result == null)
		{
			return null;
		}
		return new URL(result);
	}
}
