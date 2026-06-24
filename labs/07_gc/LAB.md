# Lab 07 — Garbage Collection

**Goal:** See GC from the OS perspective — madvise calls, RSS drops, safepoint pauses.

---

## Part 1: GC logs — the Java view

```bash
java -Xmx256m \
     -Xlog:gc*:file=gc.log:time,uptime,level,tags \
     -cp . GCPressure
```

Then tail the log:
```bash
tail -f gc.log
```

Key events to identify:
- `[GC pause (G1 Evacuation Pause)]` — stop the world, move objects
- `[GC concurrent-mark-start]` — concurrent phase, doesn't stop threads
- `[GC pause (G1 Humongous Allocation)]` — large object allocation

**Questions:**
- How long are the stop-the-world pauses?
- What triggers a full GC?

---

## Part 2: GC from the OS — madvise

When G1 or ZGC returns memory to the OS, they call `madvise(MADV_DONTNEED)`.
This tells the kernel: "I'm not using these physical pages, you can reclaim them."
The virtual address range is still reserved, but RSS drops.

```bash
# Terminal 1
java -Xmx512m -XX:+UseG1GC -cp . GCPressure

# Terminal 2
PID=$(pgrep -f GCPressure)
strace -p $PID -e trace=madvise 2>&1 | grep MADV_DONTNEED
```

Simultaneously watch RSS:
```bash
watch -n 1 "cat /proc/$PID/status | grep VmRSS"
```

**Questions:**
- Can you correlate madvise calls with RSS drops?
- Does `-XX:+UseZGC` behave differently?

---

## Part 3: Safepoints

A safepoint is a moment where all Java threads are paused so the GC can safely
inspect the heap. The JVM uses a clever trick: it makes a special memory page
unreadable. Threads periodically poll that page. When they touch it → page fault
→ SIGSEGV → JVM catches it → thread is paused at the safepoint.

```bash
java -Xmx256m \
     -Xlog:safepoint:file=safepoint.log:time,uptime \
     -cp . GCPressure
tail -f safepoint.log
```

Key metrics:
- `Total time for which application threads were stopped` — your latency impact
- `Reaching safepoint` — time waiting for all threads to reach safepoint

**Questions:**
- What fraction of time is spent at safepoints?
- Which operations trigger safepoints besides GC?

---

## Part 4: jstat — live GC stats

```bash
PID=$(pgrep -f GCPressure)

# Print GC stats every second
jstat -gcutil $PID 1000

# Columns: S0  S1  E  O  M  CCS  YGC  YGCT  FGC  FGCT  CGC  CGCT  GCT
# E = Eden used%, O = Old gen used%, YGC = young GC count, FGC = full GC count
```

**Questions:**
- How fast is Eden filling up (% per second)?
- What is the ratio of young GC to full GC?

---

## What you learned

- GC mostly works inside the heap — the OS doesn't see individual object allocations
- Memory is returned to OS via `madvise(MADV_DONTNEED)`, not `munmap()`
- Safepoints use a page fault trick to synchronize all threads
- RSS drops during GC but virtual address space stays constant
