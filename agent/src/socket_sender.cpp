

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

    serverFd_ = socket(AF_INET, SOCK_STREAM, 0);
    if (serverFd_ < 0) {
        std::cerr << "Failed to create socket\n";
        return false;
    }


    int opt = 1;
    setsockopt(serverFd_, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));


    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(port_); // htons converts port number to network byte order

    // Step 2: claim the port.
    if (bind(serverFd_, (struct sockaddr*)&address, sizeof(address)) < 0) {
        std::cerr << "Failed to bind to port " << port_ << "\n";
        return false;
    }


    if (listen(serverFd_, 1) < 0) {
        std::cerr << "Failed to listen on port " << port_ << "\n";
        return false;
    }

    std::cout << "[agent] Listening on port " << port_
              << ". Waiting for the Java dashboard to connect...\n";

 
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
