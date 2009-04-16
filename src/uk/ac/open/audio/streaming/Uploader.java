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
import java.io.*;
import java.net.*;

/**
 * Handles upload of audio recordings.
 */
public class Uploader
{
	/**
	 * If non-zero, limits uploading to this many bytes per second over 2.
	 */
	private static int ARTIFICIALDELAY_BPS;

	/**
	 * Sets upload delay simulation.
	 * @param bps Simulated bytes per second
	 */
	public static void simulateUploads(int bps)
	{
		ARTIFICIALDELAY_BPS=bps;
		if(bps==0)
		{
			System.err.println("Artifical delay: upload delay disabled");
		}
		else
		{
			System.err.println("Artifical delay: simulating uploads at "+ARTIFICIALDELAY_BPS+" bytes/s");
		}
	}

	/** Size of upload chunks */
	private final static int LARGEBUFFERSIZE=4096,SMALLBUFFERSIZE=1024;

	/** Handles callbacks. */
	public interface Handler
	{
		/**
		 * Called if there is an error during the upload process.
		 * @param t Error
		 */
		public void uploadError(Throwable t);

		/**
		 * Called to update status.
		 * @param transferred Bytes transferred
		 * @param total Total bytes
		 */
		public void uploadStatus(int transferred,int total);
	}

	private Handler h;
	private URL u;
	private byte[] data;

	private boolean close,closed;

	/**
	 * Posts the given data to the URL. If there is an error, handler.uploadError
	 * will be called
	 * @param h Handler that receives information on progress
	 * @param u URL to upload to
	 * @param data Data to upload
	 */
	public Uploader(Handler h,URL u,byte[] data)
	{
		this.h=h;
		this.u=u;

		this.data=data;

		new UploadThread();
	}

	/** Thread handles actual upload */
	private final class UploadThread extends Thread
	{
		private UploadThread()
		{
			super("Data uploader");
			start();
		}

		@Override
		public void run()
		{
			try
			{
				String boundary=Math.random()+"";
				byte[] header=
					("--"+boundary+"\r\n" +
						"Content-Disposition: form-data; name=\"audio\"; " +
							"filename=\"adpcm.wav\"\r\n" +
						"Content-Type: audio/x-wav\r\n" +
						"Content-Transfer-Encoding: binary\r\n\r\n").getBytes("UTF-8");
				byte[] footer=
					("\r\n--"+boundary+"--\r\n").getBytes("UTF-8");
				byte[] fullData=new byte[header.length+data.length+footer.length];
				System.arraycopy(header, 0, fullData, 0, header.length);
				System.arraycopy(data,0,fullData,header.length,data.length);
				System.arraycopy(footer,0,fullData,header.length+data.length,
						footer.length);
				data=fullData;

				HttpURLConnection connection=(HttpURLConnection)u.openConnection();
				connection.setDoOutput(true);
				connection.setRequestMethod("POST");
				connection.setRequestProperty("Content-Type",
						"multipart/form-data, boundary="+boundary);
				connection.connect();
				OutputStream os=connection.getOutputStream();

				int pos=0;
				long lastTime=System.currentTimeMillis();
				int bufferSize=SMALLBUFFERSIZE;
				while(pos!=data.length)
				{
					int toWrite=Math.min(bufferSize,data.length-pos);
					os.write(data,pos,toWrite);
					if(close)
					{
						os.close();
						connection.disconnect();
						return;
					}

					long now=System.currentTimeMillis();
					if(ARTIFICIALDELAY_BPS!=0)
					{
						try
						{
							long until=now+(toWrite*1000)/ARTIFICIALDELAY_BPS;

							while(now<until)
							{
								sleep(until-now);
								now=System.currentTimeMillis();
							}

							// Special pause feature - place mouse in top right of screen to cause
							// network congestion
							while(MouseInfo.getPointerInfo().getLocation().equals(new Point(0,0)))
							{
								sleep(500);
							}
						}
						catch (InterruptedException e)
						{
						}
					}

					if(now-lastTime>500 && bufferSize==LARGEBUFFERSIZE)
					{
						// Switch to a smaller buffer for a modem connection so that we
						// display progress better
						bufferSize=SMALLBUFFERSIZE;
					}
					else if(now-lastTime<100 && bufferSize==SMALLBUFFERSIZE)
					{
						// Switch back up to large buffer if the upload was reasonably
						// fast
						bufferSize=LARGEBUFFERSIZE;
					}
					lastTime=now;
					pos+=toWrite;
					h.uploadStatus(pos,data.length);
				}
				os.close();

				if(connection.getResponseCode()!=200)
				{
					throw new IOException("Unexpected server response code: "+
							connection.getResponseCode());
				}

				connection.disconnect();
			}
			catch(Throwable t)
			{
				h.uploadError(t);
			}
			finally
			{
				closed=true;
				synchronized(Uploader.this)
				{
					Uploader.this.notifyAll();
				}
			}
		}
	}

	/**
	 * Cancels the upload and closes the connection. Will block for a short
	 * time until the next block finishes.
	 */
	public synchronized void cancel()
	{
		close=true;
		notifyAll();
		while(!closed)
		{
			try
			{
				wait();
			}
			catch (InterruptedException e)
			{
			}
		}
	}
}
