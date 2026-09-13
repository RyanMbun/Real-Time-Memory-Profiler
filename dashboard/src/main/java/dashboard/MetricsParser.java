package dashboard;

/**
 * MetricsParser.java
 *
 * Turns one raw line of text from the C++ agent into a Metric object.
 * The line looks like:
 *   RSS:43200,PEAK:56000,THREADS:8,CPU:3.2
 *
 * We are NOT using a JSON library here on purpose, to keep dependencies
 * minimal, since this is a simple flat format with no nesting. We just
 * split on commas, then split each piece on the colon.
 */
public class MetricsParser {

    /**
     * Parses one line into a Metric, or returns null if the line is
     * malformed (missing a field, wrong format, etc). Returning null
     * instead of throwing lets the caller decide to just skip a bad line
     * rather than crashing the whole dashboard over one glitchy reading.
     */
    public static Metric parse(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }

        // Special control message the agent sends when the target process exits.
        if (line.startsWith("EVENT:")) {
            return null;
        }

        try {
            String[] parts = line.split(",");
            long rssKb = 0, peakKb = 0;
            int threads = 0;
            double cpu = 0.0;

            for (String part : parts) {
                String[] keyValue = part.split(":", 2);
                if (keyValue.length != 2) {
                    // This part didn't have a colon in it at all, so the
                    // line is malformed. We bail out and return null.
                    return null;
                }
                String key = keyValue[0].trim();
                String value = keyValue[1].trim();

                switch (key) {
                    case "RSS":
                        rssKb = Long.parseLong(value);
                        break;
                    case "PEAK":
                        peakKb = Long.parseLong(value);
                        break;
                    case "THREADS":
                        threads = Integer.parseInt(value);
                        break;
                    case "CPU":
                        cpu = Double.parseDouble(value);
                        break;
                    default:
                        // Unknown field, ignore it rather than failing, so
                        // we stay forward-compatible if the agent adds fields later.
                        break;
                }
            }

            return new Metric(System.currentTimeMillis(), rssKb / 1024.0, peakKb / 1024.0, threads, cpu);

        } catch (NumberFormatException e) {
            // One of the values wasn't actually a valid number. Treat this
            // reading as garbage and skip it instead of crashing.
            return null;
        }
    }
}
