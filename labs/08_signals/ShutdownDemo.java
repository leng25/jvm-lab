/**
 * Registers shutdown hooks so you can observe them run on SIGTERM.
 * Run, then send: kill <pid>
 */
public class ShutdownDemo {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("PID: " + ProcessHandle.current().pid());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook 1 running (thread: " +
                    Thread.currentThread().getName() + ")");
            try { Thread.sleep(500); } catch (InterruptedException e) {}
            System.out.println("Shutdown hook 1 done");
        }, "shutdown-hook-1"));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook 2 running");
        }, "shutdown-hook-2"));

        System.out.println("Running. Send SIGTERM with: kill " +
                ProcessHandle.current().pid());
        System.out.println("Or SIGKILL with: kill -9 " +
                ProcessHandle.current().pid());

        Thread.sleep(Long.MAX_VALUE);
    }
}
