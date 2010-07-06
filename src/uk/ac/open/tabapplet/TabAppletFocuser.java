package uk.ac.open.tabapplet;

/**
 * Interface for applets which control initial focus (because the default
 * doesn't work).
 */
public interface TabAppletFocuser
{
	/**
	 * Called from JavaScript. Initialises focus for the applet.
	 * @param last True to focus the last thing, false for the first
	 */
	public void initFocus(boolean last);
}
