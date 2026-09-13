package dashboard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * MetricsStore.java
 *
 * This is the one piece of shared state between our two threads:
 *   - the SocketReceiver thread WRITES new readings into this store
 *   - the Dashboard (UI) thread READS from this store to draw the screen
 *
 * Two things make this safe to share between threads:
 *
 * 1. `volatile` on the fields that get reassigned (like `latest` and
 *    `connectionStatus`). Volatile tells the JVM "never let a thread keep
 *    a cached copy of this value in a CPU register, always go back to
 *    main memory". Without it, the UI thread could keep reading a stale
 *    value forever even after the socket thread updates it, because the
 *    CPU decided to optimize by caching it locally.
 *
 * 2. `synchronized` on the methods that touch the rolling window list.
 *    A Deque (double-ended queue) is not thread-safe by default, so two
 *    threads touching it at the exact same instant could corrupt it.
 *    `synchronized` makes sure only one thread is inside these methods
 *    at any given moment.
 */
public class MetricsStore {

    private static final int WINDOW_SIZE = 60; // last 60 readings, ~30 seconds at 500ms

    private final Deque<Metric> window = new ArrayDeque<>();

    // volatile: always read the true, current value from main memory.
    private volatile Metric latest = null;
    private volatile Metric previousForDiff = null;
    private volatile double heapDiffMb = 0.0;

    public enum Status { CONNECTING, CONNECTED, DISCONNECTED, AGENT_EXITED }
    private volatile Status connectionStatus = Status.CONNECTING;
    private volatile String statusMessage = "Connecting to agent...";

    /**
     * Called by the socket thread every time a new reading comes in.
     * synchronized so the add-and-trim operation on the window happens as
     * one atomic step, never interrupted halfway by another thread.
     */
    public synchronized void addReading(Metric metric) {
        window.addLast(metric);
        if (window.size() > WINDOW_SIZE) {
            window.removeFirst(); // old readings fall off the back, like a conveyor belt
        }

        // Heap diff: how much RSS changed since the previous reading.
        if (previousForDiff != null) {
            heapDiffMb = metric.rssMb - previousForDiff.rssMb;
        }
        previousForDiff = metric;

        latest = metric;             // volatile write, visible to the UI thread immediately
        connectionStatus = Status.CONNECTED;
    }

    /** Returns a COPY of the current window so the caller can safely iterate it. */
    public synchronized List<Metric> getWindowSnapshot() {
        return new ArrayList<>(window);
    }

    public Metric getLatest() {
        return latest; // volatile read, always the true current value
    }

    public double getHeapDiffMb() {
        return heapDiffMb;
    }

    public double getPeakRssSeen() {
        double max = 0.0;
        for (Metric m : getWindowSnapshot()) {
            if (m.rssMb > max) max = m.rssMb;
        }
        return max;
    }

    public double getAverageRss() {
        List<Metric> snapshot = getWindowSnapshot();
        if (snapshot.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Metric m : snapshot) sum += m.rssMb;
        return sum / snapshot.size();
    }

    public void markConnected() {
        connectionStatus = Status.CONNECTED;
        statusMessage = "Connected";
    }

    public void markDisconnected(String reason) {
        connectionStatus = Status.DISCONNECTED;
        statusMessage = reason;
    }

    public void markAgentReportedExit() {
        connectionStatus = Status.AGENT_EXITED;
        statusMessage = "Target process exited";
    }

    public Status getStatus() {
        return connectionStatus;
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}
