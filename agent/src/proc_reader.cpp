

#include "proc_reader.h"
#include <fstream>   // ifstream: lets us open and read text files
#include <sstream>   // istringstream: lets us split a line of text into words
#include <unistd.h>  // sysconf: asks the OS for system configuration values


long getClockTicksPerSecond() {
    static long ticksPerSecond = sysconf(_SC_CLK_TCK);
    return ticksPerSecond;
}


static void readStatusFile(int pid, ProcessSnapshot& snapshot) {
    std::string path = "/proc/" + std::to_string(pid) + "/status";
    std::ifstream file(path);

    if (!file.is_open()) {
        // If we can't open this file, the process has probably exited.
        snapshot.valid = false;
        return;
    }

    std::string line;
    while (std::getline(file, line)) {
        std::istringstream iss(line);
        std::string label;
        iss >> label; // reads the first "word" on the line, e.g. "VmRSS:"

        if (label == "VmRSS:") {
            iss >> snapshot.vmRssKb; // next token on the line is the number
        } else if (label == "VmPeak:") {
            iss >> snapshot.vmPeakKb;
        } else if (label == "Threads:") {
            iss >> snapshot.threadCount;
        }
    }
}


static void readStatFile(int pid, ProcessSnapshot& snapshot) {
    std::string path = "/proc/" + std::to_string(pid) + "/stat";
    std::ifstream file(path);

    if (!file.is_open()) {
        snapshot.valid = false;
        return;
    }

    std::string content;
    std::getline(file, content);

    // Find where the process name (in parentheses) ends, then start
    // reading fields from right after it. This sidesteps the "name has
    // spaces in it" problem entirely.
    size_t closeParen = content.find_last_of(')');
    if (closeParen == std::string::npos) {
        snapshot.valid = false;
        return;
    }

    std::istringstream iss(content.substr(closeParen + 2)); // +2 skips ") "
    std::string field;
    long utime = 0, stime = 0;

    // After the name, field 3 (state) is the first token. utime is the
    // 14th field overall, which is the 12th field after the name, and
    // stime is the 13th field after the name. you just count the tokens.
    int fieldIndex = 3; // we resume counting from field 3 (the state field)
    while (iss >> field) {
        if (fieldIndex == 14) {
            utime = std::stol(field);
        } else if (fieldIndex == 15) {
            stime = std::stol(field);
            break; // we have both numbers we need, no reason to keep reading
        }
        fieldIndex++;
    }

    snapshot.utimeTicks = utime;
    snapshot.stimeTicks = stime;
}

ProcessSnapshot readProcessSnapshot(int pid) {
    ProcessSnapshot snapshot{};
    snapshot.valid = true;

    readStatusFile(pid, snapshot);
    if (!snapshot.valid) return snapshot;

    readStatFile(pid, snapshot);
    return snapshot;
}

double calculateCpuPercent(const ProcessSnapshot& previous,
                            const ProcessSnapshot& current,
                            double elapsedSeconds) {
    if (elapsedSeconds <= 0.0) return 0.0;

    // How many CPU ticks were used between the two snapshots.
    long tickDelta = (current.utimeTicks + current.stimeTicks) -
                      (previous.utimeTicks + previous.stimeTicks);

    // Convert ticks to seconds of CPU time actually used.
    double cpuSecondsUsed = static_cast<double>(tickDelta) / getClockTicksPerSecond();

    // CPU percent = (CPU time used / wall clock time passed) * 100.
    // If elapsedSeconds is 0.5s and cpuSecondsUsed is 0.25s, that process
    // used the CPU for half of that window, so 50%.
    return (cpuSecondsUsed / elapsedSeconds) * 100.0;
}
