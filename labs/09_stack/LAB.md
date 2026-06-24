# Lab 09 — Stack & Stack Overflow

**Goal:** Find thread stacks in the memory map, understand guard pages,
and see how StackOverflowError is detected without the JVM explicitly checking depth.

---

## Part 1: Locate thread stacks in /proc/maps

```bash
# Terminal 1
java -cp . ThreadSpawner 5   # reuse from lab 03

# Terminal 2
PID=$(pgrep -f ThreadSpawner)

# The main thread stack is labeled [stack]
cat /proc/$PID/maps | grep "\[stack\]"

# Worker thread stacks are anonymous — same size as -Xss
# Look for anonymous private rw regions near stack size (default ~1MB = 0x100000)
cat /proc/$PID/maps | awk '$2=="rw-p" && $6=="" {
    split($1, range, "-")
    size = strtonum("0x" range[2]) - strtonum("0x" range[1])
    if (size >= 4096 && size <= 2*1024*1024) print size/1024 "KB\t" $0
}'
```

**Questions:**
- How large is the main thread's stack?
- How large are worker thread stacks?

---

## Part 2: Guard pages

Each thread stack has a guard page at the bottom — a page with `PROT_NONE` (no access).
When you overflow the stack, you touch this page → SIGSEGV → JVM catches it → StackOverflowError.

```bash
PID=$(pgrep -f ThreadSpawner)

# Look for PROT_NONE pages adjacent to stack regions
cat /proc/$PID/maps | grep "---p"
# These are the guard pages — no read, write, or execute permissions
```

```bash
# More detail from smaps
cat /proc/$PID/smaps | awk '/---p/{found=1} found{print; if(/^$/)found=0}' | head -40
```

**Questions:**
- How large is the guard page? (typically 4KB = 1 page, or sometimes 64KB)
- What happens if the JVM itself overflows the stack during SIGSEGV handling?
  (hint: there's a separate signal stack — `sigaltstack()`)

---

## Part 3: Trigger StackOverflowError

```bash
strace -e signal java -cp . StackOverflowDemo 2>&1 | grep -E "SIGSEGV|SIG"
```

**Questions:**
- Can you see SIGSEGV delivered when the stack guard is touched?
- Is it the same SIGSEGV mechanism as NPE (lab 08)?
- What's the difference between StackOverflowError and OutOfMemoryError?

---

## Part 4: Tune stack size

```bash
# Default
java -cp . StackDepthCounter

# Smaller stack — less memory per thread, smaller max recursion depth
java -Xss128k -cp . StackDepthCounter

# Larger stack
java -Xss4m -cp . StackDepthCounter
```

Watch the mmap call size change:
```bash
strace -e mmap java -Xss128k -cp . Sleep 2>&1 | grep "131072\|0x20000"  # 128KB
strace -e mmap java -Xss4m   -cp . Sleep 2>&1 | grep "4194304\|0x400000" # 4MB
```

**Questions:**
- What is the maximum recursion depth with default `-Xss`?
- With `-Xss128k`? With `-Xss4m`?
- If you have 1000 threads at `-Xss1m`, how much virtual memory do stacks alone use?

---

## What you learned

- Thread stacks are `mmap()`'d regions, one per thread
- Guard pages (`PROT_NONE`) detect stack overflow via SIGSEGV — no explicit depth checking
- `-Xss` controls stack size per thread — matters a lot with many threads
- The JVM uses `sigaltstack()` to handle SIGSEGV on a separate signal stack
  (otherwise stack overflow handling would itself overflow the stack)
