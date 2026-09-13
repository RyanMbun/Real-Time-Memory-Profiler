
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

    
            memoryHog.add(new byte[1024 * 1024]);

     
            if (cycle % 10 == 0) {
                System.out.println("Releasing " + memoryHog.size() + " MB of held memory");
                memoryHog.clear();
                System.gc(); // ask the JVM to actually reclaim it now, for a visible drop
            }

            
            long busyWorkUntil = System.currentTimeMillis() + 200;
            double x = 0;
            while (System.currentTimeMillis() < busyWorkUntil) {
                x += Math.sqrt(x + 1);
            }

            Thread.sleep(300);
        }
    }
}
