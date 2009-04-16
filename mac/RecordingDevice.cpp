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

#include "RecordingDevice.h"

RecordingDevice::RecordingDevice()
{
	fInited = false;
	fBuffers = NULL;
	fRecordingIndex = 0;
	fPlaybackIndex = 0;
}

char* RecordingDevice::Init()
{
	Synchronize sync(&fMutex);
	
	if(fInited) return "Already inited";
    OSStatus err = noErr;
	
	// Find component
    Component comp;
    ComponentDescription desc;
    desc.componentType = kAudioUnitType_Output;
    desc.componentSubType = kAudioUnitSubType_HALOutput;
    desc.componentManufacturer = kAudioUnitManufacturer_Apple;
    desc.componentFlags = 0;
    desc.componentFlagsMask = 0;
    comp = FindNextComponent(NULL, &desc);
    if (comp == NULL) return "Failed to find audio component";

    // Open component
    err = OpenAComponent(comp, &fInputUnit);
	if(err != noErr) return "Failed to open audio component";

	// Enable input and disable output
	UInt32 enableIO;
    enableIO = 1;
    err = AudioUnitSetProperty(fInputUnit,
		kAudioOutputUnitProperty_EnableIO,
        kAudioUnitScope_Input,
		1, // input element
		&enableIO,
		sizeof(enableIO));
    if(err != noErr) return "Failed to enable audio component input";
	enableIO = 0;
	err = AudioUnitSetProperty(fInputUnit,
		kAudioOutputUnitProperty_EnableIO,
		kAudioUnitScope_Output,
		0,   //output element
		&enableIO,
		sizeof(enableIO));
    if(err != noErr) return "Failed to disable audio component output";
	
	// Find default device and set our component to use it
    UInt32 size;
    size = sizeof(AudioDeviceID);
    AudioDeviceID inputDevice;
    err = AudioHardwareGetProperty(kAudioHardwarePropertyDefaultInputDevice,
		&size, &inputDevice);
    if (err != noErr) return "Failed to get default input device";
    err = AudioUnitSetProperty(fInputUnit,
		kAudioOutputUnitProperty_CurrentDevice,
		kAudioUnitScope_Global,
		0,
		&inputDevice,
		sizeof(inputDevice));
    if (err != noErr) return "Failed to set input device to default";

    // Get the input device format
	AudioStreamBasicDescription deviceFormat;
    size = sizeof(AudioStreamBasicDescription);
    err = AudioUnitGetProperty(fInputUnit,
		kAudioUnitProperty_StreamFormat,
		kAudioUnitScope_Input,
		1,
		&deviceFormat,
		&size);
    if (err != noErr) return "Failed to get device audio format";

    // Set up the desired format
    AudioStreamBasicDescription desiredFormat;
	desiredFormat.mChannelsPerFrame = deviceFormat.mChannelsPerFrame == 1 ? 1 :  2;
    desiredFormat.mSampleRate =  deviceFormat.mSampleRate;
	desiredFormat.mFormatID = kAudioFormatLinearPCM;
	desiredFormat.mFormatFlags = kAudioFormatFlagIsSignedInteger | kAudioFormatFlagIsPacked;
	desiredFormat.mBitsPerChannel = 16;
	desiredFormat.mBytesPerFrame = desiredFormat.mChannelsPerFrame * desiredFormat.mBitsPerChannel / 8;
	desiredFormat.mFramesPerPacket = 1;
	desiredFormat.mBytesPerPacket = desiredFormat.mBytesPerFrame;
	
	fStereo = desiredFormat.mChannelsPerFrame == 2;
	fSampleRate = desiredFormat.mSampleRate;
	
     // Select format for device
    err = AudioUnitSetProperty(fInputUnit,
		kAudioUnitProperty_StreamFormat,
		kAudioUnitScope_Output,
		1,
		&desiredFormat,
		sizeof(AudioStreamBasicDescription));
    if (err != noErr) return "Failed to set device audio format";
	
	// Setup callback
    AURenderCallbackStruct callback;
    callback.inputProc = InputProc;
    callback.inputProcRefCon = this;
    err = AudioUnitSetProperty(
		fInputUnit,
		kAudioOutputUnitProperty_SetInputCallback,
		kAudioUnitScope_Global,
		0,
		&callback,
		sizeof(callback));
    if (err != noErr) return "Failed to set callback";
	
	// Get the number of frames required for IO buffer
	size = sizeof(UInt32);
	UInt32 bufferFrames;
	err = AudioUnitGetProperty(fInputUnit, 
		kAudioDevicePropertyBufferFrameSize,
		kAudioUnitScope_Global, 
		0, 
		&bufferFrames, 
		&size);
	if (err != noErr) return "Failed to get audio buffer size";
	
	// Work out size and number of buffers
	UInt32 bufferSize = bufferFrames * desiredFormat.mBytesPerFrame;
	fNumBuffers = (desiredFormat.mBytesPerFrame * desiredFormat.mSampleRate / 2) / bufferSize;

	// Allocate buffers
	fBuffers=(AudioBufferList**)calloc(fNumBuffers, sizeof(AudioBufferList*));
	if(fBuffers == NULL) return "Failed to allocate buffer list set";	
	for(UInt32 i=0; i<fNumBuffers; i++)
	{
		fBuffers[i] = (AudioBufferList*)calloc(1, sizeof(AudioBufferList) + sizeof(AudioBuffer));
		if (fBuffers[i] == NULL) return "Failed to allocate buffer list";
		
		fBuffers[i]->mNumberBuffers = 1;
		fBuffers[i]->mBuffers[0].mNumberChannels = desiredFormat.mChannelsPerFrame;
		fBuffers[i]->mBuffers[0].mDataByteSize = bufferSize;
		fBuffers[i]->mBuffers[0].mData = malloc(bufferSize);
		if(fBuffers[i]->mBuffers[0].mData == NULL) 
		{
			return "Failed to allocate buffer";
		}
	}
	
	// Initialise
	err = AudioUnitInitialize(fInputUnit);
	if (err != noErr) return "Failed to initialize";

	// OK!
	fInited=true;
	return NULL;	
}

