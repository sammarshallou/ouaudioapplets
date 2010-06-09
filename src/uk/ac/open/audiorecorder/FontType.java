package uk.ac.open.audiorecorder;

import java.awt.*;

/**
 * Defines the available types of font.
 */
public enum FontType
{
	/**
	 * Normal font used for most things.
	 */
	NORMAL(new Font("Verdana", Font.PLAIN, 12), Color.BLACK),
	/**
	 * Heading font used for, er, page headings.
	 */
	HEADING(new Font("Verdana", Font.BOLD, 12), Color.BLACK),
	/**
	 * Small text used in a few places.
	 */
	SMALL(new Font("Verdana", Font.PLAIN, 9), new Color(80, 80, 80));
	
	private Font font;
	private Color color;
	
	FontType(Font font, Color color)
	{
		this.font = font;
		this.color = color;
	}		
	
	/**
	 * @return Actual Java font to use
	 */
	public Font getFont()
	{
		return font;
	}
	
	/**
	 * @return Actual colour to use
	 */
	public Color getColor()
	{
		return color;
	}
}