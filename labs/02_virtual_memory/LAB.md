# Lab 02 — Virtual Memory & the Heap

**Goal:** Understand how the JVM heap maps to Linux virtual memory concepts.

---

## Background

When you write `new byte[1_000_000]` in Java, what actually happens?

1. The JVM's allocator finds space in its heap (already mmap'd from the OS)
2. It zeroes the memory (Java guarantees arrays are zero-initialized)
3. Returns a reference

The key insight: the JVM requests memory from the OS in large chunks upfront (your `-Xmx`),
then manages it internally. Most `new` calls do NOT make syscalls.

---

## Part 1: Watch the JVM request memory from the OS

```bash
# Trace only memory-related syscalls during JVM startup
strace -e trace=mmap,munmap,mprotect,brk java -Xmx256m -cp . HeapAllocator 2>&1 | head -80
```

Compile `HeapAllocator.java` from this directory first.

**Questions:**
- Can you find the large `mmap` call that corresponds to `-Xmx256m` (256MB = 268435456 bytes)?
- What flags does that `mmap` call use? (look for `MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE`)
- How many mmap calls happen before your program even starts?

---

## Part 2: Virtual vs Resident memory

```bash
# Terminal 1
java -Xmx1g -cp . HeapAllocator

# Terminal 2 — watch memory over time
PID=$(pgrep -f HeapAllocator)
watch -n 1 "cat /proc/$PID/status | grep -E 'VmSize|VmRSS|VmPeak'"
```

The program gradually allocates memory. Watch how VSZ and RSS change differently.

**Questions:**
- When you allocate 1GB with `-Xmx1g` but haven't touched the memory yet, is RSS 1GB? Why not?
- As you touch more memory, RSS grows. Is it ever equal to VSZ?
- What is "overcommit" and what does it mean here?

---

## Part 3: Read the memory map of a running JVM

```bash
PID=$(pgrep -f HeapAllocator)

# Full map
cat /proc/$PID/maps

# Summarize anonymous private mappings (the heap, stacks, JIT code)
cat /proc/$PID/maps | grep "^[0-9a-f]" | awk '$6=="" {print $1, $2, $5}' | head -40

# Detailed stats per region
cat /proc/$PID/smaps | grep -A 15 "heap"
```

**Questions:**
- Can you identify the main Java heap region? (large anonymous private mapping)
- Find the `[stack]` of the main thread. How big is it?
- What does `Size` vs `Rss` vs `Pss` mean in smaps?

---

## Part 4: pmap — a friendlier view

```bash
pmap -x $PID | sort -k3 -rn | head -20
```

The `-x` flag shows extended info: virtual size, RSS, dirty pages.

**Questions:**
- What mapping has the highest RSS?
- What is `libjvm.so`'s contribution to RSS?

---

## Part 5: What happens when the heap is full?

```bash
# Run with a tiny heap and watch it OOM
java -Xmx32m -cp . OOMTest 2>&1
```

Check the kernel OOM killer log:
```bash
dmesg | grep -i "killed process" | tail -5
# or
journalctl -k | grep -i oom | tail -10
```

**Questions:**
- What Linux mechanism kills processes when memory is exhausted?
- How can you protect a process from the OOM killer? (`/proc/<pid>/oom_score_adj`)

---

## What you learned

- The JVM heap is just a big `mmap(MAP_ANONYMOUS)` region
- Virtual memory is cheap — Linux only backs it with physical pages when touched
- VSZ (virtual size) >> RSS (resident set) for JVM processes
- The OOM killer is the kernel's last resort when physical memory is exhausted
