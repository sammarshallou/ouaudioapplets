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

import javax.swing.*;

import uk.ac.open.audio.adpcm.ADPCMRecording;

/** Base class for applet pages. */
public abstract class PageBase extends JPanel
{
	private static final long serialVersionUID=1L;

	private JPanel main,buttons;

	private MainPanel owner;

	/**
	 * @param owner Owner panel
	 * @param progress Progress (circle index) of page
	 * @param heading Page heading
	 */
	public PageBase(MainPanel owner,int progress,String heading)
	{
		this.owner=owner;
		setLayout(new BorderLayout(0,8));

		JPanel upper=new JPanel(new BorderLayout(8,0));
		upper.setBackground(Color.WHITE);
		add(upper,BorderLayout.NORTH);

		JLabel headingLabel = new FLabel(FontType.HEADING, heading);
		upper.add(headingLabel,BorderLayout.WEST);

		JLabel headingInfo = new FLabel(FontType.SMALL, "Step " + progress + " of 6");
		headingInfo.setVerticalAlignment(JLabel.BOTTOM);
		headingInfo.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));
		upper.add(headingInfo,BorderLayout.EAST);

		buttons=new JPanel(new BorderLayout(8,0));
		buttons.setOpaque(false);
		add(buttons,BorderLayout.SOUTH);
		// Extra border above buttons
		buttons.setBorder(BorderFactory.createEmptyBorder(8,0,0,0));

		main=new JPanel(new BorderLayout(8,8));
		main.setBackground(Color.WHITE);
		add(main,BorderLayout.CENTER);

		setBackground(Color.WHITE);
	}

	/** Called when page is entered. */
	protected void enter()
	{
		final JButton focusButton;
		switch(focus)
		{
		case FOCUS_RIGHT: focusButton=rightButton; break;
		case FOCUS_LEFT1: focusButton=left1Button; break;
		case FOCUS_LEFT2: focusButton=left2Button; break;
		default: focusButton=null;
		}

		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				if(focusButton!=null && focusButton.isShowing())
				{
					getRootPane().setDefaultButton(focusButton);
					focusButton.requestFocus();
				}
			}
		});
	}

	/** Called when page is left. */
	protected void leave()
	{
	}

	/** @return Owner panel */
	protected MainPanel getOwner()
	{
		return owner;
	}

	/** @return Current recording */
	protected ADPCMRecording getRecording()
	{
		return getOwner().getRecording();
	}

	/** @return Main area of this page */
	protected JPanel getMain()
	{
		return main;
	}

	/**
	 * Enables/disables the buttons.
	 * @param left1 First left button
	 * @param left2 Second left button
	 * @param right Right button
	 */
	protected void enableButtons(boolean left1,boolean left2,boolean right)
	{
		if(left1Button!=null)
			left1Button.setEnabled(left1);
		if(left2Button!=null)
			left2Button.setEnabled(left2);
		if(rightButton!=null)
			rightButton.setEnabled(right);
	}

	private JButton rightButton=null,left1Button=null,left2Button=null;
	private int focus;

	/** @return Right button */
	protected JButton getRightButton() { return rightButton; }
	/** @return Left button 1 */
	protected JButton getLeft1Button() { return left1Button; }
	/** @return Left button 2 */
	protected JButton getLeft2Button() { return left2Button; }

	/** Called when right button is clicked. */
	protected void buttonRight()
	{
	}

	/** Called when left button 1 is clicked. */
	protected void buttonLeft1()
	{
	}

	/** Called when left button 2 is clicked. */
	protected void buttonLeft2()
	{
	}

	/** Focus the right button */
	public final static int FOCUS_RIGHT=1;
	/** Focus left button 1 */
	public final static int FOCUS_LEFT1=2;
	/** Focus left button 2 */
	public final static int FOCUS_LEFT2=3;

	/**
	 *
	 * @param left1 Text for left button 1
	 * @param left2 Text for left button 2
	 * @param right Text for right button
	 * @param focus Index (FOCUS_xx constant) of button to focus
	 */
	protected void initButtons(String left1,String left2,String right,int focus)
	{
		buttons.removeAll();
		this.focus=focus;

		if(right!=null)
		{
			rightButton = new FButton(right);
			rightButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent arg0)
				{
					buttonRight();
				}
			});
			buttons.add(rightButton,BorderLayout.EAST);
		}
		if(left1!=null || left2!=null)
		{
			JPanel left=new JPanel(new BorderLayout(8,0));
			left.setOpaque(false);
			buttons.add(left,BorderLayout.WEST);
			if(left1!=null)
			{
				left1Button = new FButton(left1);
				left1Button.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent arg0)
					{
						buttonLeft1();
					}
				});
				left.add(left1Button,BorderLayout.WEST);
			}
			if(left2!=null)
			{
				left2Button = new FButton(left2);
				left2Button.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent arg0)
					{
						buttonLeft2();
					}
				});
				left.add(left2Button,BorderLayout.EAST);
			}
		}
	}

	/**
	 * Base implementation for the RecordingDevice.Handler error function to save
	 * this being implemented in multiple other pages.
	 * @param t Error that occurred
	 */
	public void recordingError(final Throwable t)
	{
		t.printStackTrace();
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				getOwner().showError(t);
			}
		});
	}
}
