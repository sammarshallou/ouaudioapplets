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
package uk.ac.open.embeddedrecorder;

import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;
import java.util.*;

import javax.swing.*;

import uk.ac.open.audio.streaming.*;

/** Panel that contains actual implementation of embedded recorder. */
public class EmbeddedRecorder extends JPanel
	implements StreamPlayerUI.Listener, StreamPlayerGroup.Listener
{
	private Color corners;

	private boolean gotRecording;

	private StreamPlayerUI listenPlayer,recordPlayer,userPlayer,modelPlayer;

	private AppletContext appletContext;

	private final static int INNERMARGIN=4, OUTERMARGIN=4;

	private StreamPlayerGroup players;

	private Order order;

	private String group;

	private Image pauseImage;

	/** Order of activity */
	public enum Order
	{
		/** Listen button appears before Record (normal) */
		LISTENFIRST,
		/** Record buttons are shown before listen button */
		RECORDFIRST
	};

	/**
	 * Constructs with initial settings.
	 * @param upload URL to upload recorded files (required)
	 * @param listen URL for initial 'listen' audio (null if none)
	 * @param record URL for 'record' prompt (null if none)
	 * @param model URL for 'model' answer clip (null if none)
	 * @param user URL for user's already-recorded audio (null if none)
	 * @param order Order of activity
	 * @param image for pause action
	 * @param group Group name or null if no group
	 * @param listenStr Listen button text
	 * @param recordStr Record button text
	 * @param playBackStr Play back button text
	 * @param modelStr Model button text
	 * @param stopStr Stop button text
	 * @param cancelStr Cancel button text
	 * @param dark Dark colour
	 * @param light Light background
	 * @param faint Even lighter background
	 * @param altDark Dark colour 2 (for user elements)
	 * @param altLight Light background 2
	 * @param altFaint Even lighter background 2
	 * @param corners Colour behind applet (to draw corners)
	 * @param crossPlatformAudio True if forcing use of only the cross-platform
	 *   audio
	 */
	public EmbeddedRecorder(URL upload, URL listen, URL record, URL model,
			URL user, Order order,Image pauseImage, String group, String listenStr, String recordStr,
			String playBackStr, String modelStr, String stopStr, String cancelStr,
			Color dark, Color light, Color faint, Color altDark, Color altLight,
			Color altFaint, Color corners, boolean crossPlatformAudio)
	{
		super(new FlowLayout(FlowLayout.LEFT, 0, 0));

		this.corners=corners;
		this.order=order;
		this.group=group;
		this.pauseImage=pauseImage;
		gotRecording=user!=null;

		int playerWidth=0;

		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(
				OUTERMARGIN, OUTERMARGIN, OUTERMARGIN, OUTERMARGIN));

		players=new StreamPlayerGroup();
		players.addListener(this);

		if(listen!=null)
		{
			listenPlayer=new StreamPlayerUI(listenStr,stopStr,cancelStr,
					dark,light,faint,pauseImage);
			listenPlayer.setForceCrossPlatform(crossPlatformAudio);
			listenPlayer.initPlay(listen);
			listenPlayer.setGroup(players);
			playerWidth += listenPlayer.getPreferredSize().width;
			if(order == Order.LISTENFIRST)
			{
				add(listenPlayer);
			}
		}

		userPlayer=new StreamPlayerUI(playBackStr,stopStr,cancelStr,
				altDark,altLight,altFaint,pauseImage);
		userPlayer.setForceCrossPlatform(crossPlatformAudio);
		userPlayer.setGroup(players);
		if(user!=null)
		{
			userPlayer.initPlay(user);
		}

		recordPlayer=new StreamPlayerUI(recordStr,stopStr,cancelStr,
				altDark,altLight,altFaint,pauseImage);
		recordPlayer.setForceCrossPlatform(crossPlatformAudio);
		recordPlayer.initRecord(upload,userPlayer);
		if(record!=null)
		{
			recordPlayer.initPlay(record);
		}
		recordPlayer.setGroup(players);
		recordPlayer.addListener(this);

		if (listen != null && order == Order.LISTENFIRST)
		{
			Spacer s;
			// Show extra margin for the special space between Listen and Record,
			// this makes it match the extra space used by Play back
			int extra=
				userPlayer.getPreferredSize().width-PlayerProgress.PREFERRED_SIZE;
			if(extra>0)
			{
				s=new Spacer(INNERMARGIN+extra);
				playerWidth+=extra;
			}
			else
			{
				s=new Spacer();
			}

			add(s);
		}
		add(recordPlayer);
		playerWidth += recordPlayer.getPreferredSize().width;
		add(new Spacer());
		add(userPlayer);
		playerWidth += userPlayer.getPreferredSize().width;

		if(model!=null)
		{
			modelPlayer=new StreamPlayerUI(modelStr,stopStr,cancelStr,
					dark,light,faint,pauseImage);
			modelPlayer.setForceCrossPlatform(crossPlatformAudio);
			modelPlayer.initPlay(model);
			modelPlayer.setGroup(players);
			if(!gotRecording)
			{
				modelPlayer.setEnabled(false);
			}
			add(new Spacer());
			add(modelPlayer);
			playerWidth += modelPlayer.getPreferredSize().width;
		}

		if(order != Order.LISTENFIRST)
		{
			add(new Spacer());
			add(listenPlayer);
			if(!gotRecording)
			{
				listenPlayer.setEnabled(false);
			}
		}

		int playerCount = 2 + (listen != null ? 1 : 0) + (model != null ? 1 : 0);

		setPreferredSize(new Dimension(playerWidth + (playerCount-1) * INNERMARGIN
				+ 2 * OUTERMARGIN,
				recordPlayer.getPreferredSize().height + 2 * OUTERMARGIN
			));

		// Special actions to turn on artificial delay
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(
			KeyEvent.VK_M, KeyEvent.ALT_DOWN_MASK), "modem");
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(
				KeyEvent.VK_D, KeyEvent.ALT_DOWN_MASK), "dsl");
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(
				KeyEvent.VK_N, KeyEvent.ALT_DOWN_MASK), "normal");
		getActionMap().put("modem", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				StreamPlayer.simulateDownloads(5120);
				Uploader.simulateUploads(2560);
			}
		});
		getActionMap().put("dsl", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				StreamPlayer.simulateDownloads(60000);
				Uploader.simulateUploads(30000);
			}
		});
		getActionMap().put("normal", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				StreamPlayer.simulateDownloads(0);
				Uploader.simulateUploads(0);
			}
		});

		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(
				KeyEvent.VK_Z, KeyEvent.ALT_DOWN_MASK), "stop");
		getActionMap().put("stop", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				stop();
			}
		});
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(
				KeyEvent.VK_X, KeyEvent.ALT_DOWN_MASK), "destroy");
		getActionMap().put("destroy", new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				destroy();
			}
		});
	}

	private static class Spacer extends JComponent
	{
		private int size=INNERMARGIN;

		private Spacer(int size)
		{
			this.size=size;
		}

		private Spacer()
		{
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(size,size);
		}
	}

	/**
	 * Updates the group enable/disable state based on all group members.
	 */
	private void updateGroup()
	{
		LinkedList<EmbeddedRecorder> groupRecorders =
			new LinkedList<EmbeddedRecorder>();

		// Get list of all players in this group including us
		for (Applet a : Collections.list(appletContext.getApplets()))
		{
			if (a instanceof EmbeddedRecorderApplet)
			{
				EmbeddedRecorder recorder=((EmbeddedRecorderApplet)a).getRecorder();
				// This may be null if applet hasn't inited yet
				if (recorder != null && group.equals(recorder.group))
				{
					groupRecorders.add(recorder);
				}
			}
		}

		// Check all group members to see if any are not enabled
		boolean allEnabled = true;
		for (EmbeddedRecorder other : groupRecorders)
		{
			if (!other.gotRecording)
			{
				allEnabled = false;
				break;
			}
		}

		// Now send the shared status to all group members
		for (EmbeddedRecorder other : groupRecorders)
		{
			other.groupEnable(allEnabled);
		}
	}

	private void groupEnable(boolean enable)
	{
		if(modelPlayer!=null)
		{
			modelPlayer.setEnabled(enable);
		}
		if(order == Order.RECORDFIRST && listenPlayer !=null)
		{
			listenPlayer.setEnabled(enable);
		}
		userPlayer.setEnabled(enable);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2=(Graphics2D)g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g2.setColor(corners);
		g2.fillRect(0, 0, getWidth(), getHeight());
		RoundRectangle2D all=new RoundRectangle2D.Float(
				0f,0f,getWidth(),getHeight(),12f,12f);
		g2.setColor(Color.WHITE);
		g2.fill(all);
	}

	public void started(StreamPlayerUI player)
	{
		// Don't care
	}

	public void stopped(StreamPlayerUI player)
	{
		// OK, they have recorded something
		if(userPlayer.hasData())
		{
			gotRecording=true;
			if(group==null)
			{
				if(modelPlayer!=null)
				{
					modelPlayer.setEnabled(true);
				}
				if(order == Order.RECORDFIRST && listenPlayer != null)
				{
					listenPlayer.setEnabled(true);
				}
			}
			else
			{
				updateGroup();
			}
		}
	}

	/**
	 * Stops the applet. If anything's playing, stops it.
	 */
	void stop()
	{
		Runnable r = new Runnable()
		{
			public void run()
			{
				// Stops any audio
				players.starting(null);
			}
		};

		if(SwingUtilities.isEventDispatchThread())
		{
			r.run();
		}
		else
		{
			try
			{
				SwingUtilities.invokeAndWait(r);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	/**
	 * Destroys the applet, closing any running threads.
	 */
	void destroy()
	{
		if(listenPlayer!=null)
		{
			listenPlayer.close();
		}
		if(recordPlayer!=null)
		{
			recordPlayer.close();
		}
		if(userPlayer!=null)
		{
			userPlayer.close();
		}
		if(modelPlayer!=null)
		{
			modelPlayer.close();
		}
	}

	/**
	 * @param appletContext Applet context, used for stopping audio in other
	 *   applets if applicable
	 */
	public void setAppletContext(AppletContext appletContext)
	{
		this.appletContext=appletContext;
		if(group!=null)
		{
			updateGroup();
		}
	}

	public void playerStarting(StreamPlayerUI player)
	{
		// Only used for applet case; and don't do this in response to another
		// applet sending the same message!
		if(appletContext == null || player==null)
		{
			return;
		}

		// Our own players will stop, but make sure that's true of other applet
		// instances too.
		for(Applet a : Collections.list(appletContext.getApplets()))
		{
			if(a instanceof EmbeddedRecorderApplet)
			{
				// Check it's not us
				EmbeddedRecorderApplet other=(EmbeddedRecorderApplet)a;
				if(other.getRecorder()==this)
				{
					continue;
				}

				// Tell it to stop audio
				other.stop();
			}
		}
	}
}
