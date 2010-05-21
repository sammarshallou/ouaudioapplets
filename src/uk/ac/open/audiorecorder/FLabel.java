package uk.ac.open.audiorecorder;

import javax.swing.JLabel;

public class FLabel extends JLabel
{
	public FLabel(FontType type, String text)
	{
		super(text);
		setFont(type.getFont());
		setForeground(type.getColor());
	}
}
