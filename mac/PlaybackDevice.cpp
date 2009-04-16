/*
 *  PlaybackDevice.cpp
 *  osxaudio
 *
 *  Created by Samuel Marshall on 26/03/2009.
 *  Copyright 2009 __MyCompanyName__. All rights reserved.
 *
 *  Apple have some halfhearted, and impossible-to-find, documentation on this
 *  area at http://developer.apple.com/technotes/tn2004/tn2097.html
 *  
 *  This should probably really be implemented with an AudioGraph which could
 *  then do format conversion as well, but I haven't got time for that.
 */

#include "PlaybackDevice.h"

PlaybackDevice::PlaybackDevice()
{
	fInited = false;
	fBuffer = NULL;
	fReadPos = 0;
	fWritePos = 0;
}

char* PlaybackDevice::Init()
{
	UInt32 size;
	
	Synchronize sync(&fMutex);
	
	if(fInited) return "Already inited";
    OSStatus err = noErr;
	
	// Find component
    Component comp;
    ComponentDescription desc;
    desc.componentType = kAudioUnitType_Output;
    desc.componentSubType = kAudioUnitSubType_DefaultOutput;
    desc.componentManufacturer = kAudioUnitManufacturer_Apple;
    desc.componentFlags = 0;
    desc.componentFlagsMask = 0;
    comp = FindNextComponent(NULL, &desc);
    if (comp == NULL) return "Failed to find audio component";

    // Open component
    err = OpenAComponent(comp, &fOutputUnit);
	if(err != noErr) return "Failed to open audio component";
/*
	// Enable output and disable input
	UInt32 enableIO;
    enableIO = 0;
    err = AudioUnitSetProperty(fOutputUnit,
		kAudioOutputUnitProperty_EnableIO,
        kAudioUnitScope_Input,
		1, // input element
		&enableIO,
		sizeof(enableIO));
    if(err != noErr) return "Failed to disable audio component input";
	enableIO = 1;
	err = AudioUnitSetProperty(fOutputUnit,
		kAudioOutputUnitProperty_EnableIO,
		kAudioUnitScope_Output,
		0,   //output element
		&enableIO,
		sizeof(enableIO));
    if(err != noErr) return "Failed to enable audio component output";
*/	
    // Get the device format
	AudioStreamBasicDescription deviceFormat;
    size = sizeof(AudioStreamBasicDescription);
    err = AudioUnitGetProperty(fOutputUnit,
		kAudioUnitProperty_StreamFormat,
		kAudioUnitScope_Output,
		0,
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
    err = AudioUnitSetProperty(fOutputUnit,
		kAudioUnitProperty_StreamFormat,
		kAudioUnitScope_Input,
		0,
		&desiredFormat,
		sizeof(AudioStreamBasicDescription));
    if (err != noErr) return "Failed to set device audio format";
	
	// Setup callback
    AURenderCallbackStruct callback;
    callback.inputProc = InputProc;
    callback.inputProcRefCon = this;
    err = AudioUnitSetProperty(
		fOutputUnit,
		kAudioUnitProperty_SetRenderCallback,
		kAudioUnitScope_Input,
		0,
		&callback,
		sizeof(callback));
    if (err != noErr) return "Failed to set callback";
/*	
	// Get the number of frames required for IO buffer
	size = sizeof(UInt32);
	UInt32 bufferFrames;
	err = AudioUnitGetProperty(fOutputUnit, 
		kAudioDevicePropertyBufferFrameSize,
		kAudioUnitScope_Global, 
		0, 
		&bufferFrames, 
		&size);
	if (err != noErr) return "Failed to get audio buffer size";
*/	
	// Initialise
	err = AudioUnitInitialize(fOutputUnit);
	if (err != noErr) return "Failed to initialize";

	fInited=true;

	// Allocate buffer for 1.5 seconds
	fBufferSize = 3 * fSampleRate * (fStereo ? 2 : 1);
	fBuffer = (UInt8*)malloc(fBufferSize);
	
	// OK!
	return NULL;	
}

char* PlaybackDevice::Start()
{
	Synchronize sync(&fMutex);
	OSStatus err = AudioOutputUnitStart(fOutputUnit);
	if(err != noErr) return "Error starting";
	return NULL;
}

char* PlaybackDevice::RequestAdd(UInt32 bytes, void* &data1, UInt32 &data1Size, void* &data2, UInt32 &data2Size)
{
	Synchronize sync(&fMutex);
	
	// Work out whethere there's space by notionally 'unrolling' the buffer read pos
	UInt32 unrolledReadPos = fReadPos;
	if (unrolledReadPos <= fWritePos)
	{
		unrolledReadPos += fBufferSize;
	}
	if (fWritePos + bytes >= unrolledReadPos)
	{
		return "Insufficient buffer space";
	}
	
	// Does all the data fit in the remainder of the circular buffer?
	data1 = &fBuffer[fWritePos];
	if(fWritePos + bytes <= fBufferSize)
	{
		data1Size = bytes;
		data2 = NULL;
		data2Size = 0;
		return NULL;
	}
	
	// Nope, so overlap round to the front of the buffer
	data1Size = fBufferSize - fWritePos;
	data2 = fBuffer;
	data2Size = bytes - data1Size;
	return NULL;
}

void PlaybackDevice::FinishAdd(UInt32 bytes)
{
	Synchronize sync(&fMutex);
	
	fWritePos += bytes;
	if(fWritePos > fBufferSize)
	{
		fWritePos -= fBufferSize;
	}	
}

UInt32 PlaybackDevice::GetUnplayedSize()
{
	Synchronize sync(&fMutex);

	UInt32 unrolledWritePos = fWritePos;
	if (unrolledWritePos < fReadPos)
	{
		unrolledWritePos += fBufferSize;
	}
	
	return unrolledWritePos - fReadPos;
}

char* PlaybackDevice::Stop()
{
	Synchronize sync(&fMutex);
	OSStatus err = AudioOutputUnitStop(fOutputUnit);
	if(err != noErr) return "Error stopping";
	return NULL;
}

char* PlaybackDevice::Reset()
{
	Synchronize sync(&fMutex);
	OSStatus err = AudioUnitReset(fOutputUnit, 
		kAudioUnitScope_Global, 
		0);
	if(err != noErr) return "Error resetting";

	fReadPos = 0;
	fWritePos = 0;
	return NULL;
}

PlaybackDevice::~PlaybackDevice()
{
	Synchronize sync(&fMutex);
	// Close init
	if (fInited)
	{
		AudioUnitUninitialize(fOutputUnit);
		CloseComponent(fOutputUnit);
		fOutputUnit = NULL;	
	}
	
	if(fBuffer != NULL)
	{
		free(fBuffer);
	}
}

// This function must be called within synchronization
void PlaybackDevice::FillBuffer(UInt8* target, UInt32 bytes)
{
	// Work out whethere there's space by notionally 'unrolling' the buffer write pos
	UInt32 unrolledWritePos = fWritePos;
	if (unrolledWritePos < fReadPos)
	{
		unrolledWritePos += fBufferSize;
	}
	if (fReadPos + bytes > unrolledWritePos)
	{
		// Use partial buffer, set the rest to 0
		UInt32 available = unrolledWritePos - fReadPos;
#ifdef DEBUG_MODE
		fprintf(stderr, "Playback buffer ran out (available %i, required %i)\n",available,bytes);
#endif // DEBUG_MODE	
		if(available > 0)
		{
			FillBuffer(target,available);
		}
		memset(&target[available], 0, bytes-available);
		return;
	}
	
	// Can we do this in one go?
	if(fReadPos + bytes <= fBufferSize)
	{
		memcpy(target, &fBuffer[fReadPos], bytes);
		fReadPos += bytes;
		if(fReadPos == fBufferSize)
		{
			fReadPos = 0;
		}
		return;
	}
	
	// Can't do in one go, need to overlap to front of buffer
	UInt32 firstBytes = fBufferSize - fReadPos;
	memcpy(target, &fBuffer[fReadPos], firstBytes);
	memcpy(&target[firstBytes], fBuffer, bytes - firstBytes);
	fReadPos = bytes - firstBytes;
}

OSStatus PlaybackDevice::InputProc(void *inRefCon,
	AudioUnitRenderActionFlags *ioActionFlags,
	const AudioTimeStamp *inTimeStamp,
	UInt32 inBusNumber,
	UInt32 inNumberFrames,
	AudioBufferList * ioData)
{
	PlaybackDevice *This = (PlaybackDevice*)inRefCon;
	Synchronize sync(&This->fMutex);
	
	for(UInt32 i=0;i<ioData->mNumberBuffers;i++)
	{
		This->FillBuffer((UInt8*)(ioData->mBuffers[i].mData),ioData->mBuffers[i].mDataByteSize);
	}

	return noErr;
}
	
