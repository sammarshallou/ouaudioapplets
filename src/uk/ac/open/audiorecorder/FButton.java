package uk.ac.open.audiorecorder;

import javax.swing.JButton;

public class FButton extends JButton
{
	public FButton(String name)
	{
		super(name);
		setForeground(FontType.NORMAL.getColor());
		setFont(FontType.NORMAL.getFont());
	}
}
