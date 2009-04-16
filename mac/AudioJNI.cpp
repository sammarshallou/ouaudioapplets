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

#include "AudioJNI.h"
#include "RecordingDevice.h"
#include "PlaybackDevice.h"

#define MAXDEVICES 16
RecordingDevice* recordingDevices[MAXDEVICES];
PlaybackDevice* playbackDevices[MAXDEVICES];

Mutex devSynch;

void ThrowException(JNIEnv* jnienv,char* result)
{
	jclass exc=jnienv->FindClass("uk/ac/open/audio/MacAudioException");
	if(exc != NULL)
	{
		jnienv->ThrowNew(exc,result);
	}
	jnienv->DeleteLocalRef(exc);
}

////////////
// Recording
////////////

RecordingDevice* CheckRecordingDevice(JNIEnv* jnienv, jint device)
{
	Synchronize sync(&devSynch);
	if(device < 0 || device >= MAXDEVICES || recordingDevices[device] == NULL)
	{
		ThrowException(jnienv, "Invalid device ID");
		return NULL;
	}
	return recordingDevices[device];
}

JNIEXPORT jint JNICALL Java_uk_ac_open_audio_MacAudio_recordingInit
  (JNIEnv *jnienv, jclass clazz)
{
	Synchronize sync(&devSynch);
	UInt32 dev;
	for (dev=0; dev<MAXDEVICES; dev++)
	{
		if(recordingDevices[dev] == NULL)
		{
			break;
		}
	}
	if(dev == MAXDEVICES)
	{
		ThrowException(jnienv, "No more recording devices available");
		return -1;
	}
	recordingDevices[dev]=new RecordingDevice();
	sync.Free();
	
	char* result = recordingDevices[dev]->Init();
	if(result != NULL)
	{
		Synchronize sync2(&devSynch);
		delete recordingDevices[dev];
		recordingDevices[dev] = NULL;
		sync2.Free();
		
		ThrowException(jnienv, result);
	}

	return dev;
}

JNIEXPORT jint JNICALL Java_uk_ac_open_audio_MacAudio_recordingGetRate
  (JNIEnv *jnienv, jclass clazz, jint device)
{ 
	RecordingDevice* dev=CheckRecordingDevice(jnienv,device);
	if(dev == NULL) 
	{
		return -1;
	}
	return dev->GetSampleRate();
}

JNIEXPORT jboolean JNICALL Java_uk_ac_open_audio_MacAudio_recordingIsStereo
  (JNIEnv *jnienv, jclass clazz, jint device)
{
	RecordingDevice* dev=CheckRecordingDevice(jnienv,device);
	if(dev == NULL) 
	{
		return FALSE;
	}
	return dev->IsStereo();
}

JNIEXPORT void JNICALL Java_uk_ac_open_audio_MacAudio_recordingStart
  (JNIEnv *jnienv, jclass clazz, jint device)
{
	RecordingDevice* dev=CheckRecordingDevice(jnienv,device);
	if(dev == NULL) 
	{
		return;
	}

	char* result = dev->Start();
	if(result != NULL)
	{
		ThrowException(jnienv, result);
	}
}

JNIEXPORT jbyteArray JNICALL Java_uk_ac_open_audio_MacAudio_recordingGetData
  (JNIEnv *jnienv, jclass clazz,jint device)
{
	RecordingDevice* dev=CheckRecordingDevice(jnienv,device);
	if(dev == NULL) 
	{
		return NULL;
	}
	
	// Extract all the audio buffers that are finished
	Synchronize sync(dev->GetMutex());
	AudioBuffer* buffers[dev->GetNumBuffers()];
	UInt32 i=0;
	UInt32 samples=0;
	while(true)
	{
		buffers[i]=dev->RetrieveBuffer();
		if(buffers[i]==NULL)
		{
			break;
		}
		else
		{
			samples += buffers[i]->mDataByteSize;
		}
		i++;
	}

	// Allocate an array big enough to hold all of them
	jbyteArray output = jnienv->NewByteArray(samples);
	if (output == NULL)
	{
		// OutOfMemoryException already thrown
		return NULL;
	}
	samples=0;
	for(UInt32 j=0; j<i; j++)
	{
		jnienv->SetByteArrayRegion(output, samples, buffers[j]->mDataByteSize, (jbyte*)buffers[j]->mData);
		samples += buffers[j]->mDataByteSize;
	}
	
	return output;	
}

JNIEXPORT void JNICALL Java_uk_ac_open_audio_MacAudio_recordingStop
  (JNIEnv *jnienv, jclass clazz, jint device)
{
	RecordingDevice* dev=CheckRecordingDevice(jnienv,device);
	if(dev == NULL) 
	{
		return;
	}

	char* result = dev->Stop();
	if(result != NULL)
	{
		ThrowException(jnienv, result);
	}
}

JNIEXPORT void JNICALL Java_uk_ac_open_audio_MacAudio_recordingReset
  (JNIEnv *jnienv, jclass clazz, jint device)
{
	RecordingDevice* dev=CheckRecordingDevice(jnienv,device);
	if(dev == NULL) 
	{
		return;
	}

	char* result = dev->Reset();
	if(result != NULL)
	{
		ThrowException(jnienv, result);
	}
}

JNIEXPORT void JNICALL Java_uk_ac_open_audio_MacAudio_recordingClose
  (JNIEnv *jnienv, jclass clazz, jint device)
{
	Synchronize sync(&devSynch);
	RecordingDevice* dev=CheckRecordingDevice(jnienv,device);
	if(dev == NULL) 
	{
		return;
	}
	
	delete dev;
	recordingDevices[device] = NULL;
}

