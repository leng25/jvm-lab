# Lab 01 — Process Startup

**Goal:** Observe what Linux does when you run `java`. No Java code needed for most of this.

---

## Part 1: What syscalls does `java -version` make?

```bash
strace java -version 2>&1 | head -50
```

You'll see the very first syscall: `execve`. That's the kernel replacing the shell process
with the JVM binary. Everything after is the JVM bootstrapping itself.

Now count all syscalls by type:

```bash
strace -c java -version 2>&1
```

**Questions:**
- What is the most frequent syscall? (hint: `mmap` or `openat`)
- How many times does it call `openat`? What is it opening?

---

## Part 2: What shared libraries does the JVM load?

```bash
ldd $(which java)
```

Then watch the dynamic linker load them live:

```bash
strace -e openat java -version 2>&1 | grep "\.so"
```

**Questions:**
- Can you find `libjvm.so`? Where on disk does it live?
- How many `.so` files are loaded?

---

## Part 3: Inspect the live process address space

Run a Java program that sleeps so you have time to inspect it:

```bash
# Terminal 1 — run this
java -cp . Sleep

# Terminal 2 — inspect it
PID=$(pgrep -f Sleep)
cat /proc/$PID/maps
```

Compile and run `Sleep.java` from this directory.

Look for these regions in the output:
- `libjvm.so` — the JVM itself
- `[heap]` — the Java heap (though with modern JVMs it may be anonymous mmap)
- `[stack]` — the main thread's stack
- anonymous `rwx` regions — JIT compiled code (may appear after warmup)
- `.jar` files — memory-mapped by the class loader

```bash
# Summarize by permission type
cat /proc/$PID/maps | awk '{print $2}' | sort | uniq -c | sort -rn
```

**Questions:**
- How many separate memory regions does the JVM process have?
- Can you find a region that is both readable and executable (`r-x`)? What is it?
- What is the address range of the heap?

---

## Part 4: Process status

```bash
cat /proc/$PID/status
```

Look at:
- `VmSize` — total virtual memory
- `VmRSS` — actual physical RAM in use
- `Threads` — number of threads (even a "simple" JVM has many)
- `voluntary_ctxt_switches` — how often it willingly gives up the CPU

**Questions:**
- Why is VmSize so much larger than VmRSS?
- How many threads does a minimal JVM spin up just to run `Sleep`? Why?

---

## What you learned

- `execve` is how a new program starts — the kernel loads the ELF binary
- The JVM is a regular Linux process with a virtual address space just like any other
- Virtual memory is vast and cheap; physical memory (RSS) is what actually costs
- Even a trivial JVM process has many threads (GC threads, JIT compiler threads, etc.)
