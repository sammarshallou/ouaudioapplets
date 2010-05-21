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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.DateFormat;
import java.util.Date;

import javax.swing.*;

/**
 * Page displays applet errors.
 */
class ErrorPage extends JPanel
{
	private static final long serialVersionUID=1L;

	/**
	 * @param t Error that occurred
	 */
	ErrorPage(Throwable t)
	{
		super(new BorderLayout(0,8));
		setBackground(Color.WHITE);

		JPanel upper=new JPanel(new BorderLayout(0,8));
		upper.setOpaque(false);
		add(upper,BorderLayout.NORTH);

		JLabel headingLabel = new FLabel(FontType.HEADING, "An error has occurred");
		upper.add(headingLabel,BorderLayout.NORTH);
		upper.add(new WrappedText("A system error has occurred. If you report this " +
			"error, please include the entire text of the error in your email (using " +
			"the copy button below). In the meantime, you might like to use alternative " +
			"software to record your audio."),BorderLayout.SOUTH);

		StringWriter sw=new StringWriter();
		PrintWriter pw=new PrintWriter(sw);
		t.printStackTrace(pw);
		pw.flush();
		String errorMessage=
			"[[Audio recording applet error begins]]\n\n"+
			"Time: "+DateFormat.getDateTimeInstance().format(new Date())+"\n"+
			"OS: "+System.getProperty("os.name")+" "+System.getProperty("os.version")+"\n"+
			"Java: "+System.getProperty("java.version")+"\n\n"+
			sw.toString()+
			"\n[[Audio recording applet error ends]]\n";

 		final JTextArea error=new JTextArea(errorMessage);
		error.setEditable(false);

		JScrollPane errorPane=new JScrollPane(error);
		add(errorPane,BorderLayout.CENTER);

		JPanel buttons=new JPanel(new BorderLayout());
		buttons.setOpaque(false);
		add(buttons,BorderLayout.SOUTH);

		JButton copy = new FButton("Copy error to clipboard");
		copy.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				error.selectAll();
				error.copy();
				error.select(0,0);
			}
		});
		buttons.add(copy,BorderLayout.WEST);
	}

}
