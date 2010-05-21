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
package uk.ac.open.audiorecorder;

/**
 * Component that displays wrapped text.
 */
public class WrappedText extends javax.swing.JTextArea
{
	private static final long serialVersionUID=1L;

	/**
	 * @param text Text to display
	 */
	public WrappedText(String text)
	{
		super(text);
		setFont(FontType.NORMAL.getFont());
		setForeground(FontType.NORMAL.getColor());
		setLineWrap(true);
		setWrapStyleWord(true);
		setEditable(false);
		setBorder(null);
		setOpaque(false);
	}
}
