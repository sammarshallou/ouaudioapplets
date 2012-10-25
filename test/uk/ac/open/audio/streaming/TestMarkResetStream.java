package uk.ac.open.audio.streaming;

import java.io.*;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Test script for the MarkResetStream.
 */
public class TestMarkResetStream
{
	private InputStream wrap;
	private byte[] buffer;

	@Before
	public void before()
	{
		InputStream in = new ByteArrayInputStream(new byte[] {1,2,3,4,5,6,7,8,9,10});
		wrap = new MarkResetStream(in);
		buffer = new byte[5];
	}

	/**
	 * Test without actually using mark/reset.
	 * @throws Exception Any error
	 */
	@Test
	public void testBasic() throws Exception
	{
		assertEquals(1, wrap.read());
		assertEquals(3, wrap.read(buffer, 1, 3));
		assertArrayEquals(new byte[] {0, 2, 3, 4, 0}, buffer);
		assertEquals(5, wrap.read(buffer, 0, 5));
		assertArrayEquals(new byte[] {5, 6, 7, 8, 9}, buffer);
		assertEquals(1, wrap.read(buffer));
		assertArrayEquals(new byte[] {10, 6, 7, 8, 9}, buffer);
		assertEquals(-1, wrap.read());
		assertEquals(-1, wrap.read(buffer));
	}

	private static class SillyStream extends ByteArrayInputStream
	{
		private boolean closed;

		public SillyStream()
		{
			super(new byte[10]);
		}

		@Override
		public void close() throws IOException
		{
			super.close();
			closed = true;
		}
	}

	@Test
	public void testClose() throws Exception
	{
		SillyStream silly = new SillyStream();
		InputStream test = new MarkResetStream(silly);
		assertFalse(silly.closed);
		test.close();
		assertTrue(silly.closed);
	}

	@Test
	/**
	 * Test simple use of mark/reset.
	 * @throws Exception
	 */
	public void testMarkSimple() throws Exception
	{
		// Read the first
		wrap.read();
		// Mark
		wrap.mark(100);
		assertEquals(2, wrap.read());
		assertEquals(5, wrap.read(buffer));
		assertArrayEquals(new byte[] {3, 4, 5, 6, 7}, buffer);
		// Reset
		wrap.reset();
		assertEquals(4, wrap.read(buffer, 0, 4));
		assertArrayEquals(new byte[] {2, 3, 4, 5, 7}, buffer);
		assertEquals(6, wrap.read());
		assertEquals(4, wrap.read(buffer));
		assertArrayEquals(new byte[] {7, 8, 9, 10, 7}, buffer);
		assertEquals(-1, wrap.read());
		assertEquals(-1, wrap.read(buffer));
	}

	@Test
	public void testMultiReset() throws Exception
	{
		// Skip the first 5
		wrap.read(buffer);
		// Mark
		wrap.mark(100);
		// Read 3
		assertEquals(3, wrap.read(buffer, 0, 3));
		assertArrayEquals(new byte[] {6, 7, 8, 4, 5}, buffer);
		// Read same 3 individually
		wrap.reset();
		assertEquals(6, wrap.read());
		assertEquals(7, wrap.read());
		assertEquals(8, wrap.read());
		// Reset again and read as buffer
		wrap.reset();
		assertEquals(3, wrap.read(buffer, 0, 3));
		assertArrayEquals(new byte[] {6, 7, 8, 4, 5}, buffer);
		// And once more for luck
		wrap.reset();
		assertEquals(3, wrap.read(buffer, 0, 3));
		assertArrayEquals(new byte[] {6, 7, 8, 4, 5}, buffer);
	}

	@Test
	public void testResetAndReadMore() throws Exception
	{
		// Mark at start
		wrap.mark(100);
		// Read 3
		assertEquals(3, wrap.read(buffer, 0, 3));
		assertArrayEquals(new byte[] {1, 2, 3, 0, 0}, buffer);
		// Reset, this time read all 5
		wrap.reset();
		assertEquals(5, wrap.read(buffer));
		assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, buffer);
		// Reset again and read 6
		wrap.reset();
		assertEquals(1, wrap.read());
		assertEquals(5, wrap.read(buffer));
		assertArrayEquals(new byte[] {2, 3, 4, 5, 6}, buffer);
		// Reset again and read the whole thing in two lots
		wrap.reset();
		assertEquals(5, wrap.read(buffer));
		assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, buffer);
		assertEquals(5, wrap.read(buffer));
		assertArrayEquals(new byte[] {6, 7, 8, 9, 10}, buffer);
		assertEquals(-1, wrap.read());
		assertEquals(-1, wrap.read(buffer));
	}

	@Test
	public void testFailedReset() throws Exception
	{
		// Check you get an exception for reset without mark
		try
		{
			wrap.reset();
			fail();
		}
		catch(IOException e)
		{
		}
		// Or for mark if you go over the limit
		wrap.mark(2);
		wrap.read(buffer, 0, 2);
		// OK to reset now...
		wrap.reset();
		wrap.read(buffer, 0, 3);
		assertArrayEquals(new byte[] {1, 2, 3, 0, 0}, buffer);
		try
		{
			// ...but not now
			wrap.reset();
			fail();
		}
		catch(IOException e)
		{
		}
	}
}
