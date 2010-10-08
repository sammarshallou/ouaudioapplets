package uk.ac.open.audiorecorder;

import javax.swing.JButton;

/**
 * Button that uses the selected text colour and font.
 */
public class FButton extends JButton
{
	/**
	 * @param name Text of button
	 */
	public FButton(String name)
	{
		super(name);
		setFont(FontType.NORMAL.getFont());
	}
}
