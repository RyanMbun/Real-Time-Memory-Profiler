/**
 * DemoTarget.java
 *
 * This is not part of the profiler itself. It is a small, deliberately
 * "badly behaved" program that allocates memory in bursts so you have
 * something visually interesting to point the profiler at. Run this in
 * one terminal, find its PID, then point the agent and dashboard at it.
 *
 * How to run:
 *   javac DemoTarget.java
 *   java DemoTarget
 *   (in another terminal) ps aux | grep DemoTarget    <- find the PID
 */
import java.util.ArrayList;
import java.util.List;

public class DemoTarget {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("DemoTarget running. PID reported by the JVM: "
                + ProcessHandle.current().pid());

        List<byte[]> memoryHog = new ArrayList<>();
        int cycle = 0;

        while (true) {
            cycle++;

            // Every cycle, allocate a chunk of memory (1 MB) and hang onto
            // it, so RSS climbs steadily, which is exactly the kind of
            // pattern the profiler is meant to reveal.
            memoryHog.add(new byte[1024 * 1024]);

            // Every 10 cycles, release everything, so you also get to see
            // memory drop back down in the chart, not just climb forever.
            if (cycle % 10 == 0) {
                System.out.println("Releasing " + memoryHog.size() + " MB of held memory");
                memoryHog.clear();
                System.gc(); // ask the JVM to actually reclaim it now, for a visible drop
            }

            // Burn some CPU too, so the CPU% column isn't always near zero.
            long busyWorkUntil = System.currentTimeMillis() + 200;
            double x = 0;
            while (System.currentTimeMillis() < busyWorkUntil) {
                x += Math.sqrt(x + 1);
            }

            Thread.sleep(300);
        }
    }
}
