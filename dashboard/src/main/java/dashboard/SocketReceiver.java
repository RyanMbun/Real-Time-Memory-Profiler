package dashboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * SocketReceiver.java
 *
 * Connects to the C++ agent over TCP and reads one line at a time in an
 * infinite loop. This class implements Runnable so it can be handed to a
 * Thread and run in the background, separate from the UI thread. That
 * separation is the whole point: reading from a socket can block (pause)
 * while waiting for the next line, and we do not want that pause to freeze
 * the dashboard's UI.
 *
 * Every time a full line arrives, we parse it and push the result into the
 * shared MetricsStore, which the UI thread reads from independently.
 */
public class SocketReceiver implements Runnable {

    private final String host;
    private final int port;
    private final MetricsStore store;

    // How many times we retry connecting before giving up, and how long we
    // wait between attempts. This handles the case where the dashboard is
    // started slightly before the C++ agent is ready to accept connections.
    private static final int MAX_RETRIES = 10;
    private static final int RETRY_DELAY_MS = 1000;

    public SocketReceiver(String host, int port, MetricsStore store) {
        this.host = host;
        this.port = port;
        this.store = store;
    }

    @Override
    public void run() {
        Socket socket = connectWithRetry();
        if (socket == null) {
            store.markDisconnected("Could not connect to agent after " + MAX_RETRIES + " attempts");
            return;
        }

        // try-with-resources guarantees the socket and reader get closed
        // automatically even if an exception happens partway through,
        // instead of us having to remember to close them in every exit path.
        try (Socket s = socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {

            store.markConnected();
            String line;

            // readLine() blocks (pauses this thread) until a full line
            // (ending in '\n') arrives, or returns null if the connection
            // was closed cleanly by the other side.
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("EVENT:exit")) {
                    store.markAgentReportedExit();
                    break;
                }
                Metric metric = MetricsParser.parse(line);
                if (metric != null) {
                    store.addReading(metric);
                }
                // If metric is null, we silently skip the malformed line
                // rather than crashing the whole dashboard over one bad reading.
            }

            // readLine() returned null: the agent closed the connection cleanly.
            store.markDisconnected("Agent closed the connection");

        } catch (IOException e) {
            // The connection dropped abruptly (e.g. the agent process was
            // killed rather than exiting cleanly). This is a different
            // path than the null check above, so we handle both cases.
            store.markDisconnected("Connection lost: " + e.getMessage());
        }
    }

    /**
     * Tries to connect up to MAX_RETRIES times, waiting RETRY_DELAY_MS
     * between attempts. Returns the connected socket, or null if every
     * attempt failed. This is what lets you start the dashboard first and
     * then start the agent a moment later without the dashboard just crashing.
     */
    private Socket connectWithRetry() {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return new Socket(host, port);
            } catch (IOException e) {
                System.out.println("[dashboard] Waiting for agent on " + host + ":" + port +
                        " (attempt " + attempt + "/" + MAX_RETRIES + ")...");
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }
}
