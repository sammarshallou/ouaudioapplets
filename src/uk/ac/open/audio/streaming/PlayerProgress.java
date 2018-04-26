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
package uk.ac.open.audio.streaming;

import static java.lang.Math.PI;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

import javax.swing.JApplet;
import javax.swing.JComponent;

import uk.ac.open.audio.*;
import uk.ac.open.embeddedrecorder.EmbeddedRecorderApplet;

/**
 * Displays fancy graphics to indicate the progress of audio stream download,
 * playback, and recording.
 */
public class PlayerProgress extends JComponent
{
	/** Width and height of display */
	public static final int PREFERRED_SIZE=66;

	private boolean close;

	private double indeterminateAngle=0;
	private double downloadPercentage=0.0,playPercentage=0.0;
	private int uploadSiren;
	private double pieProportion;
	private int barOpacity=0;
	private int lastMilliseconds,maxMilliseconds;
	private int playMilliseconds;
	private byte[][] lastData=new byte[OLDWAVEFORM][]; // Audio data
	private boolean stereo;
	private Image pauseImage;
	private int pauseTime =0;
	private boolean pauseFlag;

	private Color timeFG,mainFG,darkBG,lightBG,playFG;

	private final static int BARMARGIN=10,BARHEIGHT=6,
		TEXTLEFTMARGIN=10,TEXTTOPMARGIN=16;
	private final static int OLDWAVEFORM=4;
	private final static Font TIMEFONT=new Font("Verdana",Font.PLAIN,10);

	/** Current display state */
	public enum State {
		/** Blank: nothing happening (except maybe downloading) */
		BLANK,
		/** Indeterminate: not yet able to estimate completion times */
		INDETERMINATE,
		/** Countdown: waiting until enough is downloaded that we can start playback */
		COUNTDOWN,
		/** Playing: audio currently playing */
		PLAYING,
		/** Recording: audio currently recording */
		RECORDING,
		/** Uploading: recorded audio being transferred to server */
		UPLOADING,
		/** Prepare to record: beep playback */
		PREPARETORECORD,
		/** Error: an error has occurred (just displays a big X) */
		ERROR };
	private State state;
	private IndeterminateThread indeterminateThread;
	private PlaybackCounterThread counterThread;
	private BarFadeThread barFadeThread;

	/** @return Current display state */
	public State getState()
	{
		return state;
	}

	/**
	 * @param timeFG Colour for timecode display (black)
	 * @param mainFG Colour for download bar and (tinted) waveform display
	 * @param darkBG Darker variant background
	 * @param lightBG Lighter variant background
	 * @param playFG Foreground colour used to indicate playback position
	 */
	public PlayerProgress(Color timeFG,Color mainFG,Color darkBG,Color lightBG,Color playFG)
	{
		this.timeFG=timeFG;
		this.mainFG=mainFG;
		this.darkBG=darkBG;
		this.lightBG=lightBG;
		this.playFG=playFG;
		setBlank();
	}

	/**
	 * Sets the percentage of file that has been downloaded.
	 * @param downloadPercentage Percentage 0.0 to 100.0
	 */
	public synchronized void setDownloadPercentage(double downloadPercentage)
	{
		if(close) return;

		double old=this.downloadPercentage;
		this.downloadPercentage=downloadPercentage;
		repaint();
		if(state==State.BLANK && downloadPercentage>99.99999 && old<=99.99999
			&& barFadeThread == null)
		{
			barFadeThread=new BarFadeThread();
		}
	}

	private synchronized boolean setState(State newState)
	{
		if(state==newState || close) return false;

		if(state==State.INDETERMINATE || state==State.UPLOADING ||
				state==State.PREPARETORECORD)
		{
			indeterminateThread.close();
			indeterminateThread=null;
		}
		if(state==State.PLAYING || state==State.RECORDING)
		{
			counterThread.close();
			counterThread=null;
		}

		state=newState;

		if(state==State.INDETERMINATE || state==State.UPLOADING ||
			state==State.PREPARETORECORD)
		{
			indeterminateAngle=0.0;
			indeterminateThread=new IndeterminateThread();
			barOpacity=255;
			uploadSiren=0;
			if(state==State.UPLOADING)
			{
				downloadPercentage=0.0;
			}
			if(state==State.PREPARETORECORD)
			{
				if(barFadeThread == null)
				{
					barFadeThread = new BarFadeThread();
				}
			}
		}
		if(state==State.PLAYING || state==State.RECORDING)
		{
			counterThread=new PlaybackCounterThread();
			if(state==State.PLAYING)
			{
				barOpacity=255;
			}
			else
			{
				barOpacity=0;
			}
		}
		if(state==State.COUNTDOWN)
		{
			maxMilliseconds=0;
			pieProportion=0.0;
		}
		if(state==State.BLANK)
		{
			playMilliseconds=0;
			playPercentage=0.0;
			this.setPauseFlag(false);
			this.setPauseImage(null);
			if(barOpacity!=0 && downloadPercentage>99.99999 && barFadeThread == null)
			{
				barFadeThread = new BarFadeThread();
			}
		}
		if(state == State.ERROR)
		{
			playMilliseconds=0;
			playPercentage=0.0;
			this.setPauseFlag(false);
			this.setPauseImage(null);
			if(barOpacity!=0 && barFadeThread == null)
			{
				barFadeThread = new BarFadeThread();
			}
		}

		repaint();

		return true;
	}

