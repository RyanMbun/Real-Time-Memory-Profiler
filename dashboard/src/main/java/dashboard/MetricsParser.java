package dashboard;


public class MetricsParser {

    
    public static Metric parse(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }

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
                      
                        break;
                }
            }

            return new Metric(System.currentTimeMillis(), rssKb / 1024.0, peakKb / 1024.0, threads, cpu);

        } catch (NumberFormatException e) {
            
            return null;
        }
    }
}
