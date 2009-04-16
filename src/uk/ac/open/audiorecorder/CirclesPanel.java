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
import java.awt.font.*;

import javax.swing.JComponent;

/**
 * Panel that draws circles at top of applet.
 */
public class CirclesPanel extends JComponent
{
	private static final long serialVersionUID=1L;

	private final static int NUMCIRCLES=6;
	private final static Color
	  NORMALBG=new Color(219,237,255),
	  CURRENTBG=new Color(0,51,102),CURRENTFG=Color.WHITE,NORMALFG=CURRENTBG;

	private int progress;

	/**
	 * @param progress Current circle
	 */
	CirclesPanel(int progress)
	{
		this.progress=progress;
		setPreferredSize(new Dimension(500,30));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		int circleSize=getHeight();

		Font
		  normalFont=new Font("Verdana",Font.PLAIN,(2*circleSize)/3),
		  currentFont=normalFont.deriveFont(Font.BOLD);

		Graphics2D g2=(Graphics2D)g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g2.setColor(NORMALBG);
		g2.fillRect(circleSize/2, circleSize/2-4, getWidth()-circleSize, 8);

		for(int i=0;i<NUMCIRCLES;i++)
		{
			int circleX=((getWidth()-circleSize)*i)/(NUMCIRCLES-1);
			int number=i+1;
			boolean current=number==progress;
			g.setColor(current ? CURRENTBG : NORMALBG);
			g.fillOval(circleX, 0, circleSize, circleSize);
			g.setColor(current ? CURRENTFG : NORMALFG);
			Font f=current ? currentFont : normalFont;
			g.setFont(f);
			FontRenderContext frc=((Graphics2D)g).getFontRenderContext();
			LineMetrics lm=f.getLineMetrics(""+number, frc);
			int fontY=((int)lm.getAscent()+circleSize)/2-2; // -2 is a HACK
			int fontX=(circleSize-(int)f.getStringBounds(""+number, frc).getWidth())/2;
			g.drawString(""+number, fontX+circleX, fontY);
		}
	}


}
