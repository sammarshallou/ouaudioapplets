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

/**
 * Encodes 16 kHz 16-bit mono audio in IMA ADPCM format.
 */
public class ADPCMEncoder
{
	/** Number of samples in a single block. */
	public final static int BLOCKSAMPLES=1017;

	/** Sample rate */
	public final static int SAMPLERATE=16000;

	/**
	 * Number of bytes in a compressed block. Calculated as initial sample
	 * uncompressed(2) + initial step index (1) + blank (1) +
	 * samples except initial / 2 */
	public final static int BLOCKBYTES=(BLOCKSAMPLES-1)/2+4;

	/**
	 * Writes data to a .wav file.
	 * @param encoded Array of compressed blocks.
	 * @param f Target file
	 * @throws IOException
	 */
	public static void writeToWav(Block[] encoded,File f) throws IOException
	{
		// Open file
		BufferedOutputStream output=new BufferedOutputStream(new FileOutputStream(f));

		// Work out sample count and write header
		int samples=encoded.length*BLOCKSAMPLES;
		int blocks=writeWavHeader(output, samples);
		if(blocks!=encoded.length) throw new Error("Ooops");

		// Write all the data
		for(int i=0;i<encoded.length;i++)
		{
			output.write(encoded[i].getData());
		}

		output.close();
	}

	/**
	 * Compressed data and writes it to a wav file.
	 * @param allData Audio data (16kHz 16-bit mono little-endian)
	 * @param f Target file
	 * @throws IOException
	 */
	public static void encodeToWav(byte[] allData,File f) throws IOException
	{
		// Open file
		BufferedOutputStream output=new BufferedOutputStream(new FileOutputStream(f));

		// Work out how many blocks it will be
		int samples=allData.length/2;
		int blocks=writeWavHeader(output, samples);

		// Encode and write all the blocks
		int pos=0;
		for(int i=0;i<blocks;i++)
		{
			int size=Math.min(BLOCKSAMPLES*2, allData.length-pos);
			Block block=encodeBlock(allData,pos,size);
			output.write(block.getData());
			pos+=size;
		}

		output.close();
	}

	/**
	 * Writes a 60-byte WAV file header for this type of file.
	 * @param output Stream that receives header
	 * @param samples Number of samples in file
	 * @return Number of blocks expected
	 * @throws IOException
	 */
	public static int writeWavHeader(OutputStream output, int samples)
			throws IOException
	{
		int blocks=(samples+(BLOCKSAMPLES-1))/BLOCKSAMPLES;
		int bytes=blocks*BLOCKBYTES;

		// Write the WAV header
		writeASCII(output,"RIFF");
		write4Byte(output,52+bytes); // Total file size after this
		writeASCII(output,"WAVE");
		writeASCII(output,"fmt ");
		write4Byte(output,20); // fmt block size
		write2Byte(output,17); // Format tag
		write2Byte(output,1); // Num. channels
		write4Byte(output,SAMPLERATE); // Samples per second
		write4Byte(output,(SAMPLERATE*BLOCKBYTES+BLOCKSAMPLES/2)/BLOCKSAMPLES); // Average bytes per second
		write2Byte(output,BLOCKBYTES); // Block align (block size)
		write2Byte(output,4); // Bits per sample
		write2Byte(output,2); // Number of bytes in extra part at end of this
		write2Byte(output,BLOCKSAMPLES); // Extra data: samples per block
		writeASCII(output,"fact");
		write4Byte(output,4); // fact block size
		write4Byte(output,samples); // Number of samples in file
		writeASCII(output,"data");
		write4Byte(output,bytes); // Total length of data
		return blocks;
	}

	private static void writeASCII(OutputStream o,String s) throws IOException
	{
		byte[] ascii=s.getBytes("US-ASCII");
		o.write(ascii);
	}

	private static void write4Byte(OutputStream o,int i) throws IOException
	{
		byte[] data=new byte[4];
		data[0]=(byte)(i&0xff);
		data[1]=(byte)((i>>8)&0xff);
		data[2]=(byte)((i>>16)&0xff);
		data[3]=(byte)((i>>24)&0xff);
		o.write(data);
	}

	private static void write2Byte(OutputStream o,int i) throws IOException
	{
		byte[] data=new byte[2];
		data[0]=(byte)(i&0xff);
		data[1]=(byte)((i>>8)&0xff);
		o.write(data);
	}

	/**
	 * Class represents one block of encoded data. Also stores statistical
	 * information about the block.
	 */
	public static class Block
	{
		private byte[] data;
		private int maxLevel,minLevel;

		/** @return Compressed data */
		public byte[] getData()
		{
			return data;
		}
		/** @return Maximum audio level */
		public int getMaxLevel()
		{
			return maxLevel;
		}
		/** @return Minimum audio level */
		public int getMinLevel()
		{
			return minLevel;
		}
	}

	/**
	 * Encodes a block of data in Windows block format.
	 * @param data Array of input data; must be signed 16-bit audio
	 * @param offset Offset within array
	 * @param length Length in bytes within array (if not blockSamples
	 *   long, will be zero-padded)
	 * @return Encoded data block suitable for use in .wav file
	 */
	public static Block encodeBlock(byte[] data,int offset,int length)
	{
		// If length isn't blockSamples, need to pad with zeros
		if(length<BLOCKSAMPLES*2)
		{
			byte[] newData=new byte[BLOCKSAMPLES*2];
			System.arraycopy(data, offset, newData, 0, length);
			data=newData;
			offset=0;
			length=BLOCKSAMPLES*2;
		}
		else if(length>BLOCKSAMPLES*2)
		{
			throw new IllegalArgumentException("Cannot encode block larger than "+
				BLOCKSAMPLES+" samples");
		}

		Block result=new Block();

		byte[] adpcm=new byte[BLOCKBYTES];
		result.data=adpcm;
		int outPos=0;

	  // Initial sample uncompressed
		int lastOutput=(int)data[0+offset]&0xff | (int)data[1+offset]<<8;
		adpcm[outPos++]=data[0+offset];
		adpcm[outPos++]=data[1+offset];
		result.maxLevel=lastOutput;
		result.minLevel=lastOutput;

		// Initial step index - let's find the next sample and pick the closest
		int nextSample=(int)data[2+offset]&0xff | (int)data[3+offset]<<8;
		int initialDifference=Math.abs(nextSample-lastOutput);
		int stepIndex=0;
		for(;stepIndex<ADPCM.STEPSIZE.length;stepIndex++)
		{
			if(ADPCM.STEPSIZE[stepIndex]>initialDifference) break;
		}
		if(stepIndex>0) stepIndex--;
		adpcm[outPos++]=(byte)stepIndex;

		// Blank
		adpcm[outPos++]=0;

		boolean highNibble=false;
		for(int i=2;i<length;i+=2)
		{
			int target=(int)data[i+offset]&0xff | (int)data[i+offset+1]<<8;
			result.maxLevel=Math.max(result.maxLevel,target);
			result.minLevel=Math.min(result.minLevel,target);

			int difference = target - lastOutput;
			int step=ADPCM.STEPSIZE[stepIndex];

			int delta = (Math.abs(difference)<<2)/step;
			if(delta>7) delta=7;
			if(difference<0) delta|=0x08;

			if(highNibble)
			{
				adpcm[outPos++]|=(byte)((delta&0xf)<<4);
				highNibble=false;
			}
			else
			{
				adpcm[outPos]=(byte)(delta&0xf);
				highNibble=true;
			}

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
		}

		if(outPos!=adpcm.length)
			throw new Error("Unexpected buffer length mismatch");

		return result;
	}
}
