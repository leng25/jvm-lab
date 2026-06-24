/**
 * Deliberately triggers a NullPointerException so you can observe SIGSEGV in strace.
 */
public class NullTest {
    public static void main(String[] args) {
        System.out.println("PID: " + ProcessHandle.current().pid());
        System.out.println("About to dereference null...");
        try {
            String s = null;
            int len = s.length(); // SIGSEGV → NPE
            System.out.println(len);
        } catch (NullPointerException e) {
            System.out.println("Caught NPE (was a SIGSEGV underneath)");
        }
    }
}
