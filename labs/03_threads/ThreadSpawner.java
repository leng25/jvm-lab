/**
 * Spawns several named threads and keeps them alive so you can inspect
 * them from /proc/<pid>/task/ and with ps/top.
 */
public class ThreadSpawner {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Main thread PID: " + ProcessHandle.current().pid());
        System.out.println("Main thread ID:  " + Thread.currentThread().threadId());

        int count = args.length > 0 ? Integer.parseInt(args[0]) : 5;

        for (int i = 1; i <= count; i++) {
            final int num = i;
            Thread t = new Thread(() -> {
                System.out.printf("Worker-%d started, Java threadId=%d%n",
                        num, Thread.currentThread().threadId());
                try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) {}
            }, "worker-" + i);
            t.setDaemon(true);
            t.start();
        }

        System.out.println("\nAll threads running. Inspect with:");
        System.out.println("  ps -eLf | grep ThreadSpawner");
        System.out.println("  ls /proc/<pid>/task/");
        System.out.println("  top -H -p <pid>");
        System.out.println("\nSleeping 60s...");
        Thread.sleep(60_000);
    }
}
