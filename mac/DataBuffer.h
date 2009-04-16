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

class DataBuffer
{
public:
	DataBuffer(void* data, UInt32 size) { this->fData = data; this->fSize = size; }
	virtual ~DataBuffer() { free(fData); }
	UInt32 GetSize() { return fSize; }
	void* GetData() { return fData; }
	
private:
	void* fData;
	UInt32 fSize;
};