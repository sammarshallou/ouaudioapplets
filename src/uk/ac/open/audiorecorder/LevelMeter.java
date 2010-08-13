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

import javax.swing.JComponent;

/** Meter displays audio level. */
public class LevelMeter extends JComponent
{
	private static final long serialVersionUID=1L;

	private int level=0,maxRecent=0,timeSinceMax=0;

	private final static Color
    NOBAR=new Color(219,237,255),
    LOW=new Color(0,128,0),
    CORRECT=new Color(210,210,0),
    HIGH=new Color(190,0,0),
    DOTTED=new Color(0,51,102);

	private final static int
	  LEVEL_ORANGE=50;

	private final static int BAR_HEIGHT=12,BAR_GAP=5;

	/** Constructs */
	public LevelMeter()
	{
		setPreferredSize(new Dimension(100,300));
	}

	/**
	 * Updates displayed level.
	 * @param level Level in range 0-128
	 */
	public void setLevel(int level)
	{
		this.level=level;
		if(level>=maxRecent)
		{
			maxRecent=level;
			timeSinceMax=0;
		}
		else
		{
			timeSinceMax++;
			if(timeSinceMax>=20)
				maxRecent=0;
		}
		repaint();
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

		// Decide how many bars to show.
		// Note H here is 2 pixels less (border) than component height.
		// H = N * BAR_HEIGHT + (N-11) * BAR_GAP
		// H = N*BAR_HEIGHT + N*BAR_GAP - BAR_GAP
		// H = N*(BAR_HEIGHT+BAR_GAP) - BAR_GAP
		// so N = (H + BAR_GAP) / (BAR_HEIGHT+BAR_GAP)
		int bars=(height+ BAR_GAP)/(BAR_HEIGHT+BAR_GAP);

		// Update height so it's only big enough to include the bars
		height=bars*BAR_HEIGHT+(bars-1)*BAR_GAP;

		// How many bars are we going to colour in?
		int levelBars=(level*bars+64)/128; // Range 0 to bars
		int maxRecentBar=(maxRecent*bars+64)/128;
		int correctBars=(LEVEL_ORANGE*(bars-1)+64)/128;
		int highBars=bars-1;

		// Draw bars
		for(int bar=0;bar<bars;bar++)
		{
			if(levelBars>bar || maxRecentBar-1==bar)
			{
				Color color;
				if(correctBars>bar)
				{
					color = LOW;
				}
				else if(highBars>bar)
				{
					color = CORRECT;
				}
				else
				{
					color = HIGH;
				}
				if(levelBars <= bar)
				{
					color = new Color(color.getRed(), color.getGreen(), color.getBlue(), 100);
				}
				g2.setColor(color);
			}
			else
			{
				g2.setColor(NOBAR);
			}

			int barY=height-BAR_HEIGHT-(BAR_GAP+BAR_HEIGHT)*bar;

			// Bars go upside-down...
			g2.fillRoundRect(0, barY,width, BAR_HEIGHT, 8, 8);

			if(bar==correctBars || bar==highBars)
			{
				g2.setColor(DOTTED);
				g2.setStroke(new BasicStroke(1.0f,BasicStroke.CAP_SQUARE,BasicStroke.JOIN_MITER,10.0f,
						new float[] {2.0f},0.0f));
				g2.drawLine(1, barY+BAR_HEIGHT+BAR_GAP/2, width-1, barY+BAR_HEIGHT+BAR_GAP/2);
			}
		}
	}

}
