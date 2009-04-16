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

import javax.swing.*;

import uk.ac.open.audio.*;

/** Applet page for audio testing. */
public class AudioTestPage extends PageBase implements RecordingDevice.Handler
{
	private static final long serialVersionUID=1L;

	private LevelMeter lm;

	private RecordingDevice recording;
	private PlaybackDevice playback;

	private int count;

	/**
	 * @param owner Owning panel
	 */
	public AudioTestPage(MainPanel owner)
	{
		super(owner,2,"Audio test");
		initButtons(null,null,"Continue",FOCUS_RIGHT);

		JPanel text=new JPanel(new BorderLayout(0,4));
		text.setOpaque(false);
		getMain().add(text,BorderLayout.CENTER);

		text.add(new WrappedText("Please talk into your microphone and watch " +
			"the level display. If the display usually goes into the yellow area " +
			"between the two dotted lines while you are speaking, then your " +
			"microphone level is correct."),BorderLayout.NORTH);
		JPanel text2=new JPanel(new BorderLayout(0,4));
		text2.setOpaque(false);
		text.add(text2,BorderLayout.CENTER);
		text2.add(new WrappedText(
			"If the level frequently shows as red, your microphone level " +
			"is too loud - reduce the level or move the microphone further away. (The " +
			"top red bar indicates actual distortion.) If it " +
			"never reaches the yellow area, the level may be too low."),
			BorderLayout.NORTH);
		text2.add(new WrappedText("While you talk, your voice will be echoed back " +
				"through your headphones or speakers after a short delay. This can help " +
				"confirm that audio is working correctly. If these echoes build up into " +
				"feedback, please turn down your speakers."),
				BorderLayout.CENTER);

		lm=new LevelMeter();
		getMain().add(lm,BorderLayout.EAST);
	}

	@Override
	protected void buttonRight()
	{
		getOwner().setPage(MainPanel.PAGE_RECORD);
	}

	public void recordingBlock(byte[] data, int bytes, final int level, boolean stopped)
	{
		// Update level meter
    SwingUtilities.invokeLater(new Runnable()
    {
    	public void run()
    	{
    		lm.setLevel(level);
    	}
    });

    try
		{
      // Add data to playback
			playback.add(data, bytes);

			// Count up. After 10 buffers, start playback
	    count++;
	    if(count==10) playback.start();
		}
		catch (AudioException e)
		{
			getOwner().showError(e);
		}
	}

	@Override
	protected void enter()
	{
		super.enter();

		count=0;

		try
		{
			playback=PlaybackDevice.construct(PlaybackDevice.Format.MONO_16KHZ, false);
			recording=RecordingDevice.construct(false);
			recording.record(this);
		}
		catch (Throwable t)
		{
			t.printStackTrace();
			getOwner().showError(t);
		}
	}

	@Override
	protected void leave()
	{
		recording.stop();
		playback.close();
	}

}
