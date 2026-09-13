// socket_sender.cpp
//
// This is raw POSIX socket code, meaning we are talking directly to the
// operating system's networking layer with no library in between. Every
// TCP server follows the same four steps, in this order:
//
//   1. socket()  -> ask the OS for a new, unconnected socket (like buying a phone)
//   2. bind()    -> claim a specific port number on this machine (like getting a phone number)
//   3. listen()  -> tell the OS "I'm ready, let calls queue up if I'm busy"
//   4. accept()  -> BLOCK (wait) until someone actually calls, then answer
//
// Once accept() returns, we have a brand new file descriptor that represents
// our private line to that one client. We use that to send data.

#include "socket_sender.h"
#include <iostream>
#include <cstring>       // memset
#include <unistd.h>      // close(), write()
#include <sys/socket.h>  // socket(), bind(), listen(), accept()
#include <netinet/in.h>  // sockaddr_in, INADDR_ANY
#include <arpa/inet.h>   // htons

SocketSender::SocketSender(int port)
    : port_(port), serverFd_(-1), clientFd_(-1), connected_(false) {}

SocketSender::~SocketSender() {
    close();
}

bool SocketSender::setupAndWait() {
    // Step 1: ask the OS for a socket.
    // AF_INET      = we want IPv4
    // SOCK_STREAM  = we want TCP (a reliable, ordered stream of bytes),
    //                as opposed to SOCK_DGRAM which would be UDP
    serverFd_ = socket(AF_INET, SOCK_STREAM, 0);
    if (serverFd_ < 0) {
        std::cerr << "Failed to create socket\n";
        return false;
    }

    // This lets us restart the agent quickly after stopping it, without
    // the OS complaining that "port 9090 is still in use" from the last run.
    int opt = 1;
    setsockopt(serverFd_, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    // Describe the address we want to bind to: any network interface on
    // this machine (INADDR_ANY), on our chosen port.
    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(port_); // htons converts port number to network byte order

    // Step 2: claim the port.
    if (bind(serverFd_, (struct sockaddr*)&address, sizeof(address)) < 0) {
        std::cerr << "Failed to bind to port " << port_ << "\n";
        return false;
    }

    // Step 3: start listening. The "1" means only 1 connection is allowed
    // to wait in the queue at a time, since we only expect one dashboard.
    if (listen(serverFd_, 1) < 0) {
        std::cerr << "Failed to listen on port " << port_ << "\n";
        return false;
    }

    std::cout << "[agent] Listening on port " << port_
              << ". Waiting for the Java dashboard to connect...\n";

    // Step 4: accept() BLOCKS here. The program does nothing else until a
    // client (our Java dashboard) connects. This is normal and expected.
    socklen_t addrLen = sizeof(address);
    clientFd_ = accept(serverFd_, (struct sockaddr*)&address, &addrLen);
    if (clientFd_ < 0) {
        std::cerr << "Failed to accept a connection\n";
        return false;
    }

    connected_ = true;
    std::cout << "[agent] Dashboard connected. Streaming data...\n";
    return true;
}

bool SocketSender::sendLine(const std::string& line) {
    if (!connected_) return false;

    std::string withNewline = line + "\n";
    // write() sends raw bytes over the socket. It returns the number of
    // bytes actually sent, or a negative number if something went wrong
    // (most commonly: the other side disconnected).
    ssize_t bytesSent = write(clientFd_, withNewline.c_str(), withNewline.size());

    if (bytesSent < 0) {
        std::cerr << "[agent] Dashboard disconnected.\n";
        connected_ = false;
        return false;
    }
    return true;
}

bool SocketSender::isConnected() const {
    return connected_;
}

void SocketSender::close() {
    if (clientFd_ >= 0) {
        ::close(clientFd_); // the :: means "call the global close(), not our own method"
        clientFd_ = -1;
    }
    if (serverFd_ >= 0) {
        ::close(serverFd_);
        serverFd_ = -1;
    }
    connected_ = false;
}
