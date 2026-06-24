/**
 * Triggers StackOverflowError so you can observe the SIGSEGV via guard page in strace.
 */
public class StackOverflowDemo {
    public static void main(String[] args) {
        System.out.println("PID: " + ProcessHandle.current().pid());
        System.out.println("Overflowing stack...");
        try {
            overflow();
        } catch (StackOverflowError e) {
            System.out.println("Caught StackOverflowError");
        }
    }

    static void overflow() {
        overflow();
    }
}
