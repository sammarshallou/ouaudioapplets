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

import java.awt.BorderLayout;

import javax.swing.JPanel;

/** Final 'You've finished' page of recording tool. */
public class FinishedPage extends PageBase
{
	private static final long serialVersionUID=1L;

	/**
	 * @param owner Main panel
	 */
	public FinishedPage(MainPanel owner)
	{
		super(owner,6,"Finished!");
		initButtons(null,null,"Make another recording",FOCUS_RIGHT);

		SavePage.LocationInfo li=new SavePage.LocationInfo();

		getMain().add(new WrappedText(
				"The audio file has been saved "+
				li.getExplanation()+"."),
				BorderLayout.NORTH);

		JPanel next=getMain();

		if(li.getHorribleWarning()!=null)
		{
			String[] split=li.getHorribleWarning().split("\n+");
			for(int i=0;i<split.length;i++)
			{
				JPanel horrible=new JPanel(new BorderLayout(0,8));
				horrible.setOpaque(false);
				next.add(horrible,BorderLayout.CENTER);
				next=horrible;
				horrible.add(new WrappedText(
					split[i]),BorderLayout.NORTH);
			}
		}

		next.add(new WrappedText(
				"If you want to make another recording, click the button below. " +
				"Otherwise you can now leave this webpage."),
				BorderLayout.CENTER);
	}

	@Override
	protected void buttonRight()
	{
		getRecording().clear();
		getOwner().setPage(MainPanel.PAGE_RECORD);
	}
}
