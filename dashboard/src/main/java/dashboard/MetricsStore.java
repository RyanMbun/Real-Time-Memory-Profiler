package dashboard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;


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

 
    public synchronized void addReading(Metric metric) {
        window.addLast(metric);
        if (window.size() > WINDOW_SIZE) {
            window.removeFirst(); // old readings fall off the back, like a conveyor belt
        }

        
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
