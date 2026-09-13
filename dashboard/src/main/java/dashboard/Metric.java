package dashboard;

/**
 * Metric.java
 *
 * This is our data model: one single reading from the C++ agent, plus the
 * timestamp we received it at. It is IMMUTABLE, meaning once you build one
 * of these objects none of its fields can ever change.
 *
 * Why immutable matters here: our socket reader thread and our UI thread
 * both touch this data. If we let fields change after creation, the UI
 * thread could read an object that is "half updated" (say, RSS is new but
 * CPU is still old) if it happens to look at the exact wrong moment. By
 * making a brand new Metric every time and swapping the whole reference,
 * the UI thread only ever sees a complete, consistent object. See
 * Dashboard.java for how the reference swap itself is made thread-safe.
 */
public final class Metric {
    public final long timestampMs;
    public final double rssMb;
    public final double peakMb;
    public final int threads;
    public final double cpuPercent;

    public Metric(long timestampMs, double rssMb, double peakMb, int threads, double cpuPercent) {
        this.timestampMs = timestampMs;
        this.rssMb = rssMb;
        this.peakMb = peakMb;
        this.threads = threads;
        this.cpuPercent = cpuPercent;
    }

    @Override
    public String toString() {
        return String.format("RSS=%.1fMB PEAK=%.1fMB THREADS=%d CPU=%.1f%%",
                rssMb, peakMb, threads, cpuPercent);
    }
}
