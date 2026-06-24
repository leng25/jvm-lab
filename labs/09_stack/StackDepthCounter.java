/**
 * Recurses until StackOverflowError to measure maximum stack depth.
 * Try with different -Xss values.
 */
public class StackDepthCounter {
    static int depth = 0;

    public static void main(String[] args) {
        System.out.println("PID: " + ProcessHandle.current().pid());
        try {
            recurse();
        } catch (StackOverflowError e) {
            System.out.println("Stack overflow at depth: " + depth);
        }
    }

    static void recurse() {
        depth++;
        recurse();
    }
}
