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

#ifndef _PlaybackDevice_H_
#define _PlaybackDevice_H_

#include <Carbon/Carbon.h>
#include <AudioUnit/AudioUnit.h>
#include <AudioToolbox/AudioToolbox.h>

#include "Synchronize.h"
#include "DataBuffer.h"

class PlaybackDevice
{
public:
	PlaybackDevice();
	char* Init();	
	char* Start();
	char* RequestAdd(UInt32 bytes, void* &data1, UInt32 &data1Size, void* &data2, UInt32 &data2Size);
	void FinishAdd(UInt32 bytes);
	UInt32 GetUnplayedSize();	
	char* Stop();	
	char* Reset();
	virtual ~PlaybackDevice();
	
	UInt32 GetSampleRate()
	{
		return fSampleRate;
	}
	bool IsStereo()
	{
		return fStereo;		
	}
	
	static OSStatus InputProc(void *inRefCon,
		AudioUnitRenderActionFlags *ioActionFlags,
		const AudioTimeStamp *inTimeStamp,
		UInt32 inBusNumber,
		UInt32 inNumberFrames,
		AudioBufferList * ioData);
		
private:	
	const static int MAXBUFFERS=128;

	bool fInited;
	AudioUnit fOutputUnit;
	bool fStereo;
	UInt32 fSampleRate;
	
	Mutex fMutex;
	
	UInt8* fBuffer;
	UInt32 fBufferSize;
	UInt32 fReadPos, fWritePos;
	
	void FillBuffer(UInt8* target,UInt32 size);
};

#endif // _PlaybackDevice_H_
