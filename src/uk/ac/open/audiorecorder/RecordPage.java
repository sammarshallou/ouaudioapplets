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

import uk.ac.open.audio.RecordingDevice;
import uk.ac.open.audio.adpcm.ADPCMEncoder;

/** Audio recording tool: page for recording audio */
public class RecordPage extends PageBase implements RecordingDevice.Handler
{
	private static final long serialVersionUID=1L;

	private WaveformDisplay waveform;

	private RecordingDevice recording;

	/** @param owner Owning panel */
	public RecordPage(MainPanel owner)
	{
		super(owner,3,"Recording");
		initButtons("Start recording", "Restart from scratch", "Finish", FOCUS_LEFT1);
		enableButtons(true, false, false);

		getMain().add(new WrappedText("Click 'Start recording' to begin recording, " +
			"then speak into the microphone. The display below will show you the time " +
			"taken and a representation of the recorded sound. When you are done, click " +
			"'Pause recording' and then 'Finish'."),BorderLayout.NORTH);

		JPanel lower=new JPanel(new BorderLayout(0,8));
		lower.setOpaque(false);
		getMain().add(lower,BorderLayout.CENTER);

		waveform=new WaveformDisplay(getRecording());
		lower.add(waveform,BorderLayout.NORTH);
	}

	private boolean isRecording=false;

	@Override
	protected void buttonLeft1()
	{
		try
		{
			if(!isRecording)
			{
				if(recording.isPaused())
				{
					recording.resume();
				}
				else
				{
					recording.record(this);
				}

				isRecording=true;
				getLeft1Button().setText("Pause recording");
				enableButtons(true,false,false);
			}
			else
			{
				recording.pause();

				isRecording=false;
				getLeft1Button().setText("Resume recording");
				enableButtons(true, getRecording().getTime()>0, getRecording().getTime()>0);
			}
		}
		catch (Throwable t)
		{
			t.printStackTrace();
			getOwner().showError(t);
		}
	}

	private byte[] buffer=new byte[ADPCMEncoder.BLOCKSAMPLES*2];
	private int bufferPos;

	public void recordingBlock(byte[] data, int bytes, int level, boolean stopped)
	{
		int inPos=0;
		while(inPos < bytes)
		{
			int length=Math.min(buffer.length-bufferPos,bytes-inPos);
			System.arraycopy(data, inPos, buffer, bufferPos, length);
			bufferPos+=length;
			inPos+=length;

			if(bufferPos==buffer.length)
			{
		  	getRecording().addBlock(ADPCMEncoder.encodeBlock(buffer, 0, buffer.length));
		  	waveform.recordingBlockAdded();
		  	bufferPos=0;
			}
		}

		if(bufferPos!=0 && stopped)
		{
			// Clear rest of buffer
			for(;bufferPos<buffer.length;bufferPos++)
			{
				buffer[bufferPos]=0;
			}
	  	getRecording().addBlock(ADPCMEncoder.encodeBlock(buffer, 0, buffer.length));
	  	waveform.recordingBlockAdded();
	  	bufferPos=0;
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
			recording.stop();
			getRecording().clear();
			getLeft1Button().setText("Start recording");
			getLeft2Button().setEnabled(false);
			getRightButton().setEnabled(false);
			waveform.recordingRestart();
		}
	}

	@Override
	protected void buttonRight()
	{
		getOwner().setPage(MainPanel.PAGE_PLAYBACK);
	}

	@Override
	protected void enter()
	{
		super.enter();
		getLeft1Button().setText("Start recording");
		getRightButton().setEnabled(false);
		getLeft2Button().setEnabled(false);
		waveform.recordingRestart();
		try
		{
			recording=RecordingDevice.construct(false);
		}
		catch(Throwable t)
		{
			getOwner().showError(t);
		}
	}

	@Override
	protected void leave()
	{
		recording.stop();
	}

}
