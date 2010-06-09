package uk.ac.open.audiorecorder;

import javax.swing.JLabel;

/**
 * Label that uses the specified colour and font.
 */
public class FLabel extends JLabel
{
	/**
	 * @param type Type of font
	 * @param text Text of label
	 */
	public FLabel(FontType type, String text)
	{
		super(text);
		setFont(type.getFont());
		setForeground(type.getColor());
		setOpaque(false);
	}
}
