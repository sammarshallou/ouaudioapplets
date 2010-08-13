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
import uk.ac.open.tabapplet.TabAppletFocuser;

/** Main recording applet panel. */
public class MainPanel extends JPanel implements TabAppletFocuser
{
	private static final long serialVersionUID=1L;

	private CardLayout cards=new CardLayout();

	private boolean gotError;

	private boolean ignoreFocusChange;

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
	  setBorder(BorderFactory.createEmptyBorder(9, 11, 11, 11));
	  setOpaque(false);
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
		ignoreFocusChange = true;
		if(currentPage!=null)
			currentPage.leave();
		cards.show(this, page);
		currentPage=pages.get(page);
		currentPage.enter();
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				ignoreFocusChange = false;
			}
		});
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

	private final static Color BORDER_OUTER = new Color(204, 204, 204);
	private final static Color GRADIENT_START = new Color(204, 238, 237);
	private final static int GRADIENT_SIZE = 34;
	private final static int[] EDGES = { 3, 2, 1};

	@Override
	protected void paintComponent(Graphics g)
	{
		// Do background (white, rounded edges)
		g.setColor(Color.WHITE);
		int edge = EDGES.length;
		g.fillRect(edge, edge, getWidth() - 2*edge, getHeight() - 2*edge);
		for(int i=0; i<EDGES.length; i++)
		{
			edge = EDGES[i];
			g.fillRect(i, edge, 1, getHeight() - edge*2);
			g.fillRect(getWidth()-i-1, edge, 1, getHeight() - edge*2);
			g.fillRect(edge, i, getWidth()-edge*2, 1);
			g.fillRect(edge, getHeight()-i-1, getWidth()-edge*2, 1);
		}
	}

	@Override
	protected void paintBorder(Graphics g)
	{
		super.paintBorder(g);

		int red = GRADIENT_START.getRed(), green=GRADIENT_START.getGreen(),
			blue = GRADIENT_START.getBlue();
		for(int i=0; i<GRADIENT_SIZE; i++)
		{
			int inverse = GRADIENT_SIZE - i;
			g.setColor(new Color(
				(red * inverse + 255 * i) / GRADIENT_SIZE,
				(green * inverse + 255 * i) / GRADIENT_SIZE,
				(blue * inverse + 255 * i) / GRADIENT_SIZE
				));
			int edge = i<EDGES.length ? EDGES[i] : 0;
			g.fillRect(edge, i+1, getWidth() - 2*edge, 1);
		}

		g.setColor(BORDER_OUTER);
		((Graphics2D)g).setRenderingHint(
			RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 10, 10);
	}

	/**
	 * @return True if we are in the process of changing page so focus change
	 *   should be left to Java for a bit.
	 */
	boolean ignoreFocusChange()
	{
		return ignoreFocusChange;
	}

	public void initFocus(boolean last)
	{
		currentPage.initFocus(last);
	}
}
