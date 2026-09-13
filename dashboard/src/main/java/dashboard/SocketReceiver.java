package dashboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SocketReceiver implements Runnable {

    private final String host;
    private final int port;
    private final MetricsStore store;


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

        
        try (Socket s = socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {

            store.markConnected();
            String line;

           
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("EVENT:exit")) {
                    store.markAgentReportedExit();
                    break;
                }
                Metric metric = MetricsParser.parse(line);
                if (metric != null) {
                    store.addReading(metric);
                }
                
            }

            // readLine() returned null: the agent closed the connection cleanly.
            store.markDisconnected("Agent closed the connection");

        } catch (IOException e) {
           
            store.markDisconnected("Connection lost: " + e.getMessage());
        }
    }

   
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
