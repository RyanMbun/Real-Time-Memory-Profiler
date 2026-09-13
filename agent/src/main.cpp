

#include "proc_reader.h"
#include "socket_sender.h"
#include <iostream>
#include <string>
#include <cstdlib>   // atoi, exit
#include <unistd.h>  // usleep


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
std::string formatAsLine(const ProcessSnapshot& snapshot, double cpuPercent) {
    char cpuBuffer[32];
    snprintf(cpuBuffer, sizeof(cpuBuffer), "%.1f", cpuPercent);

    return "RSS:" + std::to_string(snapshot.vmRssKb) +
           ",PEAK:" + std::to_string(snapshot.vmPeakKb) +
           ",THREADS:" + std::to_string(snapshot.threadCount) +
           ",CPU:" + std::string(cpuBuffer);
}

int main(int argc, char* argv[]) {

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
