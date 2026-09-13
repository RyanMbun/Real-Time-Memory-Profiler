// proc_reader.h
//
// This file declares the "shape" of our proc reader: what data it collects
// and what functions are available. On Linux, every running process gets a
// folder at /proc/<pid>/ that the kernel fills with live text files describing
// that process. We read two of those files:
//   /proc/<pid>/status  -> gives us memory numbers (VmRSS, VmPeak) and thread count
//   /proc/<pid>/stat    -> gives us CPU time numbers (utime, stime) as "ticks"
//
// A struct is just a bundle of related variables with one name, so we can
// pass "one snapshot of a process" around as a single object instead of
// four separate variables.

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

// Reads /proc/<pid>/status and /proc/<pid>/stat and fills in a ProcessSnapshot.
// Returns a snapshot with valid = false if the process does not exist
// (this happens when the target program has exited).
ProcessSnapshot readProcessSnapshot(int pid);

// Takes two snapshots taken some milliseconds apart and calculates a CPU
// percentage. This works the same way you'd estimate speed from two
// odometer readings and the time between them: (distance / time) = speed.
// Here it's (CPU ticks used / wall-clock time passed) = CPU percent.
double calculateCpuPercent(const ProcessSnapshot& previous,
                            const ProcessSnapshot& current,
                            double elapsedSeconds);

// Returns the number of clock ticks per second that this Linux system uses.
// We need this to convert "ticks" into real seconds. It is usually 100,
// but we ask the system directly instead of hardcoding that number.
long getClockTicksPerSecond();

#endif
