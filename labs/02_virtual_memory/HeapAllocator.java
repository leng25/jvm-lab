import java.util.ArrayList;
import java.util.List;

/**
 * Gradually allocates heap memory so you can observe virtual vs resident memory
 * growing in real time via /proc/<pid>/status
 */
public class HeapAllocator {
    public static void main(String[] args) throws InterruptedException {
        List<byte[]> chunks = new ArrayList<>();
        System.out.println("PID: " + ProcessHandle.current().pid());
        System.out.println("Allocating 10 x 50MB chunks, 2 seconds apart...");

        for (int i = 1; i <= 10; i++) {
            byte[] chunk = new byte[50 * 1024 * 1024]; // 50MB
            // Touch every page so the OS actually maps physical memory
            for (int j = 0; j < chunk.length; j += 4096) {
                chunk[j] = (byte) i;
            }
            chunks.add(chunk);
            System.out.printf("Allocated chunk %d — total ~%dMB touched%n", i, i * 50);
            Thread.sleep(2000);
        }

        System.out.println("Done. Sleeping 30s so you can inspect /proc/<pid>/");
        Thread.sleep(30_000);
    }
}
