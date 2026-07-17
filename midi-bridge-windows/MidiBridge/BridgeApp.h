#pragma once

#include "stdafx.h"
#include "UdpReceiver.h"
#include "MidiClient.h"
#include "Logger.h"

// Application class.
class BridgeApp
    : public MidiClient::MessageDelegate,
      UdpReceiver::MessageDelegate
{
public:

    BridgeApp()
        : udpReceiver(*this),
          midiClient(*this)
    {
    }

    // Main loop: automatic mode.
	void RunAutomatic()
	{
		// Initialize the MIDI client.
		midiClient.OpenAllDevices();
		midiClient.PrintDeviceList();

		Logger::Enable();
		
		// Start IPC.
		udpReceiver.SetUp();
		udpReceiver.Start();

		while (true)
		{
			GetLine();

			// Rescan and grab the MIDI devices.
			midiClient.CloseAllDevices();
			midiClient.OpenAllDevices();
		}
	}

	// Main loop: interactive mode.
	void RunInteractive()
	{
		// Initialize the MIDI client.
		midiClient.OpenAllDevices();

		// Start IPC.
		udpReceiver.SetUp();
		udpReceiver.Start();

		while (true)
		{
			// Display the current status.
			midiClient.PrintDeviceList();

			// Command line.
			puts("Enter an ID or one of the following commands: (s)can, (r)eset, (l)og, (q)uit");
			auto input = GetLine();

			if (input[0] >= '0' && input[0] <= '9')
			{
				// ID number: switch the state of the device.
				midiClient.TrySwitchState(atoi(input.c_str()));
			}
			else if (input[0] == 'r')
			{
				// Reset: rescan and grab the all MIDI devices.
				midiClient.CloseAllDevices();
				midiClient.OpenAllDevices();
			}
			else if (input[0] == 'l')
			{
				// Log: enable the logger until the user interrupts.
				puts("===================================");
				puts("Press ENTER to quit the log viewer.");
				puts("===================================");
				Logger::Enable();
				GetLine();
				Logger::Disable();
			}
			else if (input[0] == 'q')
			{
				// Quit: break the main loop.
				break;
			}
		}

		// Cleaning up.
		midiClient.CloseAllDevices();
		udpReceiver.Stop();
	}

private:

	UdpReceiver udpReceiver;
	MidiClient midiClient;
	
	// UDP -> MIDI out
      int ProcessIncomingUdpMessage(const uint8_t* data, int length)
      {
          if (length != 4)
              return 0;

          MidiMessage message(data);

          midiClient.SendMessageToDevices(message);

          Logger::RecordMidiOutput(message);

          return 4;
      }

    // MIDI in -> IPC
    void ProcessIncomingMidiMessageFromDevice(MidiMessage message) override
    {
		Logger::RecordMidiInput(message);
        // UDP bridge is output-only for now
    }

	// Utility: get a line from stdin.
	static std::string GetLine()
	{
		char input[32];
		fgets(input, sizeof(input), stdin);
		return std::string(input);
	}
};
