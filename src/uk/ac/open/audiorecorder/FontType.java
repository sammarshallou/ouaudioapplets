/**
 * 
 */
package uk.ac.open.audiorecorder;

import java.awt.*;

public enum FontType
{
	NORMAL(new Font("Verdana", Font.PLAIN, 12), Color.BLACK),
	HEADING(new Font("Verdana", Font.BOLD, 12), Color.BLACK),
	SMALL(new Font("Verdana", Font.PLAIN, 9), new Color(80, 80, 80));
	
	private Font font;
	private Color color;
	
	FontType(Font font, Color color)
	{
		this.font = font;
		this.color = color;
	}		
	
	public Font getFont()
	{
		return font;
	}
	
	public Color getColor()
	{
		return color;
	}
}