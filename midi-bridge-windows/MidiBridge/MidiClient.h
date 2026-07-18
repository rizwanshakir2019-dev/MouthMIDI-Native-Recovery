#pragma once

#include "stdafx.h"
#include "Debug.h"
#include "Logger.h"
#include "MidiMessage.h"

// MIDI interface client class.
class MidiClient
{
public:

    // Delegate class used for handling incoming MIDI messages.
    class MessageDelegate
    {
    public:
        virtual void ProcessIncomingMidiMessageFromDevice(MidiMessage message) = 0;
    };

    // Constructor/destructor.
    MidiClient(MessageDelegate& md)
        : messageDelegate(md)
    {
    }

    ~MidiClient()
    {
        CloseAllDevices();
    }

	void TrySwitchState(int id)
	{
		std::lock_guard<std::mutex> gurad(handleMutex);

		int inDeviceCount = midiInGetNumDevs();
		int outDeviceCount = midiOutGetNumDevs();

		if (id <= 0 || id > inDeviceCount + outDeviceCount) {
			// Invalid ID.
			return;
		}

		if (id <= inDeviceCount) {
			id = id - 1;
			if (CheckInputDeviceOpened(id))
			{
				// Try to close the device.
				CloseInputDevice(id);
			}
			else
			{
				// Try to open the device.
				TryOpenInputDevice(id);
			}
		}
		else
		{
			id = id - 1 - inDeviceCount;
			if (CheckOutputDeviceOpened(id))
			{
				// Try to close the device.
				CloseOutputDevice(id);
			}
			else
			{
				// Try to open the device.
				TryOpenOutputDevice(id);
			}
		}
	}

	// Print the device list.
	void PrintDeviceList()
	{
		std::lock_guard<std::mutex> gurad(handleMutex);

		// Header.
		puts("----+--------+--------------+----------------------------------");
		puts(" ID |  TYPE  |    STATUS    | DEVICE NAME");
		puts("----+--------+--------------+----------------------------------");

		// Input devices.
		auto inDeviceCount = midiInGetNumDevs();
		for (auto i = 0U; i < inDeviceCount; i++)
		{
			MIDIINCAPS caps;
			auto result = midiInGetDevCaps(i, &caps, sizeof(caps));
			Debug::Assert(result == MMSYSERR_NOERROR, "Failed to retrieve the device caps.");
			bool opened = CheckInputDeviceOpened(i);
			wprintf(L" %2d | Input  | %-12s | %-32s\n", i + 1, opened ? L"Active" : L"", caps.szPname);
		}

		puts("----+--------+--------------+----------------------------------");

		// Output devices.
		auto outDeviceCount = midiOutGetNumDevs();
		for (auto i = 0U; i < outDeviceCount; i++)
		{
			MIDIOUTCAPS caps;
			auto result = midiOutGetDevCaps(i, &caps, sizeof(caps));
			Debug::Assert(result == MMSYSERR_NOERROR, "Failed to retrieve the device caps.");
			bool opened = CheckOutputDeviceOpened(i);
			wprintf(L" %2d | Output | %-12s | %-32s\n", i + 1 + inDeviceCount, opened ? L"Active" : L"", caps.szPname);
		}

		puts("----+--------+--------------+----------------------------------");
	}

    // Try to open the all devices.
    // Try to open the all devices.
    void OpenAllDevices()
    {
                std::lock_guard<std::mutex> gurad(handleMutex);

                auto inDeviceCount = midiInGetNumDevs();
                for (auto i = 0U; i < inDeviceCount; i++)
                {
                        TryOpenInputDevice(i);
                }

                auto outDeviceCount = midiOutGetNumDevs();

                if (selectedOutputDevice >= 0)
                {
                        TryOpenOutputDevice((UINT)selectedOutputDevice);

                        MIDIOUTCAPS caps;
                        if (midiOutGetDevCaps(
                                selectedOutputDevice,
                                &caps,
                                sizeof(caps)
                        ) == MMSYSERR_NOERROR)
                        {
                                wprintf(
                                        L"Reopening output device: %s\n",
                                        caps.szPname
                                );
                        }
                }
                else
                {
                        puts("");
                        puts("Available MIDI Outputs");
                        puts("----------------------");

                        for (auto i = 0U; i < outDeviceCount; i++)
                        {
                                MIDIOUTCAPS caps;

                                if (midiOutGetDevCaps(i, &caps, sizeof(caps)) == MMSYSERR_NOERROR)
                                {
                                        wprintf(L"%u. %s\n", i + 1, caps.szPname);
                                }
                        }

                        puts("");

                        int selection = 1;

                        printf("Select output device [1-%u]: ", (unsigned)outDeviceCount);
                        scanf_s("%d", &selection);
                        getchar();

                        if (selection < 1) selection = 1;
                        if (selection > (int)outDeviceCount) selection = (int)outDeviceCount;

                        UINT deviceId = (UINT)(selection - 1);

                        selectedOutputDevice = deviceId;

                        TryOpenOutputDevice(deviceId);

                        MIDIOUTCAPS caps;
                        if (midiOutGetDevCaps(deviceId, &caps, sizeof(caps)) == MMSYSERR_NOERROR)
                        {
                                wprintf(L"Selected output: %s\n", caps.szPname);
                        }
                }
    }