///////////
// Playback
///////////

PlaybackDevice* CheckPlaybackDevice(JNIEnv* jnienv, jint device)
{
	Synchronize sync(&devSynch);
	if(device < 0 || device >= MAXDEVICES || playbackDevices[device] == NULL)
	{
		ThrowException(jnienv, "Invalid device ID");
		return NULL;
	}
	return playbackDevices[device];
}


JNIEXPORT jint JNICALL Java_uk_ac_open_audio_MacAudio_playbackInit
  (JNIEnv *jnienv, jclass clazz)
{
	Synchronize sync(&devSynch);
	UInt32 dev;
	for (dev=0; dev<MAXDEVICES; dev++)
	{
		if(playbackDevices[dev] == NULL)
		{
			break;
		}
	}
	if(dev == MAXDEVICES)
	{
		ThrowException(jnienv, "No more playback devices available");
		return -1;
	}
	playbackDevices[dev]=new PlaybackDevice();
	sync.Free();
	
	char* result = playbackDevices[dev]->Init();
	if(result != NULL)
	{
		Synchronize sync2(&devSynch);
		delete playbackDevices[dev];
		playbackDevices[dev] = NULL;
		sync2.Free();
		
		ThrowException(jnienv, result);
	}

	return dev;
}

JNIEXPORT jint JNICALL Java_uk_ac_open_audio_MacAudio_playbackGetRate
  (JNIEnv *jnienv, jclass clazz, jint device)
{
	PlaybackDevice* dev=CheckPlaybackDevice(jnienv,device);
	if(dev == NULL) 
	{
		return -1;
	}
	return dev->GetSampleRate();
}

JNIEXPORT jboolean JNICALL Java_uk_ac_open_audio_MacAudio_playbackIsStereo
  (JNIEnv *jnienv, jclass clazz, jint device)
{
	PlaybackDevice* dev=CheckPlaybackDevice(jnienv,device);
	if(dev == NULL) 
	{
		return FALSE;
	}
	return dev->IsStereo();
}

JNIEXPORT void JNICALL Java_uk_ac_open_audio_MacAudio_playbackStart
  (JNIEnv *jnienv, jclass clazz, jint device)
{
	PlaybackDevice* dev=CheckPlaybackDevice(jnienv,device);
	if(dev == NULL) 
	{
		return;
	}
	char* result = dev->Start();
	if(result != NULL)
	{
		ThrowException(jnienv, result);
	}
}

JNIEXPORT void JNICALL Java_uk_ac_open_audio_MacAudio_playbackAddData
  (JNIEnv *jnienv, jclass clazz, jint device, jbyteArray data)
{
	PlaybackDevice* dev=CheckPlaybackDevice(jnienv,device);
	if(dev == NULL) 
	{
		return;
	}
	jint len = jnienv->GetArrayLength(data); 
	
	// Get buffers to write the data
	void* data1;
	void* data2;
	UInt32 data1Bytes,data2Bytes;
	char* result = dev->RequestAdd(len,data1,data1Bytes,data2,data2Bytes);
	if(result != NULL)
	{
		ThrowException(jnienv, result);
		return;
	}
	
	// Retrieve data from Java
	jbyte* dataBytes=(jbyte*)jnienv->GetPrimitiveArrayCritical(data, NULL);
	if(dataBytes == NULL)
	{
		ThrowException(jnienv, "Error obtaining array");
		return;
	}
	
	// Copy it into the buffer
	memcpy(data1,dataBytes,data1Bytes);
	if(data2 != NULL)
	{
		memcpy(data2,&dataBytes[data1Bytes],data2Bytes);
	}
	
	// Release back to Java
	jnienv->ReleasePrimitiveArrayCritical(data,dataBytes, NULL);
	
	// Finalise buffer add
	dev->FinishAdd(len);
}

JNIEXPORT jint JNICALL Java_uk_ac_open_audio_MacAudio_playbackGetUnplayedSize
  (JNIEnv *jnienv, jclass clazz, jint device)
{
	PlaybackDevice* dev=CheckPlaybackDevice(jnienv,device);
	if(dev == NULL) 
	{
		return 0;
	}
	return dev->GetUnplayedSize();
}

JNIEXPORT void JNICALL Java_uk_ac_open_audio_MacAudio_playbackStop
  (JNIEnv *jnienv, jclass clazz, jint device)
{
	PlaybackDevice* dev=CheckPlaybackDevice(jnienv,device);
	if(dev == NULL) 
	{
		return;
	}
	char* result = dev->Stop();
	if(result != NULL)
	{
		ThrowException(jnienv, result);
	}
}

JNIEXPORT void JNICALL Java_uk_ac_open_audio_MacAudio_playbackReset
  (JNIEnv *jnienv, jclass clazz, jint device)
{
	PlaybackDevice* dev=CheckPlaybackDevice(jnienv,device);
	if(dev == NULL) 
	{
		return;
	}
	char* result = dev->Reset();
	if(result != NULL)
	{
		ThrowException(jnienv, result);
	}
}

JNIEXPORT void JNICALL Java_uk_ac_open_audio_MacAudio_playbackClose
  (JNIEnv *jnienv, jclass clazz, jint device)
{
	Synchronize sync(&devSynch);
	PlaybackDevice* dev=CheckPlaybackDevice(jnienv,device);
	if(dev == NULL) 
	{
		return;
	}
	
	delete dev;
	playbackDevices[device] = NULL;
}
