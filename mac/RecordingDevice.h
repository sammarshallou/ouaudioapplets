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

#ifndef _RecordingDevice_H_
#define _RecordingDevice_H_

#include <Carbon/Carbon.h>
#include <AudioUnit/AudioUnit.h>
#include <AudioToolbox/AudioToolbox.h>

#include "Synchronize.h"

class RecordingDevice
{
public:
	RecordingDevice();
	char* Init();	
	char* Start();
	char* Stop();	
	char* Reset();
	virtual ~RecordingDevice();
	
	UInt32 GetSampleRate()
	{
		return fSampleRate;
	}
	bool IsStereo()
	{
		return fStereo;		
	}
	
	AudioBuffer* RetrieveBuffer();
	
	static OSStatus InputProc(void *inRefCon,
		AudioUnitRenderActionFlags *ioActionFlags,
		const AudioTimeStamp *inTimeStamp,
		UInt32 inBusNumber,
		UInt32 inNumberFrames,
		AudioBufferList * ioData);
		
	Mutex* GetMutex()
	{
		return &fMutex;
	}
	
	UInt32 GetNumBuffers()
	{
		return fNumBuffers;	
	}
	
private:	
	bool fInited;
	AudioUnit fInputUnit;
	bool fStereo;
	UInt32 fSampleRate;
	AudioBufferList** fBuffers;
	UInt32 fNumBuffers,fRecordingIndex,fPlaybackIndex;
	
	Mutex fMutex;
};

#endif // _RecordingDevice_H_
