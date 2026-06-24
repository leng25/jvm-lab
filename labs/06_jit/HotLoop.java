/**
 * A tight loop that gets JIT-compiled quickly.
 * Run with: java -XX:+PrintCompilation -cp . HotLoop
 */
public class HotLoop {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("PID: " + ProcessHandle.current().pid());
        System.out.println("Running hot loop — watch PrintCompilation output...");

        long sum = 0;
        // 500M iterations — enough to trigger C1 then C2 compilation
        for (long i = 0; i < 500_000_000L; i++) {
            sum += compute(i);
        }
        System.out.println("Result: " + sum);

        System.out.println("Sleeping 30s for inspection...");
        Thread.sleep(30_000);
    }

    // Separate method so JIT compiles it as a unit
    private static long compute(long x) {
        return x * x + x - 1;
    }
}
