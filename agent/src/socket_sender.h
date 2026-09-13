// socket_sender.h
//
// Declares a small class that opens a TCP server socket, waits for one
// client (the Java dashboard) to connect, and then lets us send text lines
// to that client whenever we want.
//
// Think of this like a radio station: setupAndWait() is "turning the
// transmitter on and waiting for a listener to tune in", and sendLine() is
// "saying something into the microphone" once someone is listening.

#ifndef SOCKET_SENDER_H
#define SOCKET_SENDER_H

#include <string>

class SocketSender {
public:
    // port: which TCP port to listen on, e.g. 9090
    explicit SocketSender(int port);
    ~SocketSender();

    // Opens the socket, binds it to the port, and BLOCKS (pauses this
    // thread) until a client connects. Returns true on success.
    bool setupAndWait();

    // Sends one line of text to the connected client. We add a '\n' at the
    // end automatically because the Java side reads with readLine(), which
    // needs a newline to know where one message ends and the next begins.
    bool sendLine(const std::string& line);

    // Returns true if we still appear to be connected to a client.
    bool isConnected() const;

    void close();

private:
    int port_;
    int serverFd_;   // file descriptor for the listening socket
    int clientFd_;   // file descriptor for the connected client
    bool connected_;
};

#endif
