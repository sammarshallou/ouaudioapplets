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
import java.util.HashMap;

import javax.swing.*;

import uk.ac.open.audio.adpcm.ADPCMRecording;

/** Main recording applet panel. */
public class MainPanel extends JPanel
{
	private static final long serialVersionUID=1L;

	private final static Color OUTLINE=new Color(219,237,255);

	private CardLayout cards=new CardLayout();

	private boolean gotError;

	private PageBase currentPage;
	/** Intro page */
	final static String PAGE_INTRO="intro";
	/** Audio test page */
	final static String PAGE_AUDIOTEST="audiotest";
	/** Record page */
	final static String PAGE_RECORD="record";
	/** Playback page */
	final static String PAGE_PLAYBACK="playback";
	/** Save page */
	final static String PAGE_SAVE="save";
	/** Finished page */
	final static String PAGE_FINISHED="finished";

	private HashMap<String,PageBase> pages=new HashMap<String,PageBase>();

	private ADPCMRecording recording=new ADPCMRecording();

	/** Constructs. */
	MainPanel()
	{
	  super();
	  setLayout(cards);
	  addPage(PAGE_INTRO,new IntroPage(this));
	  addPage(PAGE_AUDIOTEST,new AudioTestPage(this));
	  addPage(PAGE_RECORD,new RecordPage(this));
	  addPage(PAGE_PLAYBACK,new PlaybackPage(this));
	  addPage(PAGE_SAVE,new SavePage(this));
	  addPage(PAGE_FINISHED,new FinishedPage(this));
	  setPage(PAGE_INTRO);
	  setBorder(BorderFactory.createCompoundBorder(
	  		BorderFactory.createMatteBorder(1, 1, 1, 1, OUTLINE),
	  		BorderFactory.createMatteBorder(7, 7, 7, 7, Color.white)
	  		));
	}

	/**
	 * Adds page.
	 * @param name Page name
	 * @param page Page
	 */
	void addPage(String name,PageBase page)
	{
		pages.put(name,page);
		add(name,page);
	}

	/**
	 * Sets current page.
	 * @param page Page ID
	 */
	void setPage(String page)
	{
		if(currentPage!=null)
			currentPage.leave();
		cards.show(this, page);
		currentPage=pages.get(page);
		currentPage.enter();
	}

	/**
	 * Shows an error.
	 * @param t Exception
	 */
	void showError(Throwable t)
	{
		if(gotError)
		{
			// Only show the first
			return;
		}
		gotError=true;
		add("!error",new ErrorPage(t));
		cards.show(this,"!error");
	}

	/**
	 * @return The recording object
	 */
	ADPCMRecording getRecording()
	{
		return recording;
	}
}
