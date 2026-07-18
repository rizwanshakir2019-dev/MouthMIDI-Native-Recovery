#pragma once

#include "stdafx.h"
#include "Debug.h"
#include "MidiMessage.h"

class UdpReceiver
{
public:

    static const int portNumber = 52365;

    class MessageDelegate
    {
    public:
        virtual int ProcessIncomingUdpMessage(
            const uint8_t* data,
            int length
        ) = 0;
    };


    UdpReceiver(MessageDelegate& md)
        : messageDelegate(md)
    {
        socketHandle = INVALID_SOCKET;
        receiverThread = nullptr;
        stopReceiverThread = false;
    }


    ~UdpReceiver()
    {
        Stop();
    }


    void SetUp()
    {
        socketHandle = socket(
            AF_INET,
            SOCK_DGRAM,
            IPPROTO_UDP
        );

        Debug::Assert(
            socketHandle != INVALID_SOCKET,
            "Failed creating UDP socket"
        );


        sockaddr_in addr;
        memset(&addr, 0, sizeof(addr));

        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = INADDR_ANY;
        addr.sin_port = htons(portNumber);


        int result = bind(
            socketHandle,
            (sockaddr*)&addr,
            sizeof(addr)
        );


        Debug::Assert(
            result != SOCKET_ERROR,
            "Failed binding UDP socket"
        );
    }


    void Start()
    {
        stopReceiverThread = false;

        receiverThread = CreateThread(
            nullptr,
            0,
            ReceiverThreadEntry,
            this,
            0,
            nullptr
        );
    }


    void Stop()
    {
        stopReceiverThread = true;

        if(socketHandle != INVALID_SOCKET)
        {
            closesocket(socketHandle);
            socketHandle = INVALID_SOCKET;
        }

        if(receiverThread)
        {
            WaitForSingleObject(
                receiverThread,
                INFINITE
            );

            receiverThread = nullptr;
        }
    }


private:

    MessageDelegate& messageDelegate;

    SOCKET socketHandle;

    HANDLE receiverThread;

    bool stopReceiverThread;


    void RunReceiverLoop()
    {
        uint8_t buffer[4];


        while(!stopReceiverThread)
        {
            sockaddr_in sender;
            int senderSize = sizeof(sender);


            int length = recvfrom(
                socketHandle,
                (char*)buffer,
                sizeof(buffer),
                0,
                (sockaddr*)&sender,
                &senderSize
            );

            printf(
                "UDP packet received: length=%d",
                length
            );

            for(int i = 0; i < length; i++)
            {
                printf(" %02X", buffer[i]);
            }

            printf("\n");


            if(length == 4)
            {
                messageDelegate.ProcessIncomingUdpMessage(
                    buffer,
                    length
                );
            }
        }
    }


    static DWORD WINAPI ReceiverThreadEntry(
        LPVOID param
    )
    {
        UdpReceiver* receiver =
            reinterpret_cast<UdpReceiver*>(param);

        receiver->RunReceiverLoop();

        return 0;
    }

};
