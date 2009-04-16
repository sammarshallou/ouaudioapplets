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
package uk.ac.open.audio;

/**
 * Thread that can be closed.
 */
public abstract class ClosableThread extends Thread
{
	private boolean close,closed;
	private Object sync;

	/**
	 * @param name Thread name
	 * @param sync Object to synchronize and send notify() events on
	 */
	protected ClosableThread(String name, Object sync)
	{
		super(name);
		this.sync=sync;
	}

	/** Closes thread, blocking until it exits. */
	public void close()
	{
		synchronized(sync)
		{
			close=true;
			sync.notifyAll();
			while(!closed)
			{
				try
				{
					sync.wait();
				}
				catch (InterruptedException e)
				{
				}
			}
		}
	}

	@Override
	public final void run()
	{
		try
		{
			runInner();
		}
		finally
		{
			synchronized(sync)
			{
				closed=true;
				sync.notifyAll();
			}
		}
	}

	/** @return True if thread should close itself. */
	protected boolean shouldClose()
	{
		return close;
	}

	/** Thread main method; should periodically check {@link #shouldClose}. */
	protected abstract void runInner();
}