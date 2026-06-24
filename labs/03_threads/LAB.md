# Lab 03 — Threads

**Goal:** See that Java threads are Linux threads, observe `clone()`, and match Java
thread names to kernel thread IDs.

---

## Background

A Java thread is a POSIX thread. When you call `new Thread().start()`, the JVM calls
`pthread_create()`, which internally calls the `clone()` syscall. The kernel creates a new
task that shares the same virtual address space as the parent process.

This is the key difference between a process and a thread at the kernel level:
- `fork()` → new task, **copy** of address space
- `clone(CLONE_VM|CLONE_FS|CLONE_FILES|...)` → new task, **shared** address space = thread

---

## Part 1: Watch `clone()` in strace

```bash
# Write and compile ThreadSpawner.java first, then:
strace -f -e trace=clone java -cp . ThreadSpawner 2>&1 | grep clone
```

The `-f` flag tells strace to follow child threads/processes.

**Questions:**
- What flags are passed to `clone()`? Look for `CLONE_VM`, `CLONE_THREAD`, `CLONE_SIGHAND`
- How many `clone()` calls happen before your `main()` even runs?
- What is the TID (thread ID) of the main thread?

---

## Part 2: Match Java thread names to kernel TIDs

```bash
# Terminal 1
java -cp . ThreadSpawner

# Terminal 2
PID=$(pgrep -f ThreadSpawner)

# Show all threads with their TIDs and names
ps -eLf | grep $PID

# Or with top in thread mode
top -H -p $PID
```

Now from inside Java, print the thread ID:
- Java 19+: `Thread.currentThread().threadId()`
- Older: you'll need JNI or `/proc` — the lab code does this for you

```bash
# /proc/<pid>/task/ has one directory per thread
ls /proc/$PID/task/

# Each thread directory has its own status
cat /proc/$PID/task/<TID>/status | grep -E "Name|Pid|Tgid"
```

**Questions:**
- The TID and PID of the main thread — are they the same or different?
- Can you find the GC threads? What are they named in `/proc`?
- Can you find the JIT compiler threads (C1, C2)?

---

## Part 3: Thread stacks in the address space

```bash
PID=$(pgrep -f ThreadSpawner)
cat /proc/$PID/maps | grep "stack"
```

Each thread has its own stack segment. The main thread's is labeled `[stack]`.
Worker thread stacks are anonymous mappings (not labeled) — you can identify them
by size and position.

```bash
# See all stack-sized anonymous regions
cat /proc/$PID/smaps | awk '/^[0-9a-f]/{region=$0} /VmFlags.*st/{print region}'
```

**Questions:**
- How large is each thread's stack by default?
- How does `-Xss256k` change this? Try running with that flag.
- If you spawn 100 threads, how much virtual memory do the stacks alone consume?

---

## Part 4: Thread scheduling (observation only)

```bash
# See scheduler stats per thread
cat /proc/$PID/task/*/schedstat
# Format: cpu_time(ns)  wait_time(ns)  timeslices
```

```bash
# Use perf to see context switches
perf stat -e context-switches,cpu-migrations -p $PID sleep 5
```

**Questions:**
- How often is the JVM context-switched off the CPU?
- Do all threads get equal CPU time?

---

## What you learned

- `new Thread().start()` → `clone()` syscall
- Java threads are kernel-scheduled — no JVM control over ordering
- Each thread has its own stack in the process address space
- The JVM runs many internal threads you never create (GC, JIT, etc.)
