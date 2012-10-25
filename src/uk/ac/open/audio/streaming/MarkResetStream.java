package uk.ac.open.audio.streaming;

import java.io.*;

/**
 * Stream that adds mark/reset support to a source stream.
 *
 * Not thread-safe.
 */
public class MarkResetStream extends InputStream
{
	private InputStream original;
	private boolean marking;
	private byte[] markBuffer;
	private int markPos;
	private boolean replaying;
	private byte[] replayBuffer;
	private int replayPos;

	public MarkResetStream(InputStream original)
	{
		this.original = original;
	}

	@Override
	public boolean markSupported()
	{
		return true;
	}

	@Override
	public void mark(int readlimit)
	{
		if(markBuffer == null || markBuffer.length < readlimit)
		{
			markBuffer = new byte[readlimit];
		}
		marking = true;
		markPos = 0;
	}

	@Override
	public void reset() throws IOException
	{
		if(marking)
		{
			replaying = true;
			replayBuffer = new byte[markPos];
			System.arraycopy(markBuffer, 0, replayBuffer, 0, markPos);
			replayPos = 0;
		}
		else
		{
			throw new IOException("Not marking");
		}
	}

	@Override
	public void close() throws IOException
	{
		original.close();
	}

	@Override
	public int available() throws IOException
	{
		return original.available() + (replaying ? replayBuffer.length - replayPos : 0);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException
	{
		int result;
		if(replaying)
		{
			// Do we have enough bytes left to cover it?
			if(replayBuffer.length - replayPos >= len)
			{
				System.arraycopy(replayBuffer, replayPos, b, off, len);
				replayPos += len;
				if (replayPos == replayBuffer.length)
				{
					replaying = false;
					replayBuffer = null;
				}
				result = len;
			}
			else
			{
				// Read all the rest of the buffer
				int left = replayBuffer.length - replayPos;
				System.arraycopy(replayBuffer, replayPos, b, off, left);

				// Also read from original to cover the remaining request
				int originalResult = original.read(b, off + left, len - left);
				if(originalResult <= 0)
				{
					result = left;
				}
				else
				{
					result = left + originalResult;
					if(marking)
					{
						if(markPos + originalResult <= markBuffer.length)
						{
							System.arraycopy(b, off + left, markBuffer, markPos, originalResult);
							markPos += originalResult;
						}
						else
						{
							// Abandon mark
							marking = false;
						}
					}
				}
				replaying = false;
				replayBuffer = null;
			}
		}
		else
		{
			result = original.read(b, off, len);
			if(marking && result > 0)
			{
				if(markPos + result <= markBuffer.length)
				{
					System.arraycopy(b, off, markBuffer, markPos, result);
					markPos += result;
				}
				else
				{
					// Abandon mark
					marking = false;
				}
			}
		}
		return result;
	}

	@Override
	public int read(byte[] b) throws IOException
	{
		return read(b, 0, b.length);
	}

	@Override
	public int read() throws IOException
	{
		if(replaying)
		{
			int result = replayBuffer[replayPos++];
			if(replayPos == replayBuffer.length)
			{
				replaying = false;
				replayBuffer = null;
			}
			return result;
		}
		int result = original.read();
		if(marking && result != -1)
		{
			if(markPos < markBuffer.length)
			{
				markBuffer[markPos++] = (byte)result;
			}
			else
			{
				// Abandon mark
				marking = false;
			}
		}
		return result;
	}
}