char* RecordingDevice::Start()
{
	Synchronize sync(&fMutex);
	OSStatus err = AudioOutputUnitStart(fInputUnit);
	if(err != noErr) return "Error starting";
	return NULL;
}

char* RecordingDevice::Stop()
{
	Synchronize sync(&fMutex);
	OSStatus err = AudioOutputUnitStop(fInputUnit);
	if(err != noErr) return "Error stopping";
	return NULL;
}

char* RecordingDevice::Reset()
{
	Synchronize sync(&fMutex);
	OSStatus err = AudioUnitReset(fInputUnit, 
		kAudioUnitScope_Global, 
		0);
	if(err != noErr) return "Error stopping";
	return NULL;
}

RecordingDevice::~RecordingDevice()
{
	Synchronize sync(&fMutex);
	// Close init
	if (fInited)
	{
		AudioUnitUninitialize(fInputUnit);
		CloseComponent(fInputUnit);
		fInputUnit = NULL;	
	}
	
	// Free buffers
	if (fBuffers != NULL)
	{
		for (UInt32 buffer=0; buffer<fNumBuffers; buffer++)
		{
			for (UInt32 i=0; i<fBuffers[buffer]->mNumberBuffers; i++)
			{
				if (fBuffers[buffer]->mBuffers[i].mData != NULL)
				{
					free(fBuffers[buffer]->mBuffers[i].mData);
				}
			}
			free(fBuffers[buffer]);
		}
		free(fBuffers);
	}
	
	sync.Free();
}

OSStatus RecordingDevice::InputProc(void *inRefCon,
	AudioUnitRenderActionFlags *ioActionFlags,
	const AudioTimeStamp *inTimeStamp,
	UInt32 inBusNumber,
	UInt32 inNumberFrames,
	AudioBufferList * ioData)
{
	RecordingDevice *This = (RecordingDevice*)inRefCon;

	// Render into audio buffer
	OSStatus err = AudioUnitRender(
		This->fInputUnit, 
		ioActionFlags, 
		inTimeStamp, 
		inBusNumber, 
		inNumberFrames, 
		This->fBuffers[This->fRecordingIndex]);

	// Move onto next buffer in circle
	Synchronize sync(&This->fMutex);
	UInt32 nextIndex=(This->fRecordingIndex+1)%This->fNumBuffers;
	if(This->fPlaybackIndex == nextIndex)
	{
		This->fPlaybackIndex = (This->fPlaybackIndex+1)%This->fNumBuffers;
	}
	This->fRecordingIndex = nextIndex;

	return err;
}
	
AudioBuffer* RecordingDevice::RetrieveBuffer()
{
	Synchronize sync(&fMutex);
	// Check there's something to play
	if(fRecordingIndex == fPlaybackIndex)
	{
		return NULL;
	}
	// Get buffer and update pointer
	AudioBuffer* result=&fBuffers[fPlaybackIndex]->mBuffers[0];
	fPlaybackIndex = (fPlaybackIndex+1) % fNumBuffers;
	return result;
}
