package dashboard;

import java.io.File;


public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java dashboard.Main <pid> [host] [port]");
            System.exit(1);
        }

        int pid = Integer.parseInt(args[0]);
        String host = args.length >= 2 ? args[1] : "localhost";
        int port = args.length >= 3 ? Integer.parseInt(args[2]) : 9090;


        new File("exports").mkdirs();
        new File("reports").mkdirs();

        MetricsStore store = new MetricsStore();

       port, store), "socket-receiver");
        socketThread.setDaemon(true); // let the JVM exit even if this thread is mid-read
        socketThread.start();

        Dashboard dashboard = new Dashboard(store, pid);
        dashboard.start(); // this call blocks until the user quits
    }
}
