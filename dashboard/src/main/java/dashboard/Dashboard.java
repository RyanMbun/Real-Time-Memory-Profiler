package dashboard;

import java.util.List;
import java.util.Scanner;

/**
 * Dashboard.java
 *
 * This is the UI thread. It wakes up every 500ms, reads the CURRENT state
 * out of MetricsStore (never touching the socket itself), and redraws the
 * whole terminal screen using ANSI escape codes.
 *
 * ANSI escape codes are special character sequences starting with ESC (\033)
 * that terminals understand as commands instead of text to print. For
 * example "\033[2J" means "clear the whole screen" and "\033[H" means
 * "move the cursor back to the top left corner". We use a handful of these
 * to draw colored boxes and bars without needing any external UI library,
 * which keeps this project dependency-free and easy to build with plain
 * javac + a Makefile.
 *
 * We deliberately do NOT read from the socket in this class. That is
 * SocketReceiver's job, running on its own thread. This class only reads
 * from MetricsStore, which is safe to do from any thread because of the
 * volatile fields and synchronized methods inside it. That separation is
 * what keeps the UI smooth even if the socket briefly has nothing new to say.
 */
public class Dashboard {

    // A handful of ANSI codes we use. RESET always goes back to normal text.
    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String RED = "\033[31m";
    private static final String CYAN = "\033[36m";
    private static final String CLEAR_SCREEN = "\033[2J\033[H";

    private static final int REFRESH_MS = 500;
    private static final int CHART_WIDTH = 50; // how many characters wide the bar chart bars can get

    private final MetricsStore store;
    private final int targetPid;
    private final long startTimeMs;
    private volatile boolean running = true;
    private volatile boolean paused = false;

    public Dashboard(MetricsStore store, int targetPid) {
        this.store = store;
        this.targetPid = targetPid;
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Starts the keyboard listener on its own thread and then runs the
     * render loop on the calling thread until the user quits.
     *
     * Note: plain Java's System.in is LINE buffered by the terminal itself
     * (not by Java), which means a key press is not delivered to our
     * program until Enter is pressed. That is a real limitation of using
     * zero external libraries. We accept it here as a scoped tradeoff:
     * type a letter then press Enter, instead of a single instant keypress.
     */
    public void start() {
        Thread keyboardThread = new Thread(this::listenForKeyboardInput, "keyboard-listener");
        keyboardThread.setDaemon(true); // daemon = JVM can exit even if this thread is still running
        keyboardThread.start();

        renderLoop();
    }

    private void renderLoop() {
        while (running) {
            if (!paused) {
                render();
            }
            try {
                Thread.sleep(REFRESH_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println(RESET + "\nDashboard stopped.");
    }

    private void listenForKeyboardInput() {
        Scanner scanner = new Scanner(System.in);
        while (running && scanner.hasNextLine()) {
            String input = scanner.nextLine().trim().toLowerCase();
            switch (input) {
                case "q":
                    running = false;
                    break;
                case "p":
                    paused = !paused;
                    break;
                case "e":
                    handleExportRequest();
                    break;
                case "r":
                    handleReportRequest();
                    break;
                default:
                    // ignore anything else the user typed
            }
        }
    }

    private void handleExportRequest() {
        List<Metric> snapshot = store.getWindowSnapshot();
        String path = CsvExporter.export(snapshot);
        if (path != null) {
            System.out.println("\n" + GREEN + "[export] Saved to " + path + RESET);
        } else {
            System.out.println("\n" + RED + "[export] Nothing to export yet." + RESET);
        }
    }

    private void handleReportRequest() {
        System.out.println("\n" + CYAN + "[report] Generating AI report, this can take a few seconds..." + RESET);
        new Thread(() -> {
            String result = AiReportGenerator.generateFromLatestExport();
            System.out.println(result);
        }, "ai-report-generator").start();
    }

    private void render() {
        StringBuilder sb = new StringBuilder();
        sb.append(CLEAR_SCREEN);

        renderHeader(sb);
        renderChart(sb);
        renderStats(sb);
        renderFooter(sb);

        System.out.print(sb);
        System.out.flush();
    }

    private void renderHeader(StringBuilder sb) {
        long uptimeSeconds = (System.currentTimeMillis() - startTimeMs) / 1000;
        sb.append(BOLD).append(CYAN)
          .append("=== Memory Profiler | PID ").append(targetPid)
          .append(" | Uptime ").append(uptimeSeconds).append("s ===")
          .append(RESET).append("\n\n");
    }

    private void renderChart(StringBuilder sb) {
        List<Metric> snapshot = store.getWindowSnapshot();
        if (snapshot.isEmpty()) {
            sb.append("Waiting for data...\n\n");
            return;
        }

        double maxRss = 0.0;
        for (Metric m : snapshot) {
            if (m.rssMb > maxRss) maxRss = m.rssMb;
        }
        if (maxRss <= 0) maxRss = 1.0; // avoid dividing by zero if everything is 0

        sb.append("RSS Memory (last ").append(snapshot.size()).append(" readings, scale 0-")
          .append(String.format("%.0f", maxRss)).append(" MB):\n");

        for (Metric m : snapshot) {
            int barLength = (int) ((m.rssMb / maxRss) * CHART_WIDTH);
            String bar = "#".repeat(Math.max(barLength, 1));
            sb.append(String.format("%6.1f MB ", m.rssMb))
              .append(GREEN).append(bar).append(RESET).append("\n");
        }
        sb.append("\n");
    }

    private void renderStats(StringBuilder sb) {
        Metric latest = store.getLatest();
        if (latest == null) {
            sb.append("No readings yet.\n\n");
            return;
        }

        double heapDiff = store.getHeapDiffMb();
        String diffColor = heapDiff >= 0 ? YELLOW : GREEN;
        String diffSign = heapDiff >= 0 ? "+" : "";

        sb.append(BOLD).append("Current Stats:").append(RESET).append("\n");
        sb.append(String.format("  RSS:     %.1f MB  (%s%s%.1f MB%s since last reading)\n",
                latest.rssMb, diffColor, diffSign, heapDiff, RESET));
        sb.append(String.format("  Peak:    %.1f MB\n", latest.peakMb));
        sb.append(String.format("  Average: %.1f MB\n", store.getAverageRss()));
        sb.append(String.format("  Threads: %d\n", latest.threads));
        sb.append(String.format("  CPU:     %.1f%%\n\n", latest.cpuPercent));
    }

    private void renderFooter(StringBuilder sb) {
        MetricsStore.Status status = store.getStatus();
        String statusColor;
        switch (status) {
            case CONNECTED: statusColor = GREEN; break;
            case CONNECTING: statusColor = YELLOW; break;
            default: statusColor = RED; break;
        }

        sb.append(statusColor).append("Status: ").append(store.getStatusMessage()).append(RESET).append("\n");
        sb.append(paused ? YELLOW + "[PAUSED] " + RESET : "");
        sb.append("Controls: [q]uit  [p]ause  [e]xport CSV  [r]AI report  (type a letter + Enter)\n");
    }
}
