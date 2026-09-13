#ifndef SOCKET_SENDER_H
#define SOCKET_SENDER_H

#include <string>

class SocketSender {
public:
    // port: which TCP port to listen on, e.g. 9090
    explicit SocketSender(int port);
    ~SocketSender();


    bool setupAndWait();


    bool sendLine(const std::string& line);

    bool isConnected() const;

    void close();

private:
    int port_;
    int serverFd_;   // file descriptor for the listening socket
    int clientFd_;   // file descriptor for the connected client
    bool connected_;
};

#endif
