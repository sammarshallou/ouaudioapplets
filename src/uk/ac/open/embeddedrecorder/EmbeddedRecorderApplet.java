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

import java.applet.Applet;
import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.regex.*;

import javax.swing.*;

import uk.ac.open.audio.RecordingDevice;
import uk.ac.open.embeddedrecorder.EmbeddedRecorder.Order;

/** Applet version of embedded recorder. */
public class EmbeddedRecorderApplet extends JApplet
{
	private final static int STR_LISTEN = 0, STR_RECORD = 1, STR_PLAYBACK = 2,
		STR_MODEL = 3, STR_STOP = 4, STR_CANCEL = 5;

	private final static int COL_DARK = 0, COL_LIGHT = 1, COL_FAINT = 2,
		COL_ALTDARK = 3, COL_ALTLIGHT = 4, COL_ALTFAINT = 5, COL_CORNERS = 6;

	private final static Pattern COLOUR = Pattern.compile(
		"#([0-9A-F]{2})([0-9A-F]{2})([0-9A-F]{2})");

	private EmbeddedRecorder recorder;

	private String focusHackId;

	private final static Pattern REGEX_VERSION = Pattern.compile(
			"([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:_([0-9]+))?(-.*)?");

	private static boolean oldJava;


	/** @return Recorder for this applet */
	EmbeddedRecorder getRecorder()
	{
		return recorder;
	}

	/** Static code detects old Java version */
	static
	{
		String version = System.getProperty("java.version");
		Matcher m = REGEX_VERSION.matcher(version);

		// If it doesn't match we assume it's newer
		if(m.matches())
		{
			int
				major = Integer.parseInt(m.group(1)),
				minor = Integer.parseInt(m.group(2)),
				sub = Integer.parseInt(m.group(3)),
				patch = 0;
			if(m.group(4) != null)
			{
				patch = Integer.parseInt(m.group(4));
			}

			// If they ever release Java '2' it'll be newer..
			if(major==1)
			{
				if(minor < 6)
				{
					oldJava = true;
				}
				else if(minor == 6 && sub == 0)
				{
					oldJava = patch < 13;
				}
			}
		}
		if(oldJava)
		{
			System.err.println("Old Java version < 1.6.0_13 in use; Tab key not " +
				"fully supported");
		}
	}

	@Override
	public void init()
	{
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

		// Get the focus hack setting. If enabled, the system does custom
		// processing to call JavaScript function in the host page, at the point
		// where it ought to release keyboard focus
		focusHackId = getParameter("focushackid");
		if(!focusHackId.matches("[A-Za-z0-9_]+"))
		{
			throw new IllegalArgumentException("Invalid focushackid: " +
				focusHackId + ". Expecting A-Z, a-z, 0-9, _ only.");
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

		// Set up focus - if Java version is recent enough that it doesn't crash!
		// (See OU bug 7734)
		if(focusHackId != null && !oldJava)
		{
			// Listen for focus falling off the ends
			recorder.addFocusHack(
				new Runnable()
				{
					public void run()
					{
						ditchFocus(false);
					}
				},
				new Runnable()
				{
					public void run()
					{
						ditchFocus(true);
					}
				});

			// Inform browser that applet has loaded
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					appletLoaded();
				}
			});
		}
	}

	/**
	 * Initialises focus for the applet.
	 * @param last True to focus the last thing, false for the first
	 */
	public void initFocus(boolean last)
	{
		recorder.focusSomething(last);
	}

	private void ditchFocus(boolean forward)
	{
		// Tell JavaScript to ditch focus for this applet id
		evalJS("appletDitchFocus('"+focusHackId+"', "+forward+");");
	}

	private void evalJS(String js)
	{
		try
		{
			// Decided to use reflection to make this easier to compile - otherwise
			// it needs plugin.jar from a JRE. Also this should make it safer at
			// runtime.

			// JSObject.getWindow(this).eval(js);
			Class<?> c=Class.forName("netscape.javascript.JSObject");
			Method m = c.getMethod("getWindow", new Class<?>[] {Applet.class});
			Object win = m.invoke(null, this);
			Method m2 = c.getMethod("eval", new Class<?>[] {String.class});
			m2.invoke(win, js);
		}
		catch (ClassNotFoundException ex)
		{
			System.err.println("JSObject support not found, ignoring (keyboard focus may not work)");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}

	private void appletLoaded()
	{
		// Tell JavaScript this applet is ready
		evalJS("appletLoaded('"+focusHackId+"');");
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