	private class PlaybackCounterThread extends ClosableThread
	{
		public PlaybackCounterThread()
		{
			super("Playback time counter",PlayerProgress.this);
			start();
		}

		@Override
		protected void runInner()
		{
			synchronized(PlayerProgress.this)
			{
				long previous=System.currentTimeMillis();
				while(!shouldClose())
				{
					long then=System.currentTimeMillis();
					playMilliseconds+=then-previous;
					previous=then;
					repaint();
					try
					{
						PlayerProgress.this.wait(16);
					}
					catch (InterruptedException e)
					{
					}
				}
			}
		}
	}

	private class IndeterminateThread extends ClosableThread
	{
		public IndeterminateThread()
		{
			super("Indeterminate angle updater",PlayerProgress.this);
			start();
		}

		@Override
		protected void runInner()
		{
			synchronized(PlayerProgress.this)
			{
				while(!shouldClose())
				{
					indeterminateAngle-=0.02;
					if(indeterminateAngle<0) indeterminateAngle+=2*Math.PI;
					repaint();
					try
					{
						PlayerProgress.this.wait(16);
					}
					catch (InterruptedException e)
					{
					}
				}
			}
		}
	}

	private class BarFadeThread extends ClosableThread
	{
		public BarFadeThread()
		{
			super("Download bar fader",PlayerProgress.this);
			start();
		}

		@Override
		protected void runInner()
		{
			synchronized(PlayerProgress.this)
			{
				while(!shouldClose() && barOpacity!=0)
				{
					barOpacity-=8;
					if(barOpacity<0) barOpacity=0;
					repaint();
					try
					{
						PlayerProgress.this.wait(16);
					}
					catch (InterruptedException e)
					{
					}
				}
				barFadeThread=null;
			}
		}
	}

	/** Sets blank state. */
	public synchronized void setBlank()
	{
		setState(State.BLANK);
	}

	/** Sets error state. */
	public synchronized void setError()
	{
		setState(State.ERROR);
	}

	/** Sets indeterminate state. */
	public synchronized void setIndeterminate()
	{
		setState(State.INDETERMINATE);
	}

	/** Sets playing state. */
	public synchronized void setPlaying()
	{
		setState(State.PLAYING);
	}

	/** Sets recording state. */
	public synchronized void setRecording()
	{
		setState(State.RECORDING);
	}

	/** Sets uploading state. */
	public synchronized void setUploading()
	{
		setState(State.UPLOADING);
	}

	/** Sets prepare-to-record state. */
	public synchronized void setPrepareToRecord()
	{
		setState(State.PREPARETORECORD);
	}

	/**
	 * Sets or updates countdown state.
	 * @param remainingMilliseconds Number of milliseconds still left
	 */
	public synchronized void setCountdown(int remainingMilliseconds)
	{
		if(close) return;

		setState(State.COUNTDOWN);

		if(lastMilliseconds==0) lastMilliseconds=remainingMilliseconds;

		// Update max if needed
		if(maxMilliseconds==0)
		{
			maxMilliseconds=remainingMilliseconds;
		}
		else if(remainingMilliseconds>maxMilliseconds)
		{
			// Don't adjust it by a massive amount
			maxMilliseconds+=1000;
			remainingMilliseconds=maxMilliseconds;
		}

		// Check if the estimate actually went up
		if(remainingMilliseconds>lastMilliseconds)
		{
			remainingMilliseconds=lastMilliseconds;
		}

		pieProportion=1.0-((double)remainingMilliseconds/maxMilliseconds);
		lastMilliseconds=remainingMilliseconds;
		repaint();
	}

