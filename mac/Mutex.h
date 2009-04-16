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

#ifndef _Mutex_H_
#define _Mutex_H_

#include <pthread.h>

class Mutex
{
public:
	Mutex()
	{
		pthread_mutexattr_t attr;
		pthread_mutexattr_init(&attr);
		pthread_mutexattr_settype(&attr,PTHREAD_MUTEX_RECURSIVE);
		pthread_mutex_init(&fMutex, &attr);
		pthread_mutexattr_destroy(&attr);
	}
	~Mutex()
	{
		pthread_mutex_destroy(&fMutex);
	}
	pthread_mutex_t* GetMutex()
	{
		return &fMutex;
	}
private:
	pthread_mutex_t fMutex;
};

#endif // _Mutex_H_
