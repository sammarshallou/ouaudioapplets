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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.LinkedList;

import javax.swing.*;

import uk.ac.open.audio.*;
import uk.ac.open.audio.adpcm.*;
import uk.ac.open.audio.mp3.MP3Decoder;
import uk.ac.open.audio.streaming.StreamPlayer.State;

/**
 * Ties together the low-level streaming support with the progress UI and
 * audio.
 */
public class StreamPlayerUI extends JPanel implements
	StreamPlayer.Handler,PlaybackDevice.Handler,RecordingDevice.Handler,
	Uploader.Handler
{
	private final static int MARGIN=2;

	/**
	 * Connect timeout.
	 */
	private final static int CONNECT_TIMEOUT = 10000;

	/**
	 * Read timeout.
	 */
	private final static int READ_TIMEOUT = 30000;

	/**
	 * 3 connect retries.
	 */
	private final static int CONNECT_RETRIES = 3;

	private enum ButtonState
	{
		START,STOP,CANCELUPLOAD
	}
	private ButtonState buttonState;

	private StreamPlayer stream=null;
	private URL playURL=null,recordURL=null;
	private byte[] playData;
	private PlayerProgress progress;
	private PlaybackDevice playback;
	private RecordingDevice recording;
	private JButton button;
	private StreamPlayerGroup group;
	private Uploader uploader;

	private StreamPlayerUI recordTarget;

	private LinkedList<byte[]> recordedData=new LinkedList<byte[]>();

	private String startText,stopText,cancelUploadText;

	private boolean forceCrossPlatform = false;
	private boolean started;
	private int lastWait=-1;
	private boolean doneBeep,reallyStop,enabled;
	private boolean close;

	private LinkedList<Listener> listeners = new LinkedList<Listener>();

	private Connector connector;

	/** For anyone who wants to listen to events from this player. */
	public interface Listener
	{
		/**
		 * Called whenever this player starts playing or recording.
		 * @param player Player
		 */
		public void started(StreamPlayerUI player);

		/**
		 * Called whenever this player stops playing or recording.
		 * @param player Player
		 */
		public void stopped(StreamPlayerUI player);
	}


	/**
	 * Constructs the player with no existing role/data yet. USe an init method
	 * to set this up.
	 * @param startText Start button text
	 * @param stopText Stop button text
	 * @param cancelUploadText Cancel upload text (if applicable)
	 * @param dark Dark colour
	 * @param light Light colour
	 * @param faint Faint colour
	 */
	public StreamPlayerUI(String startText,String stopText,
			String cancelUploadText,Color dark,Color light,Color faint)
	{
		super(new BorderLayout(0,MARGIN));
		setOpaque(false);
		this.startText=startText;
		this.stopText=stopText;
		this.cancelUploadText=cancelUploadText;
		this.buttonState=ButtonState.START;
		enabled=true;

		progress=new PlayerProgress(Color.BLACK,dark,light,faint,Color.white);
		JPanel inner=new JPanel(new BorderLayout());
		inner.setOpaque(false);
		inner.add(progress,BorderLayout.WEST);
		add(inner,BorderLayout.NORTH);

		// Ensure the button doesn't change size between stop/play modes
		button=new JButton(stopText);
		button.setOpaque(false);
		button.putClientProperty( "JButton.buttonType", "square" );
		button.putClientProperty( "JComponent.sizeVariant", "small" );
		if(RecordingDevice.isMac() && System.getProperty("os.version").startsWith("10.4"))
		{
			button.setFont(
					button.getFont().deriveFont(9.0f));
		}
		else if(!RecordingDevice.isMac())
		{
			button.setFont(button.getFont().deriveFont(11.0f));
		}

		Dimension d=button.getPreferredSize();
		int w=d.width,h=d.height;
		if(cancelUploadText!=null)
		{
			button.setText(cancelUploadText);
			d=button.getPreferredSize();
			w=Math.max(w,d.width);
			h=Math.max(h,d.height);
		}
		button.setText(startText);
		d=button.getPreferredSize();
		w=Math.max(w,d.width);
		h=Math.max(h,d.height);
		button.setPreferredSize(new Dimension(w,h));
		enableButton();

		add(button,BorderLayout.SOUTH);
		button.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				switch(buttonState)
				{
				case START :
					if(group!=null)
					{
						group.starting(StreamPlayerUI.this);
					}
					start();
					break;
				case STOP:
					stop(true,true);
					break;
				case CANCELUPLOAD:
					cancelUpload();
					break;
				}
			}
		});
	}

	/**
	 * @param forceCrossPlatform True to use cross-platform audio only
	 *  (no native)
	 */
	public void setForceCrossPlatform(boolean forceCrossPlatform)
	{
		this.forceCrossPlatform = forceCrossPlatform;
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		if (this.enabled != enabled)
		{
			this.enabled=enabled;
			enableButton();
		}
	}

	/**
	 * Updates current button enabled state.
	 */
	private void enableButton()
	{
		button.setEnabled(enabled &&
			(playURL != null || recordURL != null || playData != null));
	}

	/**
	 * Initialises this player for playback.
	 * @param u URL to play back
	 */
	public void initPlay(URL u)
	{
		this.playURL=u;
		enableButton();
	}

	/**
	 * Initialises this player for playback using the given audio data (ADPCM
	 * WAV or MP3).
	 * @param data Audio file data bytes
	 */
	public void initPlay(byte[] data)
	{
		this.playData=data;
		this.playURL=null;
		stream=null;
		enableButton();
	}

	/**
	 * Initialises this player for recording.
	 * @param u URL that will accept recorded audio
	 * @param recordTarget Player that will accept recorded audio for playback
	 *   (null if none)
	 */
	public void initRecord(URL u,StreamPlayerUI recordTarget)
	{
		this.recordURL=u;
		this.recordTarget=recordTarget;
		enableButton();
	}

	/**
	 * Sets the group of this player. When a player is grouped, clicking play
	 * on one player will stop others.
	 * @param g Group
	 */
	public void setGroup(StreamPlayerGroup g)
	{
		this.group=g;
		group.add(this);
	}

	/** Starts playback. */
	public synchronized void start()
	{
		if(buttonState!=ButtonState.START) return;

		if(recordURL!=null && recordTarget!=null &&
			(recordTarget.playData!=null || recordTarget.playURL!=null))
		{
			if(JOptionPane.showConfirmDialog(this,
					"Recording again will overwrite your previous recording.",
					"Confirm record", JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)
			{
				return;
			}
		}

		try
		{
			doneBeep=false;
			reallyStop=false;

			if(playURL!=null)
			{
				playback=PlaybackDevice.construct(PlaybackDevice.Format.STEREO_44KHZ, forceCrossPlatform);
				if(stream==null)
				{
					if(connector == null)
					{
						progress.setIndeterminate();
						boolean mp3=playURL.getPath().endsWith(".mp3");
						if(!mp3 && !playURL.getPath().endsWith(".wav"))
							throw new Exception("Unsupported filetype: only MP3 or WAV permitted");

						// Connect to the URL in a different thread. Sometimes, the URL connection
						// hangs, so this is not safe to do in the UI thread.
						connector = new Connector(playURL,
							mp3 ? MP3Decoder.class : ADPCMDecoder.class);
					}
				}
				else
				{
					streamChangedState(stream.getState());
					if(stream.getState()==State.WAITBEFOREPLAY)
					{
						if(lastWait==-1)
							progress.setCountdown(lastWait);
						else
							progress.setIndeterminate();
					}
				}
			}
			else if(playData!=null)
			{
				playback=PlaybackDevice.construct(PlaybackDevice.Format.STEREO_44KHZ, forceCrossPlatform);
				if(stream==null)
				{
					progress.setIndeterminate();
					boolean mp3=!(new String(playData,0,4,"UTF-8")).equals("RIFF");
					stream=new StreamPlayer(new ByteArrayInputStream(playData),
						playData.length,
						mp3 ? MP3Decoder.class : ADPCMDecoder.class ,this);
				}
				else
				{
					streamChangedState(stream.getState());
					if(stream.getState()==State.WAITBEFOREPLAY)
					{
						if(lastWait==-1)
							progress.setCountdown(lastWait);
						else
							progress.setIndeterminate();
					}
				}
			}
			else if(recordURL!=null)
			{
				startRecording();
			}

			setButtonState(ButtonState.STOP);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			progress.setError();
		}
	}

	/**
	 * Thread handles URL connection and starts stream player.
	 */
	private class Connector extends Thread
	{
		private URL url;
		private Class<? extends StreamableDecoder> decoderClass;
		private ConnectKiller killer;
		private int attempts;

		Connector(URL url, Class<? extends StreamableDecoder> decoderClass)
		{
			this(url, decoderClass, CONNECT_RETRIES);
		}

		private Connector(URL url, Class<? extends StreamableDecoder> decoderClass,
			int attempts)
		{
			this.url = url;
			this.decoderClass = decoderClass;
			this.attempts = attempts;
			start();
		}

		private class ConnectKiller extends Thread
		{
			ConnectKiller()
			{
				start();
			}

			@Override
			public void run()
			{
				try
				{
					// Wait until after it should have connected
					sleep(CONNECT_TIMEOUT + 500);
					synchronized(Connector.this)
					{
						if(killer == this)
						{
							// Setting the killer variable to null will kill this connection
							log("Undetected timeout connecting to URL:");
							System.err.println(url);
							killer = null;

							// If there are retries left, start a new one
							if(attempts > 0)
							{
								log("Retrying");
								new Connector(url, decoderClass, attempts);
							}
							else
							{
								log("No more retries, stopping");
								failed();
							}
						}
					}
				}
				catch(InterruptedException e)
				{
					// This shouldn't happen
				}
			}
		}

		@Override
		public void run()
		{
			while(true)
			{
				try
				{
					attempts--;
					connect();
					return;
				}
				catch(SocketTimeoutException e)
				{
					killer = null;
					log("Timeout connecting to URL:");
					System.err.println(url);
					if(attempts > 0)
					{
						log("Retrying");
					}
					else
					{
						log("No more retries, stopping");
						failed();
						return;
					}
				}
				catch(IOException e)
				{
					log("Error connecting to URL:");
					System.err.println(url);
					e.printStackTrace();
					return;
				}
			}
		}

		/**
		 * Called when there are no more retries, to cancel playback.
		 */
		private void failed()
		{
			SwingUtilities.invokeLater(new Runnable()
			{
				@Override
				public void run()
				{
					synchronized(StreamPlayerUI.this)
					{
						progress.setError();
						try
						{
							playback.stop();
						}
						catch (AudioException e)
						{
							playbackError(e);
						}
						connector = null;
						setButtonState(ButtonState.START);
					}
				}
			});
		}

		private void connect() throws IOException
		{
			// Kill request even if the connect timeout doesn't work
			synchronized(this)
			{
				killer = new ConnectKiller();
			}
			HttpURLConnection connection = (HttpURLConnection)playURL.openConnection();
			connection.setConnectTimeout(CONNECT_TIMEOUT);
			connection.setReadTimeout(READ_TIMEOUT);
			InputStream input = connection.getInputStream();
			int length = connection.getContentLength();
			synchronized(this)
			{
				// If this completes after we were killed, drop the connection and do
				// nothing.
				if(killer == null)
				{
					connection.disconnect();
					return;
				}
				// OK, now we are safe so drop the killer.
				killer = null;
			}
			synchronized(StreamPlayerUI.this)
			{
				stream = new StreamPlayer(input, length, decoderClass, StreamPlayerUI.this);
				connector = null;
			}
		}
	}

	/**
	 * Display error message with standard format.
	 * @param message Message text
	 */
	private static void log(String message)
	{
		System.err.println("[uk.ac.open.audio.streaming.StreamPlayerUI] " + message);
	}

	private void startRecording() throws AudioException
	{
		recording=RecordingDevice.construct(forceCrossPlatform);
		recording.record(this);
		progress.setRecording();
	}

	/**
	 * Stops playback, returning to the beginning of the track. Will continue
	 * downloading.
	 * @param really True if it really needs to stop playback, false if playback
	 *   has been stopped anyhow
	 * @param changeButton True if button should change (normally it should but
	 *   not if we are about to start recording)
	 */
	public synchronized void stop(boolean really,boolean changeButton)
	{
		// Do nothing if not playing
		if(buttonState!=ButtonState.STOP) return;

		if(playback!=null)
		{
			if(really)
			{
				if(recordURL!=null)
				{
					reallyStop=true;
				}
				try
				{
					playback.stop();
				}
				catch (AudioException e)
				{
					playbackError(e);
				}
			}
			else if(playback.isPlaying()) // It should be, but if there are errors...
			{
				try
				{
					playback.waitForEnd();
				}
				catch (Exception e)
				{
					playbackError(e);
				}
			}
			if(stream != null)
			{
				stream.rewind();
			}
			if(connector != null)
			{
				connector = null;
			}
			progress.setBlank();
			playback.close();
			playback=null;
			started=false;
		}
		if(recording!=null)
		{
			recording.stop();
			recording=null;
		}

		if(changeButton)
		{
			setButtonState(ButtonState.START);
		}
	}

	public void streamChangedState(State s)
	{
		if(s==StreamPlayer.State.READYTOPLAY || s==StreamPlayer.State.FULLYLOADED)
		{
			// This happens if user manually stopped player
			synchronized(this)
			{
				if(playback==null) return;
			}

			if(!started)
			{
				playback.play(this, false);
				started=true;
				progress.setPlaying();
			}
			else if(playback.isPaused())
			{
				try
				{
					playback.resume();
					progress.setPlaying();
				}
				catch (Exception e)
				{
					playbackError(e);
				}
			}
		}
	}

	public void streamError(Throwable t)
	{
		t.printStackTrace();
		progress.setError();
	}

	public void updateStats(int download, int length, final double percentage,
			int downloadPerSecond, int audioPerSecond, final int estimatedWait)
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				progress.setDownloadPercentage(percentage);
				if((progress.getState()==PlayerProgress.State.INDETERMINATE ||
						progress.getState()==PlayerProgress.State.COUNTDOWN) &&
						estimatedWait>0)
				{
					lastWait=estimatedWait;
					progress.setCountdown(estimatedWait);
				}
			}
		});
	}

	private byte[] getBeep()
	{
		// 800 Hz, max level
		double freq=(2*Math.PI/44100)*800,level=20000.0,faderamp=1000;

		// One second
		short[] data=new short[44100];

		// Calculate sine wave
		double pos=0;
		for(int i=0;i<data.length;i++)
		{
			pos+=freq;
			if(pos>2*Math.PI) pos-=2*Math.PI;
			double value=Math.sin(pos)*level;
			if(i<faderamp-1)
			{
				value=(value*(i+1))/faderamp;
			}
			else if(i>data.length-faderamp)
			{
				value=(value*(data.length-i))/faderamp;
			}
			data[i]=(short)Math.round(value);
		}

		return AudioUtil.shortToByte(data, data.length, true);
	}

	public byte[] playbackBlock()
	{
		if(close)
		{
			return null;
		}

		if(stream.hasNextAudio())
		{
			try
			{
				StreamPlayer.AudioBlock data=stream.getNextAudio();
				if(data==null)
				{
					if(recordURL!=null && !doneBeep && !reallyStop)
					{
						doneBeep=true;
						SwingUtilities.invokeLater(new Runnable()
						{
							public void run()
							{
								progress.setLastData(null,true,100.0);
								progress.setPrepareToRecord();
							}
						});

						return getBeep();
					}
					else
					{
						SwingUtilities.invokeLater(new Runnable()
						{
							public void run()
							{
								if(!doneBeep)
								{
									progress.setLastData(null,true,100.0);
								}
								stop(false,!doneBeep);
							}
						});
					}
					return null;
				}
				else
				{
					progress.setLastData(data.getData(),true,data.getPercentagePlayed());
					return data.getData();
				}
			}
			catch (AudioException e)
			{
				e.printStackTrace();
				return null;
			}
		}

		try
		{
			playback.pause();
		}
		catch(AudioException e)
		{
			playbackError(e);
		}
		progress.setIndeterminate();
		return new byte[4];
	}

	public void playbackStopped()
	{
		if(close)
		{
			return;
		}

		if(recordURL!=null && !reallyStop)
		{
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					try
					{
						startRecording();
					}
					catch (AudioException e)
					{
						e.printStackTrace();
						progress.setError();
					}
				}
			});
		}
		else
		{
			progress.setBlank();
		}
	}

	private byte[] recordingOverlap;

	public void recordingBlock(byte[] data, int bytes, int level, boolean stopped)
	{
		if(close)
		{
			return;
		}

		progress.setLastData(data,false,StreamPlayer.UNKNOWN);

		if(recordingOverlap!=null)
		{
			byte[] includingOverlap=new byte[recordingOverlap.length+bytes];
			System.arraycopy(recordingOverlap,0,includingOverlap,0,recordingOverlap.length);
			System.arraycopy(data,0,includingOverlap,recordingOverlap.length,bytes);
			data=includingOverlap;
			bytes=includingOverlap.length;
		}

		int pos=0;
		while(true)
		{
			if(bytes-pos < ADPCMEncoder.BLOCKSAMPLES*2)
			{
				if(bytes-pos==0)
				{
					recordingOverlap=null;
				}
				else
				{
					recordingOverlap=new byte[bytes-pos];
					System.arraycopy(data,pos,recordingOverlap,0,bytes-pos);
				}
				break;
			}

			recordedData.add(ADPCMEncoder.encodeBlock(data, pos, ADPCMEncoder.BLOCKSAMPLES*2).getData());
			pos+=ADPCMEncoder.BLOCKSAMPLES*2;
		}

		if(stopped)
		{
			// Do partial block
			if(recordingOverlap!=null)
			{
				recordedData.add(ADPCMEncoder.encodeBlock(recordingOverlap, 0, recordingOverlap.length).getData());
				recordingOverlap=null;
			}

			progress.setUploading();

			// Build content to upload
			try
			{
				ByteArrayOutputStream upload=new ByteArrayOutputStream();
				ADPCMEncoder.writeWavHeader(upload, recordedData.size()*ADPCMEncoder.BLOCKSAMPLES);
				while(!recordedData.isEmpty())
				{
					upload.write(recordedData.removeFirst());
				}
				if(recordTarget!=null)
				{
					recordTarget.initPlay(upload.toByteArray());
				}
				uploader=new Uploader(this,recordURL,upload.toByteArray());
			}
			catch(IOException e)
			{
				uploadError(e);
			}

			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					setButtonState(ButtonState.CANCELUPLOAD);
				}
			});

		}
	}

	private void cancelUpload()
	{
		uploader.cancel();
		setButtonState(ButtonState.START);
		progress.setError();
	}

	public void playbackError(Throwable t)
	{
		t.printStackTrace();
		progress.setError();
	}

	public void recordingError(Throwable t)
	{
		t.printStackTrace();
		progress.setError();
	}

	public void uploadError(Throwable t)
	{
		t.printStackTrace();
		progress.setError();
	}

	public void uploadStatus(final int transferred, final int total)
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				if(transferred==total)
				{
					progress.setBlank();
					progress.setDownloadPercentage(100.0);
					uploader=null;
					setButtonState(ButtonState.START);
				}
				else
				{
					progress.setDownloadPercentage((100.0*transferred)/total);
				}
			}
		});
	}

	/**
	 * @param l Listener to be notified about player events
	 */
	public void addListener(Listener l)
	{
		synchronized(listeners)
		{
			listeners.add(l);
		}
	}

	/**
	 * @param l Listener that no longer needs notification
	 */
	public void removeListener(Listener l)
	{
		synchronized(listeners)
		{
			listeners.remove(l);
		}
	}

	private Listener[] getListeners()
	{
		synchronized(listeners)
		{
			return listeners.toArray(new Listener[listeners.size()]);
		}
	}

	private void setButtonState(ButtonState newState)
	{
		if(buttonState==newState) return;
		ButtonState oldState=buttonState;
		buttonState=newState;

		if(!SwingUtilities.isEventDispatchThread())
		{
			if(close)
			{
				// Closing the whole thing anyway so who cares
				return;
			}
			throw new Error("Wrong thread");
		}

		switch(newState)
		{
		case STOP:
			button.setText(stopText);
			for(Listener l : getListeners())
			{
				l.started(this);
			}
			break;

		case START:
			button.setText(startText);
			if (oldState == ButtonState.CANCELUPLOAD)
			{
				break;
			}
			for(Listener l : getListeners())
			{
				l.stopped(this);
			}
			break;

		case CANCELUPLOAD:
			button.setText(cancelUploadText);
			for(Listener l : getListeners())
			{
				l.stopped(this);
			}
			break;
		}
	}

	/**
	 * Closes the player, cancelling any playback/recording/upload and
	 * releasing resources.
	 */
	public void close()
	{
		close=true;
		listeners.clear();

		// Cancel existing playback if any
		switch(buttonState)
		{
		case STOP:
			stop(true,true);
			break;
		case CANCELUPLOAD:
			cancelUpload();
			break;
		}

		// Stop progress display and stream
		progress.close();
		if(stream!=null)
		{
			stream.close();
			stream=null;
		}
		progress.getParent().remove(progress);
		progress=null;
		remove(button);
	}

	/** @return True if this player has audio playback data or URL. */
	public boolean hasData()
	{
		return playData != null || playURL != null;
	}

}
