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

import javax.swing.*;

import uk.ac.open.audio.PlaybackDevice;
import uk.ac.open.audio.adpcm.*;

/**
 * Audio recording tool play-what-you-just-recorded page.
 */
public class PlaybackPage extends PageBase implements PlaybackDevice.Handler
{
	private static final long serialVersionUID=1L;

	private WaveformDisplay waveform;
	private ADPCMRecording adpcmPlayback;

	private PlaybackDevice playback;

	private boolean isPlaying;

	private ADPCMEncoder.Block[] blocks;
	private int blockPos;

	/**
	 * @param owner Owner panel
	 */
	public PlaybackPage(MainPanel owner)
	{
		super(owner,4,"Playback");
		initButtons("Play recorded audio","Restart from scratch","Continue",FOCUS_RIGHT);
		enableButtons(true, true, true);

		getMain().add(new WrappedText("Click 'Play recorded audio' if you want to " +
			"hear what you've recorded. When you are done, click 'Continue' to save " +
			"your recording."),BorderLayout.NORTH);

		JPanel lower=new JPanel(new BorderLayout(0,8));
		lower.setOpaque(false);
		getMain().add(lower,BorderLayout.CENTER);

		adpcmPlayback=new ADPCMRecording();
		waveform=new WaveformDisplay(adpcmPlayback);
		lower.add(waveform,BorderLayout.NORTH);
	}

	@Override
	protected void enter()
	{
		super.enter();
		try
		{
			playback=PlaybackDevice.construct(PlaybackDevice.Format.MONO_16KHZ, false);
			adpcmPlayback.clear();
			waveform.recordingRestart();
			blocks=getRecording().getBlocks();
		}
		catch (Throwable t)
		{
			getOwner().showError(t);
		}
	}

	@Override
	protected void leave()
	{
		playback.close();
		super.leave();
	}

	@Override
	protected void buttonLeft1()
	{
		if(!isPlaying)
		{
			if(playback.isPaused())
			{
				try
				{
					playback.resume();
				}
				catch (Exception e)
				{
					getOwner().showError(e);
				}
			}
			else
			{
				blockPos=0;
				adpcmPlayback.clear();
				waveform.recordingRestart();
				try
				{
					playback.play(this, true);
				}
				catch (Throwable t)
				{
					t.printStackTrace();
					getOwner().showError(t);
				}
			}

			isPlaying=true;
			getLeft1Button().setText("Pause playback");
		}
		else
		{
			try
			{
				playback.pause();
			}
			catch (Exception e)
			{
				getOwner().showError(e);
			}

			isPlaying=false;
			getLeft1Button().setText("Resume playback");
		}
	}

	/** Restart button clicked */
	@Override
	protected void buttonLeft2()
	{
		JLabel message = new FLabel(FontType.NORMAL,
			"Are you sure you want to delete the recording and start again?");
		int result=JOptionPane.showOptionDialog(this,
			message,
			"Confirm restart",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE,
			null,
			new String[] {"Delete recording","Cancel"},
			"Cancel");
		if(result==JOptionPane.YES_OPTION)
		{
			getRecording().clear();
			getOwner().setPage(MainPanel.PAGE_RECORD);
		}
	}

	@Override
	protected void buttonRight()
	{
		getOwner().setPage(MainPanel.PAGE_SAVE);
	}

	public byte[] playbackBlock()
	{
		if(blockPos<blocks.length)
		{
			ADPCMEncoder.Block block=blocks[blockPos++];
			adpcmPlayback.addBlock(block);
			waveform.recordingBlockAdded();
			return ADPCMDecoder.decodeBlock(block.getData(), 0);
		}
		else
		{
			return null;
		}
	}

	public void playbackStopped()
	{
		isPlaying=false;
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				getLeft1Button().setText("Play recorded audio");
			}
		});
	}

	public void playbackError(Throwable t)
	{
		getOwner().showError(t);
	}

}
