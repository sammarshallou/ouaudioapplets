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
 
#ifndef _Synchronize_H_
#define _Synchronize_H_
 
#include "Mutex.h"

class Synchronize
{
public:
	Synchronize(Mutex* pMutex)
	{
		fMutex=pMutex;
		pthread_mutex_lock(fMutex->GetMutex());
	}
	
	void Free()
	{
		if(fMutex != NULL)
		{
			pthread_mutex_unlock(fMutex->GetMutex());
			fMutex = NULL;
		}
	}
	
	~Synchronize()
	{
		Free();
	}
	
private:
	Mutex *fMutex;
};

#endif // _Synchronize_H_
