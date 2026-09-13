package dashboard;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;


public class AiReportGenerator {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-6";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");


    public static String generateFromLatestExport() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return "[report] ANTHROPIC_API_KEY is not set. Run: export ANTHROPIC_API_KEY=your-key-here";
        }

        Optional<Path> latestCsv = findMostRecentExport();
        if (latestCsv.isEmpty()) {
            return "[report] No CSV export found. Press 'e' first to export your data.";
        }

        try {
            String csvContent = Files.readString(latestCsv.get());
            String reportText = callClaudeApi(apiKey, csvContent);
            String savedPath = saveReport(reportText);
            return "[report] Saved to " + savedPath + "\n\n" + reportText;

        } catch (IOException | InterruptedException e) {
            return "[report] Failed to generate report: " + e.getMessage();
        }
    }

    /** Looks in exports/ and returns the CSV file with the newest last-modified time. */
    private static Optional<Path> findMostRecentExport() {
        File exportDir = new File("exports");
        File[] files = exportDir.listFiles((dir, name) -> name.endsWith(".csv"));
        if (files == null || files.length == 0) {
            return Optional.empty();
        }
        File newest = null;
        for (File f : files) {
            if (newest == null || f.lastModified() > newest.lastModified()) {
                newest = f;
            }
        }
        return Optional.ofNullable(newest).map(File::toPath);
    }

    
    private static String callClaudeApi(String apiKey, String csvContent)
            throws IOException, InterruptedException {

        String instructions =
                "You are a performance analysis assistant. Analyze this memory and CPU " +
                "profiling data from a Linux process. Identify: (1) any memory spikes and " +
                "when they occurred, (2) periods of high CPU usage, (3) thread count " +
                "anomalies, (4) an overall health assessment. Write in plain English as if " +
                "explaining to a software engineering student. Be specific, reference actual " +
                "timestamps and numbers from the data.";

        String promptText = instructions + "\n\nCSV data:\n" + csvContent;

     
        String jsonBody = "{"
                + "\"model\":\"" + MODEL + "\","
                + "\"max_tokens\":1024,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escapeJson(promptText) + "\"}]"
                + "}";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(60))
                // x-api-key: how Anthropic identifies who is making the request
                .header("x-api-key", apiKey)
                // anthropic-version: tells the API which version of its rules to follow
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return "API request failed with status " + response.statusCode() + ": " + response.body();
        }

        return extractTextFromResponse(response.body());
    }

   
    private static String extractTextFromResponse(String responseBody) {
        String marker = "\"text\":\"";
        int start = responseBody.indexOf(marker);
        if (start == -1) {
            return "Could not find report text in API response:\n" + responseBody;
        }
        start += marker.length();

        StringBuilder result = new StringBuilder();
        for (int i = start; i < responseBody.length(); i++) {
            char c = responseBody.charAt(i);
            if (c == '\\' && i + 1 < responseBody.length()) {
                char next = responseBody.charAt(i + 1);
                if (next == 'n') { result.append('\n'); i++; continue; }
                if (next == '"') { result.append('"'); i++; continue; }
                if (next == '\\') { result.append('\\'); i++; continue; }
            }
            if (c == '"') {
                break; // unescaped quote means the string is over
            }
            result.append(c);
        }
        return result.toString();
    }

    /** Escapes characters that would otherwise break the JSON string we build by hand. */
    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "");
    }

    private static String saveReport(String reportText) throws IOException {
        String timestamp = Instant.now().atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMAT);
        String path = "reports/report_" + timestamp + ".txt";
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(reportText);
        }
        return path;
    }
}