	/**
	 * Sets last audio data for waveform display; also updates playback progress.
	 * @param block Last audio data
	 * @param stereo True for stereo
	 * @param playPercentage Percentage of audio file that has now been played
	 *   (may be StreamPlayer.UNKNOWN)
	 */
	public synchronized void setLastData(byte[] block,boolean stereo,double playPercentage)
	{
		if(close) return;

		lastData[0]=block;
		this.stereo=stereo;
		if(playPercentage!=StreamPlayer.UNKNOWN)
		{
			this.playPercentage=playPercentage;
		}
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		if(close) return;

		Graphics2D g2=(Graphics2D)g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g2.setColor(darkBG);
		RoundRectangle2D edge=new RoundRectangle2D.Float(
				1.5f,1.5f,getWidth()-3f,getHeight()-3f,12f,12f);
		g2.fill(edge);

		BufferedImage fancy=new BufferedImage(getWidth(),getHeight(),
			BufferedImage.TYPE_INT_ARGB);
		RoundRectangle2D inner=new RoundRectangle2D.Float(
				5.5f,5.5f,getWidth()-10.5f,getHeight()-10.5f,12f,12f);
		Graphics2D buffer=fancy.createGraphics();
		buffer.setColor(darkBG);
		buffer.fill(inner);
		buffer.setColor(Color.white);
		buffer.setComposite(AlphaComposite.SrcAtop);

		BufferedImage fancy2=new BufferedImage(getWidth(),getHeight(),
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D buffer2=fancy2.createGraphics();

		float radius=getWidth()/2f;
		float centreX=getWidth()/2f,centreY=getHeight()/2f;

		State currentState;
		double angle,percentageDL,percentagePlay;
		synchronized(this)
		{
			currentState=state;
			angle=indeterminateAngle;
			percentageDL=downloadPercentage;
			percentagePlay=playPercentage;
		}

		if(currentState==State.INDETERMINATE || currentState==State.UPLOADING
				|| currentState==State.PREPARETORECORD)
		{
			// Scary upload siren
			Color sweepBG=darkBG;
			if(currentState==State.UPLOADING)
			{
				uploadSiren++;

				int SIREN_DELAYFRAMES=40;
				int SIREN_FADEFRAMES=8;

				if(uploadSiren>=SIREN_DELAYFRAMES)
				{
					int opacity=(SIREN_FADEFRAMES-
						Math.abs(SIREN_DELAYFRAMES+SIREN_FADEFRAMES-uploadSiren)+1)*
						(255/(SIREN_FADEFRAMES+1));
					buffer2.setColor(new Color(255,128,128,opacity));
					buffer2.fillRect(0, 0, getWidth(), getHeight());
					int inv=255-opacity;
					sweepBG=new Color(
							(sweepBG.getRed()*inv + 255*opacity)/255,
							(sweepBG.getGreen()*inv + 128*opacity)/255,
							(sweepBG.getBlue()*inv + 128*opacity)/255);
					if(uploadSiren==SIREN_DELAYFRAMES+SIREN_FADEFRAMES*2)
					{
						uploadSiren=0;
					}
				}
			}

			// Sweep mode
			//double a2=angle+2*Math.PI/3,a3=angle+4*Math.PI/3;
			double a2=angle+2*Math.PI/5,a3=angle+4*Math.PI/5,a4=angle+6*Math.PI/5,
				a5=angle+8*Math.PI/5;
			if(a2>=2*Math.PI) a2-=2*Math.PI;
			if(a3>=2*Math.PI) a3-=2*Math.PI;
			if(a4>=2*Math.PI) a4-=2*Math.PI;
			if(a5>=2*Math.PI) a5-=2*Math.PI;

			paintSweep(buffer2, sweepBG, lightBG, radius, centreX, centreY,angle);
			paintSweep(buffer2, sweepBG, lightBG, radius, centreX, centreY,a2);
			paintSweep(buffer2, sweepBG, lightBG, radius, centreX, centreY,a3);
			paintSweep(buffer2, sweepBG, lightBG, radius, centreX, centreY,a4);
			paintSweep(buffer2, sweepBG, lightBG, radius, centreX, centreY,a5);
		}
		else if(currentState==State.COUNTDOWN)
		{
			// Pie mode
			paintPie(buffer2, Color.WHITE, lightBG, radius, centreX, centreY, pieProportion);
		}
		else if(currentState==State.ERROR)
		{
			// Error mode
			buffer2.setStroke(new BasicStroke(20f));
			buffer2.drawLine(Math.round(centreX-radius), Math.round(centreY-radius),
					Math.round(centreX+radius), Math.round(centreY+radius));
			buffer2.drawLine(Math.round(centreX-radius), Math.round(centreY+radius),
					Math.round(centreX+radius), Math.round(centreY-radius));
		}
		else if(currentState==State.PLAYING || currentState==State.RECORDING)
		{
			buffer2.setColor(lightBG);
			buffer2.fillRect(0,0,getWidth(),getHeight());

			for(int history=lastData.length-1;history>=0;history--)
			{
				byte[] bytes=lastData[history];
				if(bytes!=null)
				{
					// Display historical audio data in progressively lower opacity
					int opacity = 64;
					for(int shift=0;shift<history;shift++)
					{
						opacity>>=1;
					}
					if(opacity==0) throw new Error("wtf opacity out of range");
					buffer2.setColor(new Color(mainFG.getRed(),mainFG.getGreen(),mainFG.getBlue(),opacity));

					short[] data=AudioUtil.byteToShort(
							bytes, Math.min(bytes.length,getWidth()*4), false);
					int x=0;
					GeneralPath path=new GeneralPath();
					for(int i=0;i<data.length;x++)
					{
						// Average stereo data
						float value;
						if(stereo)
						{
							value=((float)data[i]+(float)data[i+1])/2f;
							i+=2;
						}
						else
						{
							value=data[i];
							i++;
						}

						// Put in range
						float halfHeight=getHeight()/2f;
						int y=Math.round(value/32768f*halfHeight+halfHeight);

						if(x==0)
							path.moveTo(x, y);
						else
							path.lineTo(x, y);
					}
					buffer2.draw(path);
				}
			}
			for(int i=lastData.length-1;i>0;i--)
			{
				lastData[i]=lastData[i-1];
			}

			buffer2.setColor(timeFG);
			buffer2.setFont(TIMEFONT);

			int ms;
			if (this.getpauseFlag() == false)
			{
				ms=playMilliseconds;
				this.setPauseTime(playMilliseconds);
			} else {
				ms = this.getPauseTime();
			}
				String minutes=ms/60000+"";
				if(minutes.length()<2) minutes="0"+minutes;
				ms%=60000;
				String seconds=ms/1000+"";
				if(seconds.length()<2) seconds="0"+seconds;
				ms%=1000;
				String tenths=ms/100+"";
				String time=minutes+":"+seconds+"."+tenths;
				buffer2.drawString(time, TEXTLEFTMARGIN, TEXTTOPMARGIN);
		}

		buffer.drawImage(fancy2,0,0,null);
		g2.drawImage(fancy,0,0, null);

		if(barOpacity!=0 || currentState==State.RECORDING)
		{
			if (this.pauseImage != null)
			{
				int imgsize = 20;
				g2.drawImage(this.pauseImage,(getWidth()-imgsize)/2,(int)(getHeight()-imgsize)/2, imgsize, imgsize,null);
				this.setFocusBorder(g2);
			}
		}

		// Do bar transparently if we're fading it
		if(barOpacity!=0)
		{
			if(barOpacity==255)
			{
				g2.setColor(mainFG);
			}
			else
			{
				g2.setColor(new Color(mainFG.getRed(),mainFG.getGreen(),
					mainFG.getBlue(),barOpacity));
			}

			// Draw percentage bar outline
			int barY=getHeight()-BARMARGIN-BARHEIGHT;
			int barW=getWidth()-2*BARMARGIN-1;
			g2.drawRect(BARMARGIN, barY, barW, BARHEIGHT);

			// And fill DL
			Rectangle2D bar=new Rectangle2D.Float(BARMARGIN+1,barY+1,
					(float)((barW-1)*percentageDL/100.0),BARHEIGHT-1);
			g2.fill(bar);

			// Now draw play point, if nonzero
			if(percentagePlay!=0.0)
			{
				bar=new Rectangle2D.Float(BARMARGIN+1,barY+1,
						(float)((barW-1)*percentagePlay/100.0),BARHEIGHT-1);
				int x=(int)Math.round((barW-2)*percentagePlay/100);
				for(int trail=0;trail<8 && x-trail>=0;trail++)
				{
					int opacity=255-trail*32;
					g2.setColor(new Color(playFG.getRed(),playFG.getGreen(),
							playFG.getBlue(),opacity));
					g2.fillRect(
							BARMARGIN+1+x-trail,barY+1,
							1,BARHEIGHT-1);
				}
			}
		}
	}

	/**
	 * Set border to jpanel on focus
	 **/
	private void setFocusBorder(Graphics2D g2) {
		g2.setColor(new Color(153,204,255));
		g2.drawRect(2,2,getWidth()-5,getHeight()-5);
	}

	/**
	 * Set pause time.
	 * @param pausetime
	 **/
	public void setPauseTime(int pauseTime) {
		this.pauseTime=pauseTime;
		playMilliseconds = pauseTime;
	}

	/**
	 * Get pausetime
	 * @return pausetime
	 **/
	public int getPauseTime() {
		return this.pauseTime;
	}

	/**
	 * Set Pause Image
	 * @param pauseimage
	 **/
	public void setPauseImage(Image pauseImage) {
		this.pauseImage = pauseImage;
	}

	/**
	 * Set Pause Flag
	 * @param pauseflag
	 **/
	public void setPauseFlag(boolean pauseFlag) {
		this.pauseFlag = pauseFlag;
		if (getRootPane() != null)
		{
			Container parent = getRootPane().getParent();
			if (parent instanceof JApplet)
		    {
				((EmbeddedRecorderApplet)parent).isPause(this.pauseFlag);
		    }
		}
	}

	/**
	 * Get Pause Flag
	 * @return pauseFlag
	 **/
	public boolean getpauseFlag() {
		return this.pauseFlag;
	}

	private void paintSweep(Graphics2D g, Color bright, Color dim, double radius,
			float centreX, float centreY, double sweepAngle)
	{
		float
		  xP1=centreX+(float)(Math.sin(sweepAngle)*radius*2),
		  yP1=centreY+(float)(Math.cos(sweepAngle)*radius*2),
	  	xP2=centreX+(float)(Math.sin(sweepAngle+Math.PI/8)*radius*2),
	  	yP2=centreY+(float)(Math.cos(sweepAngle+Math.PI/8)*radius*2);

		Polygon triangle=new Polygon(
			new int[]{Math.round(centreX),Math.round(xP1),Math.round(xP2)},
			new int[]{Math.round(centreY),Math.round(yP1),Math.round(yP2)},3);

		g.setPaint(new GradientPaint(xP2,yP2,bright,
				xP1,yP1,dim));
		g.fill(triangle);
		g.setColor(Color.WHITE);
		g.drawLine(Math.round(centreX), Math.round(centreY), Math.round(xP1), Math.round(yP1));
	}

	private void paintPie(Graphics2D g, Color bright, Color dim, double radius,
			float centreX, float centreY, double pieProportion)
	{
		double pieAngle=-(pieProportion*2*PI);
		double startAngle=pieAngle-PI;
		int quadrant = (int)(pieProportion/0.25);
		double endAngle=PI/2*(2-quadrant);

		float
		  xP1=centreX+(float)(Math.sin(startAngle)*radius*2),
		  yP1=centreY+(float)(Math.cos(startAngle)*radius*2),
	  	xP2=centreX+(float)(Math.sin(endAngle)*radius*2),
	  	yP2=centreY+(float)(Math.cos(endAngle)*radius*2);

		Polygon triangle=new Polygon(
			new int[]{Math.round(centreX),Math.round(xP1),Math.round(xP2)},
			new int[]{Math.round(centreY),Math.round(yP1),Math.round(yP2)},3);

		g.setColor(dim);
		g.fill(triangle);

		if(quadrant>0)
		{
			g.fillRect(Math.round(centreX),Math.round(centreY-(float)radius),
					Math.round((float)radius),Math.round((float)radius));
		}
		if(quadrant>1)
		{
			g.fillRect(Math.round(centreX),Math.round(centreY),
					Math.round((float)radius),Math.round((float)radius));
		}
		if(quadrant>2)
		{
			g.fillRect(Math.round(centreX-(float)radius),Math.round(centreY),
					Math.round((float)radius),Math.round((float)radius));
		}
		if(quadrant>3)
		{
			g.fillRect(Math.round(centreX-(float)radius),Math.round(centreY-(float)radius),
					Math.round((float)radius),Math.round((float)radius));
		}

		if(quadrant<4)
		{
			g.setColor(bright);
			g.drawLine((int)centreX, (int)centreY, (int)xP1, (int)yP1);
		}
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PREFERRED_SIZE,PREFERRED_SIZE);
	}

	/** Closes progress display, freeing resources */
	public synchronized void close()
	{
		// Set close marker
		close=true;

		// Close all threads
		if(indeterminateThread != null)
		{
			indeterminateThread.close();
			indeterminateThread = null;
		}
		if(barFadeThread != null)
		{
			barFadeThread.close();
			barFadeThread = null;
		}
		if(counterThread != null)
		{
			counterThread.close();
			counterThread = null;
		}
	}
}
