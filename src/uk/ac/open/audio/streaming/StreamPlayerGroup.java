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

import java.util.*;

/**
 * Manages a group of StreamPlayers to ensure they don't have two playing at
 * once.
 */
public class StreamPlayerGroup
{
	private HashSet<StreamPlayerUI> players=new HashSet<StreamPlayerUI>();
	private LinkedList<Listener> listeners=new LinkedList<Listener>();

	/** If listening to events (outside of StreamPlayerUIs) */
	public interface Listener
	{
		/**
		 * Called just before a new player starts.
		 * @param player Player that is starting
		 */
		public void playerStarting(StreamPlayerUI player);
	}

	/**
	 * Adds a player to the group.
	 * @param player Player to add
	 */
	public synchronized void add(StreamPlayerUI player)
	{
		players.add(player);
	}

	/**
	 * Indicates that one player is starting, so others should stop.
	 * @param player Player that is starting
	 */
	public synchronized void starting(StreamPlayerUI player)
	{
		for(StreamPlayerUI other : players)
		{
			if(other!=player) other.stop(true,true);
		}
		for(Listener l : listeners)
		{
			l.playerStarting(player);
		}
	}

	/**
	 * Adds other listener.
	 * @param l Listener
	 */
	public synchronized void addListener(Listener l)
	{
		listeners.add(l);
	}

	/**
	 * Removes other listener.
	 * @param l Listener
	 */
	public synchronized void removeListener(Listener l)
	{
		listeners.remove(l);
	}
}
