package dashboard;


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
