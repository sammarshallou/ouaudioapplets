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
import java.awt.geom.Rectangle2D;

import javax.swing.*;

import uk.ac.open.audio.adpcm.*;

/**
 * Displays recorded ADPCM data as a waveform.
 */
public class WaveformDisplay extends JComponent
{
	private static final long serialVersionUID=1L;

	private final static Color
    AXIS=new Color(219,237,255),
    DATA=new Color(0,51,102),
    HIGH=Color.RED;

	private final static int DANGERTIPS=2;

	private ADPCMRecording recording;
	private int position=0;

	/**
	 * @param recording Recording to display
	 */
	public WaveformDisplay(ADPCMRecording recording)
	{
		this.recording=recording;
		setPreferredSize(new Dimension(500,60));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2=(Graphics2D)g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int width=getWidth(),height=getHeight();

		// Clear background
		g2.setColor(Color.WHITE);
		g2.fillRect(0,0,width+1,height+1);

		// Draw time
		g2.setColor(FontType.NORMAL.getColor());
		Font f = FontType.NORMAL.getFont();
		g2.setFont(f);
		FontMetrics fm=g2.getFontMetrics(f);
		int baselinePos=height-fm.getDescent();
		height-=fm.getDescent()+fm.getAscent()+4; // 4 pixel margin for everything else

		int seconds=recording.getTime()/1000;
		int minutes=seconds/60;
		seconds=seconds-(minutes*60);
		String time=minutes+":"+(seconds/10)+""+(seconds%10);
		g2.drawString(time, 0, baselinePos);

		// Get data up to current position
		int start=position-width;
		int count=width;
		if(start<0)
		{
			count+=start;
			start=0;
		}
		ADPCMEncoder.Block[] blocks=recording.getBlocks(start, count);

		// Build full data with one block per pixel
		int[] min=new int[width],max=new int[width];
		int offset=width-blocks.length;
		for(int i=0;i<blocks.length;i++)
		{
			min[i+offset]=blocks[i].getMinLevel();
			max[i+offset]=blocks[i].getMaxLevel();
		}

		float scaleFactor=((float)height/2.0f)/32768f;
		int centre=height/2;

		// Draw waveform
		g2.setColor(DATA);
		//Line2D.Float line=new Line2D.Float();
		Rectangle2D.Float rect=new Rectangle2D.Float();
		for(int i=0;i<width;i++)
		{
			if(min[i]==max[i]) continue;
			float
			  minY=scaleFactor*(float)min[i]+(float)centre+0.5f,
			  maxY=scaleFactor*(float)max[i]+(float)centre+0.5f;
			//line.setLine(i,scaleFactor*(float)min[i]+(float)centre,i,scaleFactor*(float)max[i]+(float)centre);
			//g2.draw(line);
			if(minY<DANGERTIPS || maxY>=height-DANGERTIPS)
			{
				g2.setColor(HIGH);
				if(minY<DANGERTIPS)
				{
					rect.setRect(i,minY,1,DANGERTIPS-minY);
					g2.fill(rect);
					minY=DANGERTIPS;
				}
				if(maxY>height-DANGERTIPS+1)
				{
					rect.setRect(i,height-DANGERTIPS+1,1,maxY-(height-DANGERTIPS+1));
					g2.fill(rect);
					maxY=height-DANGERTIPS+1;
				}

				g2.setColor(DATA);
			}
			rect.setRect(i,minY,1,maxY-minY);
			g2.fill(rect);
		}

		// Draw axis
		g2.setColor(AXIS);
		g2.drawLine(0, centre, width+1, centre);
	}

	/**
	 * Must be called when a block is added to the recording, to make it repaint
	 * and update current position.
	 */
	void recordingBlockAdded()
	{
		position++;
		repaint();
	}

	/**
	 * Must be called when the recording is cleared, to go back to the start and
	 * wipe the display
	 */
	void recordingCleared()
	{
		position=0;
		repaint();
	}

	/*
	private final static int GROUPBLOCKS=1,GROUPPIXELS=1;

	protected void paintComponent(Graphics g)
	{
		Graphics2D g2=(Graphics2D)g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int width=getWidth(),height=getHeight(),widthChunks=(getWidth()+(GROUPPIXELS-1))/GROUPPIXELS;

		// Clear background
		g2.setColor(Color.WHITE);
		g2.fillRect(0,0,width+1,height+1);

		// Get data up to current position
		int start=(position/GROUPBLOCKS)*GROUPBLOCKS-widthChunks*GROUPBLOCKS;
		int count=widthChunks*GROUPBLOCKS;
		if(start<0)
		{
			count+=start;
			start=0;
		}
		ADPCMEncoder.Block[] blocks=recording.getBlocks(start, count);

		// Build full data with one block per pixel
		int[] min=new int[widthChunks],max=new int[widthChunks];
		int minCurrent=0,maxCurrent=0,offset=widthChunks-blocks.length/GROUPBLOCKS;
		for(int i=0;i<blocks.length;i++)
		{
			minCurrent=Math.min(minCurrent,blocks[i].getMinLevel());
			maxCurrent=Math.max(maxCurrent,blocks[i].getMaxLevel());
			if((i+1)%GROUPBLOCKS == 0)
			{
				min[i/GROUPBLOCKS+offset]=minCurrent/GROUPBLOCKS;
				max[i/GROUPBLOCKS+offset]=maxCurrent/GROUPBLOCKS;
				minCurrent=0;
				maxCurrent=0;
			}
		}

		float scaleFactor=((float)height/2.0f)/32768f;
		int centre=height/2;

		// Draw waveform
		g2.setColor(DATA);
		//Line2D.Float line=new Line2D.Float();
		Rectangle2D.Float rect=new Rectangle2D.Float();
		for(int i=0;i<widthChunks;i++)
		{
			if(min[i]==max[i]) continue;
			int x=i*GROUPPIXELS-(width%GROUPPIXELS);
			float
			  minY=scaleFactor*(float)min[i]+(float)centre,
			  maxY=scaleFactor*(float)max[i]+(float)centre;
			//line.setLine(i,scaleFactor*(float)min[i]+(float)centre,i,scaleFactor*(float)max[i]+(float)centre);
			//g2.draw(line);
			rect.setRect(x,minY,GROUPPIXELS,maxY-minY);
			g2.fill(rect);
		}

		// Draw axis
		g2.setColor(AXIS);
		g2.drawLine(0, centre, width+1, centre);
	}

	void recordingBlockAdded()
	{
		position++;
		if(position%GROUPBLOCKS==0) repaint();
	}
*/
}
