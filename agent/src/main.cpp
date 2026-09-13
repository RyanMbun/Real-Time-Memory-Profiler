// main.cpp
//
// This is the entry point for the C++ agent. It:
//   1. Reads command line arguments (--pid, --port, --interval, --print-only)
//   2. Opens a socket and waits for the Java dashboard to connect
//      (unless --print-only is used, which just prints to the terminal
//      instead, useful for testing Phase 1 before Phase 2 exists)
//   3. Loops forever: read /proc, calculate CPU%, send one line of data,
//      sleep, repeat
//
// Usage:
//   ./profiler --pid 1234 --port 9090 --interval 500
//   ./profiler --pid 1234 --print-only     (no socket, just prints)

#include "proc_reader.h"
#include "socket_sender.h"
#include <iostream>
#include <string>
#include <cstdlib>   // atoi, exit
#include <unistd.h>  // usleep

// Turns command line flags like "--pid 1234" into actual variables.
// This is a very simple manual parser, nothing fancy, but it is easy to
// read and that matters more than being clever here.
struct Args {
    int pid = -1;
    int port = 9090;
    int intervalMs = 500;
    bool printOnly = false;
};

Args parseArgs(int argc, char* argv[]) {
    Args args;
    for (int i = 1; i < argc; i++) {
        std::string flag = argv[i];
        if (flag == "--pid" && i + 1 < argc) {
            args.pid = std::atoi(argv[++i]);
        } else if (flag == "--port" && i + 1 < argc) {
            args.port = std::atoi(argv[++i]);
        } else if (flag == "--interval" && i + 1 < argc) {
            args.intervalMs = std::atoi(argv[++i]);
        } else if (flag == "--print-only") {
            args.printOnly = true;
        }
    }
    return args;
}

// Formats one snapshot + CPU% as a single CSV-style line, matching exactly
// what the Java MetricsParser expects on the other end:
//   RSS:43200,PEAK:56000,THREADS:8,CPU:3.2
//
// std::to_string(double) always prints 6 decimal places (e.g. "3.200000"),
// which works but is noisy, so we format CPU with snprintf to keep it to
// one decimal place instead.
std::string formatAsLine(const ProcessSnapshot& snapshot, double cpuPercent) {
    char cpuBuffer[32];
    snprintf(cpuBuffer, sizeof(cpuBuffer), "%.1f", cpuPercent);

    return "RSS:" + std::to_string(snapshot.vmRssKb) +
           ",PEAK:" + std::to_string(snapshot.vmPeakKb) +
           ",THREADS:" + std::to_string(snapshot.threadCount) +
           ",CPU:" + std::string(cpuBuffer);
}

int main(int argc, char* argv[]) {
    // By default C's stdout is "fully buffered" when it's not a real
    // terminal (like when it's piped or captured). That means our printf
    // lines can sit in a buffer instead of showing up right away. Line
    // buffering flushes after every newline, so output appears immediately.
    setvbuf(stdout, nullptr, _IOLBF, 0);

    Args args = parseArgs(argc, argv);

    if (args.pid == -1) {
        std::cerr << "Usage: " << argv[0]
                  << " --pid <PID> [--port 9090] [--interval 500] [--print-only]\n";
        return 1;
    }

    SocketSender sender(args.port);
    if (!args.printOnly) {
        // This call BLOCKS until the Java dashboard connects. That is
        // expected and correct behavior, not a bug.
        if (!sender.setupAndWait()) {
            std::cerr << "Failed to set up socket. Exiting.\n";
            return 1;
        }
    }

    // Take the first reading before the loop starts so we have a "previous"
    // snapshot to compare the very first CPU calculation against.
    ProcessSnapshot previous = readProcessSnapshot(args.pid);
    if (!previous.valid) {
        std::cerr << "PID " << args.pid << " does not exist or already exited.\n";
        return 1;
    }

    double intervalSeconds = args.intervalMs / 1000.0;

    // The main sampling loop. This runs until the target process exits or
    // you press Ctrl+C.
    while (true) {
        // usleep takes MICROSECONDS, so we multiply milliseconds by 1000.
        usleep(args.intervalMs * 1000);

        ProcessSnapshot current = readProcessSnapshot(args.pid);
        if (!current.valid) {
            std::cout << "[agent] Target process " << args.pid << " has exited.\n";
            if (!args.printOnly) {
                sender.sendLine("EVENT:exit");
            }
            break;
        }

        double cpuPercent = calculateCpuPercent(previous, current, intervalSeconds);
        std::string line = formatAsLine(current, cpuPercent);

        if (args.printOnly) {
            // Phase 1 behavior: just print a human readable line to the terminal.
            double rssMb = current.vmRssKb / 1024.0;
            double peakMb = current.vmPeakKb / 1024.0;
            printf("[memory-profiler] PID %d | RSS: %.1f MB | Peak: %.1f MB | Threads: %d | CPU: %.1f%%\n",
                   args.pid, rssMb, peakMb, current.threadCount, cpuPercent);
        } else {
            // Phase 2+ behavior: send the line over the socket instead.
            if (!sender.sendLine(line)) {
                std::cout << "[agent] Lost connection to dashboard. Exiting.\n";
                break;
            }
        }

        previous = current; // this reading becomes "previous" for the next loop
    }

    return 0;
}
