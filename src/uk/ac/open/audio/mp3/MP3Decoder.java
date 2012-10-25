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
package uk.ac.open.audio.mp3;

import java.io.InputStream;

import javazoom.jl.decoder.*;
import uk.ac.open.audio.*;
import uk.ac.open.audio.streaming.*;

/**
 * This class decodes MP3 data using the JLayer library. It converts all data
 * to 44.1 kHz stereo.
 */
public class MP3Decoder implements StreamableDecoder
{
	private Decoder d;
	private Bitstream b;
	private Header h;

	/**
	 * Initialises the decoder.
	 * @param input InputStream used for input data
	 * @throws AudioException If there is a problem reading the MP3
	 */
	public void init(InputStream input) throws AudioException
	{
		try
		{
			d = new Decoder();
			if (!input.markSupported())
			{
				input = new MarkResetStream(input);
			}
	  	b=new Bitstream(input,false);
	  	h=b.readFrame();
	  	if(h==null) throw new AudioException("Cannot play empty MP3");
	  	SampleBuffer buffer=new SampleBuffer(
	  		h.frequency(),
	  		h.mode()==Header.SINGLE_CHANNEL ? 1 : 2);
	  	d.setOutputBuffer(buffer);
		}
		catch(JavaLayerException e)
		{
			throw new AudioException(e);
		}
	}

	/**
	 * Decodes the next frame of the MP3 and returns audio data. This may cause
	 * the thread to block while waiting for data from the InputStream.
	 * @return Decoded data in 44.1kHz stereo 16-bit little-endian format;
	 *   or null if MP3 has ended
	 * @throws AudioException
	 */
	public byte[] decode() throws AudioException
	{
		if(h==null) return null;

		try
		{
			SampleBuffer output=(SampleBuffer)d.decodeFrame(h, b);
			short[] data=output.getBuffer();
			int length=output.getBufferLength();

			// Upsample if required
			boolean stereo=output.getChannelCount()==2;
			int frequency=output.getSampleFrequency();
			if(frequency!=44100)
			{
				data=AudioUtil.resample(data,length,stereo,frequency,44100);
				length=data.length;
			}

			// Convert to bytes for audio output
			byte[] byteData=AudioUtil.shortToByte(data, length, !stereo);

			b.closeFrame();
	  	h=b.readFrame();

			return byteData;
		}
		catch(JavaLayerException e)
		{
			throw new AudioException(e);
		}
	}

}
