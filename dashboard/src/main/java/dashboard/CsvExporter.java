package dashboard;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;


public class CsvExporter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

  
    public static String export(List<Metric> readings) {
        if (readings == null || readings.isEmpty()) {
            return null;
        }

        String timestamp = Instant.now().atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMAT);
        String path = "exports/profiler_export_" + timestamp + ".csv";

  
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
