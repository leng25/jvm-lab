# Lab 10 — Putting It All Together

**Goal:** Profile a real JVM workload end-to-end with flame graphs that show
Java frames, JVM frames, and kernel frames in one picture.

---

## Part 1: perf — Linux's built-in profiler

`perf` uses hardware performance counters and kernel tracing to profile anything.

```bash
# Install
apt install linux-perf

# Profile a Java program for 10 seconds (requires -XX:+PreserveFramePointer)
java -XX:+PreserveFramePointer -cp . WorkloadDemo &
PID=$!

sudo perf record -F 99 -p $PID -g -- sleep 10
sudo perf report --stdio | head -60
```

`-F 99` = sample at 99Hz (avoiding lockstep with 100Hz kernel timer)
`-g` = capture call graphs (stack traces)

**Questions:**
- What functions consume the most CPU?
- Can you see JVM internal functions (C++ code) alongside Java?

---

## Part 2: async-profiler flame graphs

async-profiler avoids the JVM's frame pointer issues and produces better Java flame graphs.

```bash
# Download
wget https://github.com/async-profiler/async-profiler/releases/latest/download/async-profiler-3.0-linux-x64.tar.gz
tar xzf async-profiler-3.0-linux-x64.tar.gz
cd async-profiler-3.0-linux-x64

# Profile
java -XX:+PreserveFramePointer -cp .. WorkloadDemo &
PID=$!

# Collect CPU profile for 10s, output flame graph
./asprof -d 10 -f flame.html $PID
```

Open `flame.html` in a browser. The flame graph shows:
- Bottom: kernel frames (syscalls, page faults, scheduler)
- Middle: JVM C++ frames
- Top: Java method frames

Click on any frame to zoom in.

**Questions:**
- What fraction of CPU time is your application code vs JVM overhead?
- Can you find GC frames in the flame graph during a GC pause?
- Can you see `epoll_wait` at the bottom when the server is idle?

---

## Part 3: Allocation profiling

```bash
# Profile allocations instead of CPU
./asprof -d 10 -e alloc -f alloc.html $PID
```

This shows you what Java code is allocating the most memory — useful for finding
GC pressure hotspots.

**Questions:**
- Which methods allocate the most?
- Are there any unexpected allocation sites?

---

## Part 4: bpftrace — dynamic kernel tracing

`bpftrace` lets you write mini scripts that hook into kernel events with near-zero overhead.

```bash
apt install bpftrace

# Count mmap calls from a specific PID
PID=$(pgrep -f WorkloadDemo)
sudo bpftrace -e "tracepoint:syscalls:sys_enter_mmap /pid == $PID/ { @[comm] = count(); }"

# Trace futex calls with arguments
sudo bpftrace -e "
tracepoint:syscalls:sys_enter_futex /pid == $PID/ {
    @ops[args->op & 0x7f] = count();
}
interval:s:5 { print(@ops); clear(@ops); }"

# Track page faults (= memory actually being used for first time)
sudo bpftrace -e "
software:page-faults:1 /pid == $PID/ {
    @faults = count();
}
interval:s:1 { print(@faults); clear(@faults); }"
```

**Questions:**
- How many page faults per second during heap warmup vs steady state?
- How many futex calls during a burst of requests vs idle?

---

## Part 5: Correlate everything

Run the workload under load and simultaneously:

```bash
# Terminal 1: the JVM
java -XX:+PreserveFramePointer \
     -Xlog:gc*:file=gc.log \
     -Xlog:safepoint:file=safepoint.log \
     -cp . WorkloadDemo

# Terminal 2: watch memory
watch -n 1 "cat /proc/\$(pgrep -f WorkloadDemo)/status | grep -E 'VmRSS|Threads'"

# Terminal 3: watch syscalls at aggregate level
PID=$(pgrep -f WorkloadDemo)
sudo perf stat -e 'syscalls:sys_enter_*' -p $PID sleep 10 2>&1 | sort -k1 -rn | head -20

# Terminal 4: async-profiler
./asprof start $PID && sleep 30 && ./asprof stop -f final.html $PID
```

Then cross-reference:
- GC log pauses ↔ safepoint log ↔ flame graph GC frames ↔ RSS drops

---

## What you learned

- `perf` + async-profiler give you a complete picture from kernel to Java
- Flame graphs let you see the full call stack across language boundaries
- `bpftrace` lets you instrument the kernel with near-zero overhead
- All the layers we studied (memory, threads, I/O, JIT, GC) are visible in one profile
