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
import java.util.*;


/**
 * Stores all the compressed data blocks recorded for a file.
 */
public class ADPCMRecording
{
	private LinkedList<ADPCMEncoder.Block> blocks=new LinkedList<ADPCMEncoder.Block>();

	/**
	 * Adds a block to the end of the recording.
	 * @param block Block data
	 */
	public synchronized void addBlock(ADPCMEncoder.Block block)
	{
		blocks.addLast(block);
	}

	/**
	 * Clears the recording.
	 */
	public synchronized void clear()
	{
		blocks.clear();
	}

	/**
	 * @return Total recording time in milliseconds
	 */
	public synchronized int getTime()
	{
		return (ADPCMEncoder.BLOCKSAMPLES*blocks.size())/16;
	}

	/**
	 * @param index Block that we want start time of
	 * @return Start time of that block in milliseconds since start of file
	 */
	public static int getBlockTime(int index)
	{
		return (ADPCMEncoder.BLOCKSAMPLES*index)/16;
	}

	/**
	 * Obtains a range of blocks for display or output.
	 * @param start First block to retrieve
	 * @param count Number of blocks
	 * @return Requested blocks (or fewer if there weren't enough available)
	 */
	public synchronized ADPCMEncoder.Block[] getBlocks(int start,int count)
	{
		if(start+count>blocks.size()) count=blocks.size()-start;
		ADPCMEncoder.Block[] result=new ADPCMEncoder.Block[count];

		Iterator<ADPCMEncoder.Block> i=blocks.iterator();
		for(int skip=0;skip<start;skip++) i.next();
		for(int dest=0;dest<count;dest++)
		{
			result[dest]=i.next();
		}
		return result;
	}

	/**
	 * Obtains all blocks for display or output.
	 * @return Array of all blocks
	 */
	public synchronized ADPCMEncoder.Block[] getBlocks()
	{
		return blocks.toArray(new ADPCMEncoder.Block[blocks.size()]);
	}

	/**
	 * Saves data as a .wav file.
	 * @param f Target for saving
	 * @throws IOException Any I/O errors
	 */
	public synchronized void save(File f) throws IOException
	{
		ADPCMEncoder.writeToWav(getBlocks(0,blocks.size()), f);
	}

}
