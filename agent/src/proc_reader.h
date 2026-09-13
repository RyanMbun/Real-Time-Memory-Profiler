
#ifndef PROC_READER_H
#define PROC_READER_H

#include <string>

// One snapshot of a process's stats at a single point in time.
struct ProcessSnapshot {
    long vmRssKb;      // Resident Set Size: how much RAM is actually in use right now (KB)
    long vmPeakKb;     // The highest VmRSS this process has ever reached (KB)
    int threadCount;   // How many threads this process currently has
    long utimeTicks;   // CPU ticks spent running in user mode (our own code)
    long stimeTicks;   // CPU ticks spent running in kernel mode (system calls made on our behalf)
    bool valid;        // false if the process no longer exists (it exited)
};


ProcessSnapshot readProcessSnapshot(int pid);


double calculateCpuPercent(const ProcessSnapshot& previous,
                            const ProcessSnapshot& current,
                            double elapsedSeconds);


// but we ask the system directly instead of hardcoding that number.
long getClockTicksPerSecond();

#endif
