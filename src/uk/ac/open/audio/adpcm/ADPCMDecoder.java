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
package uk.ac.open.audio.adpcm;

import java.io.*;

import uk.ac.open.audio.*;
import uk.ac.open.audio.streaming.StreamableDecoder;

/**
 * ADPCM decoder.
 */
public class ADPCMDecoder implements StreamableDecoder
{
	/**
	 * Decodes a block (ADPCMEncoder.BLOCKBYTES) of ADPCM data.
	 * @param adpcm Block of data
	 * @param offset Offset in block to begin decoding at
	 * @return Block of 16-bit 16 kHz decoded audio (size ADPCMEncoder.BLOCKSAMPLES*2)
	 */
	public static byte[] decodeBlock(byte[] adpcm,int offset)
	{
		byte[] data=new byte[ADPCMEncoder.BLOCKSAMPLES*2];
		int outPos=0,inPos=offset;

		data[outPos++]=adpcm[inPos++];
		data[outPos++]=adpcm[inPos++];
		int lastOutput=(int)data[0]&0xff | (int)data[1]<<8;

		int stepIndex=(int)adpcm[inPos++];
		inPos++;

		boolean highNibble=false;
		for(int i=1;i<ADPCMEncoder.BLOCKSAMPLES;i++)
		{
			int delta;
			if(highNibble)
			{
				delta=(int)(((adpcm[inPos]&0xf0)<<24)>>28);
				highNibble=false;
				inPos++;
			}
			else
			{
				delta=(int)(((adpcm[inPos]&0xf)<<28)>>28);
				highNibble=true;
			}

			int step=ADPCM.STEPSIZE[stepIndex];

      int deltaMagnitude = delta & 0x07;

      // Possible delta values
      // 0000 = 0 [+1/8 step]
      // 0001 = 1 [+3/8 step]
      // 0010 = 2 [+5/8 step]
      // 0011 = 3 [+7/8 step]
      // 0100 = 4 [+9/8 step]
      // 0101 = 5 [+11/8 step]
      // 0110 = 6 [+13/8 step]
      // 0111 = 7 [+15/8 step]
      // 1000 = -8 [-1/8 step]
      // 1001 = -7 [-3/8 step]
      // 1010 = -6 [-5/8 step]
      // 1011 = -5 [-7/8 step]
      // 1100 = -4 [-9/8 step]
      // 1101 = -3 [-11/8 step]
      // 1110 = -2 [-13/8 step]
      // 1111 = -1 [-15/8 step]

      int valueAdjust =0;
      if ((deltaMagnitude & 4)!=0) valueAdjust += step;
      step = step >> 1;
      if ((deltaMagnitude & 2)!=0) valueAdjust += step;
      step = step >> 1;
      if ((deltaMagnitude & 1)!=0) valueAdjust += step;
      step = step >> 1;
		  valueAdjust += step;

      if (deltaMagnitude != delta) {
              lastOutput -= valueAdjust;
              if (lastOutput<-0x8000) lastOutput = -0x8000;
      } else {
              lastOutput += valueAdjust;
              if (lastOutput>0x7fff) lastOutput = 0x7fff;
      }

      stepIndex+=ADPCM.STEPINCREMENT_MAGNITUDE[deltaMagnitude];
			if(stepIndex<0) stepIndex=0;
			else if(stepIndex>=ADPCM.STEPSIZE.length) stepIndex=ADPCM.STEPSIZE.length-1;

			data[outPos++]=(byte)(lastOutput&0xff);
			data[outPos++]=(byte)((lastOutput>>8)&0xff);
		}

		return data;
	}

	private InputStream stream;
	private byte[] header=null;

	public void init(InputStream is) throws AudioException
	{
		this.stream=is;
	}

	public byte[] decode() throws AudioException
	{
		try
		{
			if(header==null)
			{
				header=new byte[60];
				int headerPos=0;
				do
				{
					int read=stream.read(header,headerPos,header.length-headerPos);
					if(read==-1)
					{
						throw new AudioException("Unexpected EOF in ADPCM header");
					}
					headerPos+=read;
				}
				while(headerPos!=header.length);

				// Byte 20 is the format type
				if(header[20]!=17)
				{
					throw new AudioException("Does not appear to be ADPCM WAV file");
				}
			}

			// Read block of data
			byte[] input=new byte[ADPCMEncoder.BLOCKBYTES];
			int pos=0;
			do
			{
				int read=stream.read(input,pos,input.length-pos);
				if(read==-1)
				{
					if(pos==0)
					{
						stream.close();
						return null;
					}
					else // Partial block?
					{
						throw new AudioException("Unexpected EOF in ADPCM decoding");
					}
				}
				pos+=read;
			}
			while(pos!=input.length);

			byte[] decoded=decodeBlock(input,0);
			return AudioUtil.convert(
				decoded, decoded.length, false, true, 16000, 44100);
		}
		catch(IOException e)
		{
			throw new AudioException(e);
		}
	}
}
