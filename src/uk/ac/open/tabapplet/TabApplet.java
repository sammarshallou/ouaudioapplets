package uk.ac.open.tabapplet;

import java.applet.Applet;
import java.awt.Dimension;
import java.awt.event.*;
import java.lang.reflect.Method;
import java.util.regex.*;

import javax.swing.*;

/**
 * Applet that (in conjunction with suitable JavaScript) implements tabbing
 * between the applet and the rest of the page. This is a nightmare because
 * browsers/Java plugin don't do it properly so we have to implement by hand.
 */
public abstract class TabApplet extends JApplet
{
	protected static boolean oldJava;

	/** If enabled, outputs extra junk to Java console. */
	private static boolean VERBOSE = false;

	private final static Pattern REGEX_VERSION = Pattern.compile(
		"([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:_([0-9]+))?(-.*)?");

	private String focusHackId;

	private JButton before, after;
	private Runnable beforeAction, afterAction;

	private boolean internalIgnoreFocusChange;

	private boolean pauseFlag = false;

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
			System.err.println("[uk.ac.open.tabapplet.TabApplet] " +
				"Old Java version < 1.6.0_13 in use; Tab key not fully supported");
		}
		if(VERBOSE)
		{
			System.err.println("[uk.ac.open.tabapplet.TabApplet] " +
				"Applet init (Java version: " + version + ")");
		}
	}

	/**
	 * @return Applet inner component
	 */
	protected abstract JComponent getInner();

	/**
	 * Applet can implement this method to temporarily return true during
	 * periods when Java might try to shift focus onto the buttons, but it is
	 * going to be reset shortly.
	 * @return True to prevent special handling of focus change
	 */
	protected boolean ignoreFocusChange() {
		return false;
	}

	/**
	 * Applet can implement this method if it will directly control initial
	 * focus rather than using the automatic method (because sometimes it doesn't
	 * work).
	 * @return Focuser or null (default) to use automatic method
	 */
	protected TabAppletFocuser getFocuser() {
		return null;
	}

	private void focusLog(String message)
	{
		if(VERBOSE)
		{
			String time = String.format("%tH:%<tM:%<tS.%<tL", System.currentTimeMillis());
			System.err.println("[uk.ac.open.tabapplet.TabApplet] " + time + " " + focusHackId +
				": " + message);
		}
	}

	private void invokeAfterDelay(final long delay, final Runnable r)
	{
		Thread t = new Thread(new Runnable()
		{
			public void run()
			{
				try
				{
					Thread.sleep(delay);
				}
				catch(InterruptedException e)
				{
				}
				SwingUtilities.invokeLater(r);
			}
		});
		t.start();
	}

	public TabApplet()
	{
		getContentPane().setLayout(null);

		// Layout manager doesn't set the before/after buttons so they won't show
		before = new JButton("before");
		getContentPane().add(before, null);

		getContentPane().add(getInner(), null);

		after = new JButton("after");
		getContentPane().add(after, null);

		// The before and after buttons are used to manage focus
		before.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent arg0)
			{
				focusLog("focusGained (before)");
				// Special case so owner can temporarily disable focus change
				if(ignoreFocusChange() || internalIgnoreFocusChange)
				{
					focusLog("focusGained - ignore");
					return;
				}
				if(beforeAction == null)
				{
					// Loop around
					after.transferFocusBackward();
				}
				else
				{
					focusLog("focusGained - transfer");
					// In applet, go back to where focus was before
					before.transferFocus();
					focusLog("focusGained - action");
					// Then run action which will probably send focus elsewhere
					beforeAction.run();
				}
			}
		});
		after.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent arg0)
			{
				focusLog("focusGained (after)");
				// Special case so owner can temporarily disable focus change
				if(ignoreFocusChange() || internalIgnoreFocusChange)
				{
					focusLog("focusGained - ignore");
					return;
				}
				if(afterAction == null)
				{
					// Loop around
					before.transferFocus();
				}
				else
				{
					focusLog("focusGained - transfer");
					// In applet, go back to where focus was before
					after.transferFocusBackward();
					focusLog("focusGained - action");
					// Then run action which will probably send focus elsewhere
					afterAction.run();
				}
			}
		});
	}

	@Override
	public void setBounds(int x, int y, int width, int height)
	{
		super.setBounds(x, y, width, height);
		getInner().setBounds(0, 0, width, height);
	}

	@Override
	public void init()
	{
		super.init();

		// Disable focus processing for a bit - for some reason it tends to get
		// focused as soon as it appears, which causes it to ditch focus off
		// somewhere stupid.
		internalIgnoreFocusChange = true;
		invokeAfterDelay(100, new Runnable()
		{
			public void run()
			{
				internalIgnoreFocusChange = false;
			}
		});

		// Get the focus hack setting. If enabled, the system does custom
		// processing to call JavaScript function in the host page, at the point
		// where it ought to release keyboard focus
		focusHackId = getParameter("focushackid");

		// Set up focus - if Java version is recent enough that it doesn't crash!
		// (See OU bug 7734)
		if(focusHackId != null && !oldJava)
		{
			// Check id is valid
			if(!focusHackId.matches("[A-Za-z0-9_]+"))
			{
				throw new IllegalArgumentException("Invalid focushackid: " +
					focusHackId + ". Expecting A-Z, a-z, 0-9, _ only.");
			}
			focusLog("Focus hack enabled");

			// Listen for focus falling off the ends
			this.beforeAction = new Runnable()
			{
				public void run()
				{
					ditchFocus(false);
				}
			};
			this.afterAction = new Runnable()
			{
				public void run()
				{
					ditchFocus(true);
				}
			};

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

	private void ditchFocus(boolean forward)
	{
		// Tell JavaScript to ditch focus for this applet id
		String js = "appletDitchFocus('"+focusHackId+"', "+forward+");";
		if(VERBOSE)
		{
			System.err.println("[uk.ac.open.tabapplet.TabApplet] " + focusHackId +
				": Ditching focus via JS. " + js);
		}
		evalJS(js);
	}

	/**
	 * To check if VR is paused
	 * @param ispause boolean 
	 **/
	public void isPause(boolean isPause)
	{
		this.pauseFlag = isPause;
		// Tell JavaScript to ditch focus for this applet id
		String js = "isPause("+this.pauseFlag+");";
		if(VERBOSE)
		{
			System.err.println("[uk.ac.open.tabapplet.TabApplet] " + focusHackId +
				": Ditching focus via JS. " + js);
		}
		evalJS(js);
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
			System.err.println("[uk.ac.open.tabapplet.TabApplet] " +
				"JSObject support not found, ignoring (keyboard focus may not work)");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}

	private void appletLoaded()
	{
		// Tell JavaScript this applet is ready
		String js = "appletLoaded('"+focusHackId+"');";
		if(VERBOSE)
		{
			System.err.println("[uk.ac.open.tabapplet.TabApplet] " + focusHackId +
				": Reporting applet loaded. " + js);
		}
		evalJS(js);
	}

	/**
	 * Called from JavaScript. Initialises focus for the applet.
	 * @param last True to focus the last thing, false for the first
	 */
	public void initFocus(final boolean last)
	{
		focusLog("Received initFocus call from JS (" + last + ")");
		Runnable r = new Runnable()
		{
			public void run()
			{
				TabAppletFocuser focuser = getFocuser();
				if(focuser != null)
				{
					focusLog("Calling focuser");
					focuser.initFocus(last);
					return;
				}
				if(last)
				{
					focusLog("Requesting last focus (1)");
					internalIgnoreFocusChange = true;
					after.requestFocusInWindow();
					after.requestFocus();
					invokeAfterDelay(250, new Runnable()
					{
						public void run()
						{
							focusLog("Requesting last focus (2)");
							after.transferFocusBackward();
							internalIgnoreFocusChange = false;
							focusLog("Requesting last focus (done)");
						}
					});
				}
				else
				{
					focusLog("Requesting first focus (1)");
					internalIgnoreFocusChange = true;
					before.requestFocusInWindow();
					before.requestFocus();
					invokeAfterDelay(250, new Runnable()
					{
						public void run()
						{
							focusLog("Requesting first focus (2)");
							before.transferFocus();
							internalIgnoreFocusChange = false;
							focusLog("Requesting first focus (done)");
						}
					});
				}
			}
		};
		if(SwingUtilities.isEventDispatchThread())
		{
			r.run();
		}
		else
		{
			SwingUtilities.invokeLater(r);
		}
	}

	@Override
	public Dimension getPreferredSize()
	{
		return getInner().getPreferredSize();
	}
}
