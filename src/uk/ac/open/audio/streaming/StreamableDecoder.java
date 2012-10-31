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

import java.io.InputStream;

import uk.ac.open.audio.AudioException;

/**
 * Interface implemented by anything that can stream data.
 */
public interface StreamableDecoder
{
	/**
	 * Initialises the decoder. This must be called precisely once per decoder.
	 * You cannot reuse a decoder.
	 * @param is Stream containing input data
	 * @throws AudioException If there's any problem
	 */
	public void init(InputStream is) throws AudioException;

	/**
	 * Retrieves audio data. Blocks until sufficient data is available from the
	 * InputStream.
	 * @return Decoded data in 16-bit 44.1kHz stereo little-endian, null at EOF
	 * @throws AudioException If there's any problem
	 */
	public byte[] decode() throws AudioException;
}
