public class Sleep {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("PID: " + ProcessHandle.current().pid());
        System.out.println("Sleeping for 60 seconds. Inspect /proc/<pid>/ now.");
        Thread.sleep(60_000);
    }
}
