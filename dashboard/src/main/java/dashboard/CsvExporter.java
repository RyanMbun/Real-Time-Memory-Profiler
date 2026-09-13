package dashboard;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CsvExporter.java
 *
 * Writes out whatever readings are currently in the rolling window (up to
 * the last 60, which at 500ms intervals is about 30 seconds, or the last
 * 5 minutes if you increase WINDOW_SIZE in MetricsStore) to a CSV file so
 * you can open it in Excel, or feed it to AiReportGenerator later.
 *
 * Columns: timestamp, rss_mb, peak_mb, threads, cpu_pct, rss_delta_mb
 */
public class CsvExporter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /**
     * Writes the given readings to exports/profiler_export_<timestamp>.csv
     * and returns the file path, or null if there was nothing to export or
     * writing failed.
     */
    public static String export(List<Metric> readings) {
        if (readings == null || readings.isEmpty()) {
            return null;
        }

        String timestamp = Instant.now().atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMAT);
        String path = "exports/profiler_export_" + timestamp + ".csv";

        // try-with-resources closes the file automatically, even if
        // writing throws an exception partway through.
        try (FileWriter writer = new FileWriter(path)) {
            writer.write("timestamp,rss_mb,peak_mb,threads,cpu_pct,rss_delta_mb\n");

            double previousRss = readings.get(0).rssMb;
            for (Metric m : readings) {
                double delta = m.rssMb - previousRss;
                writer.write(String.format("%d,%.2f,%.2f,%d,%.2f,%.2f%n",
                        m.timestampMs, m.rssMb, m.peakMb, m.threads, m.cpuPercent, delta));
                previousRss = m.rssMb;
            }
            return path;

        } catch (IOException e) {
            System.err.println("[export] Failed to write CSV: " + e.getMessage());
            return null;
        }
    }
}
