package dashboard;

import java.io.File;

/**
 * Main.java
 *
 * Entry point for the Java dashboard. This is what actually starts
 * everything:
 *   1. Create the shared MetricsStore (the "mailbox" between our two threads)
 *   2. Start SocketReceiver on a background thread (it will connect to the
 *      C++ agent and start filling the store with readings)
 *   3. Start Dashboard on THIS thread (it reads from the store and draws
 *      the screen in a loop until the user quits)
 *
 * Usage:
 *   java -cp target/classes dashboard.Main <pid> [host] [port]
 *   java -cp target/classes dashboard.Main 1234 localhost 9090
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java dashboard.Main <pid> [host] [port]");
            System.exit(1);
        }

        int pid = Integer.parseInt(args[0]);
        String host = args.length >= 2 ? args[1] : "localhost";
        int port = args.length >= 3 ? Integer.parseInt(args[2]) : 9090;

        // Make sure the exports/ and reports/ folders exist before we need
        // to write into them, otherwise CsvExporter and AiReportGenerator
        // would fail on a fresh checkout of the project.
        new File("exports").mkdirs();
        new File("reports").mkdirs();

        MetricsStore store = new MetricsStore();

        // This thread runs SocketReceiver.run() in the background. It is
        // separate from the main thread so that waiting on the socket
        // (which can pause the thread for hundreds of milliseconds at a
        // time) never freezes the dashboard's screen updates.
        Thread socketThread = new Thread(new SocketReceiver(host, port, store), "socket-receiver");
        socketThread.setDaemon(true); // let the JVM exit even if this thread is mid-read
        socketThread.start();

        Dashboard dashboard = new Dashboard(store, pid);
        dashboard.start(); // this call blocks until the user quits
    }
}
