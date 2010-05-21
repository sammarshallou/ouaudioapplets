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

/**
 * Introduction page for audio recording tool.
 */
public class IntroPage extends PageBase
{
	private static final long serialVersionUID=1L;

	/**
	 * @param owner Owning panel
	 */
	public IntroPage(MainPanel owner)
	{
		super(owner,1,"Introduction");
		initButtons(null,null,"Begin audio test",FOCUS_RIGHT);

		getMain().add(new WrappedText("Welcome to the audio recorder. This " +
			"tool helps you record an audio file using your computer.\n\nYou will " +
			"begin by testing that your microphone is working with this tool. " +
			"After that, you record your audio. You can then play it back to check " +
			"it, before finally saving it to your desktop as a compressed .wav file."),
			BorderLayout.NORTH);
	}

	@Override
	protected void buttonRight()
	{
		getOwner().setPage(MainPanel.PAGE_AUDIOTEST);
	}

	@Override
	protected void enter()
	{
	}
}
