import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Creates steady GC pressure by allocating short-lived and long-lived objects.
 * Run with: java -Xmx256m -Xlog:gc* -cp . GCPressure
 */
public class GCPressure {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("PID: " + ProcessHandle.current().pid());

        // Long-lived objects that survive into old gen
        Deque<byte[]> tenured = new ArrayDeque<>();

        for (int round = 0; round < 1000; round++) {
            // Allocate lots of short-lived garbage (will be collected by young GC)
            for (int i = 0; i < 1000; i++) {
                byte[] trash = new byte[1024]; // 1KB — dies young
            }

            // Occasionally promote something to old gen
            if (round % 20 == 0) {
                tenured.addLast(new byte[512 * 1024]); // 512KB survives
                if (tenured.size() > 100) {
                    tenured.pollFirst(); // let old objects die too
                }
                System.out.println("Round " + round + " — tenured size: " + tenured.size());
            }

            Thread.sleep(10);
        }

        System.out.println("Done. Sleeping 30s...");
        Thread.sleep(30_000);
    }
}