    // Close the all devices opened by this client.
    void CloseAllDevices()
    {
		std::lock_guard<std::mutex> gurad(handleMutex);
	
		for (auto& handle : inDeviceHandles)
        {
            midiInClose(handle);
        }
        inDeviceHandles.clear();

        for (auto& handle : outDeviceHandles)
        {
            midiOutClose(handle);
        }
        outDeviceHandles.clear();
    }

    // Send a MIDI message to the all output devices.
    void SendMessageToDevices(MidiMessage message)
    {
		// Only send if the mutex isn't locked.
		if (handleMutex.try_lock())
		{
			for (auto& handle : outDeviceHandles)
			{
				MMRESULT result = midiOutShortMsg(
                                      handle,
                                      message.GetRaw32()
                                  );

                                  printf(
                                      "midiOutShortMsg -> result=%d raw=0x%08X\n",
                                      result,
                                      message.GetRaw32()
                                  );
			}
			handleMutex.unlock();
		}
    }

private:

    MessageDelegate& messageDelegate;
    std::vector<HMIDIIN> inDeviceHandles;
    std::vector<HMIDIOUT> outDeviceHandles;
	std::mutex handleMutex;

    int selectedOutputDevice = -1;

	// Check if the device is already opened.
	bool CheckInputDeviceOpened(int id)
	{
		for (auto handle : inDeviceHandles)
		{
			UINT idFromHandle;
			if (midiInGetID(handle, &idFromHandle) == MMSYSERR_NOERROR)
			{
				if (idFromHandle == id) return true;
			}
		}
		return false;
	}

        bool CheckOutputDeviceOpened(int id)
        {
                for (auto handle : outDeviceHandles)
                {
                        UINT idFromHandle;

                        MMRESULT result =
                                midiOutGetID(handle, &idFromHandle);

                        printf("CheckOutputDeviceOpened(%d): result=%u idFromHandle=%u\\n",
                                id,
                                (unsigned)result,
                                (unsigned)idFromHandle);

                        if (result == MMSYSERR_NOERROR)
                        {
                                if ((int)idFromHandle == id)
                                        return true;
                        }
                }

                return false;
        }

	// Try to open an device.
	bool TryOpenInputDevice(UINT id)
	{
		HMIDIIN handle;
                DWORD_PTR callback = reinterpret_cast<DWORD_PTR>(MidiInProc);
                DWORD_PTR instance = reinterpret_cast<DWORD_PTR>(&messageDelegate);
		if (midiInOpen(&handle, id, callback, instance, CALLBACK_FUNCTION) == MMSYSERR_NOERROR)
		{
			if (midiInStart(handle) == MMSYSERR_NOERROR)
			{
				inDeviceHandles.push_back(handle);
				return true;
			}
			midiInClose(handle);
		}
		return false;
	}

	bool TryOpenOutputDevice(UINT id)
	{
                HMIDIOUT handle;

                MMRESULT result =
                        midiOutOpen(&handle, id, 0, 0, CALLBACK_NULL);

                printf("TryOpenOutputDevice(%u) -> %u\n",
                        id,
                        (unsigned)result);

                if (result == MMSYSERR_NOERROR)
                {
                        outDeviceHandles.push_back(handle);

                        MIDIOUTCAPS caps;
                        midiOutGetDevCaps(id, &caps, sizeof(caps));

                        wprintf(
                                L"Opened output device %u : %s\n",
                                id,
                                caps.szPname
                        );

                        return true;
                }

                return false;
	}

	// Try to close the device.
	void CloseInputDevice(int id)
	{
		for (auto handleItr = inDeviceHandles.begin(); handleItr != inDeviceHandles.end(); ++handleItr)
		{
			UINT idFromHandle;
			if (midiInGetID(*handleItr, &idFromHandle) == MMSYSERR_NOERROR)
			{
				if (idFromHandle == id)
				{
					midiInStop(*handleItr);
					midiInClose(*handleItr);
					inDeviceHandles.erase(handleItr);
					break;
				}
			}
		}
	}

	void CloseOutputDevice(int id)
	{
		for (auto handleItr = outDeviceHandles.begin(); handleItr != outDeviceHandles.end(); ++handleItr)
		{
			UINT idFromHandle;
			if (midiOutGetID(*handleItr, &idFromHandle) == MMSYSERR_NOERROR)
			{
				if (idFromHandle == id)
				{
					midiOutClose(*handleItr);
					outDeviceHandles.erase(handleItr);
					break;
				}
			}
		}
	}

	// MIDI callback function.
    static void CALLBACK MidiInProc(HMIDIIN hMidiIn, UINT wMsg, DWORD_PTR dwInstance, DWORD_PTR dwParam1, DWORD_PTR dwParam2)
    {
        if (wMsg == MIM_DATA)
        {
            auto md = reinterpret_cast<MessageDelegate*>(dwInstance);
            md->ProcessIncomingMidiMessageFromDevice(MidiMessage(dwParam1));
        }
        else if (wMsg == MIM_CLOSE)
        {
			Logger::RecordMisc("Device (%0x) was disconnected.", hMidiIn);
        }
    }
};
