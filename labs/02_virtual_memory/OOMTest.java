import java.util.ArrayList;
import java.util.List;

/**
 * Intentionally exhausts the heap to trigger OutOfMemoryError.
 * Run with: java -Xmx32m OOMTest
 */
public class OOMTest {
    public static void main(String[] args) {
        System.out.println("PID: " + ProcessHandle.current().pid());
        System.out.println("Allocating until OOM...");
        List<byte[]> sink = new ArrayList<>();
        int i = 0;
        while (true) {
            sink.add(new byte[1024 * 1024]); // 1MB at a time
            System.out.println("Allocated " + (++i) + "MB");
        }
    }
}
