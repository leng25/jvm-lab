# Lab 06 — JIT Compilation

**Goal:** See the JVM writing executable machine code into memory at runtime,
observe the mprotect calls that make it executable, and profile compiled code.

---

## Part 1: JIT code in the address space

```bash
# Terminal 1 — run a hot loop long enough for JIT to kick in
java -XX:+PrintCompilation -cp . HotLoop

# Terminal 2
PID=$(pgrep -f HotLoop)

# Look for anonymous executable regions — that's JIT code
cat /proc/$PID/maps | grep "r-xp" | grep -v "\.so\|\.jar\|jvm"
```

Anonymous `r-xp` regions (no file name, executable) = JIT compiled code cache.

**Questions:**
- How many anonymous executable regions are there?
- What size are they? How does this change with more warmup?

---

## Part 2: Watch mprotect during JIT compilation

The JVM writes JIT code with WRITE permissions, then seals it as EXECUTE-only.

```bash
strace -f -e trace=mprotect java -cp . HotLoop 2>&1 | \
    grep "PROT_READ\|PROT_EXEC" | head -30
```

You'll see patterns like:
- `mprotect(addr, size, PROT_READ|PROT_WRITE)` — making writable to write code
- `mprotect(addr, size, PROT_READ|PROT_EXEC)` — sealing as executable

This is W^X (Write XOR Execute) — a security policy where a page can be writable
OR executable, never both simultaneously.

**Questions:**
- Can you correlate mprotect calls with `-XX:+PrintCompilation` output?
- What would happen if the JVM kept pages PROT_READ|PROT_WRITE|PROT_EXEC?

---

## Part 3: Observe JIT compilation tiers

```bash
java -XX:+PrintCompilation -cp . HotLoop 2>&1 | head -50
```

Output columns: `timestamp  id  flags  tier  method  size`

Flags:
- `%` = OSR (on-stack replacement — method compiled while running)
- `s` = synchronized method
- `!` = method has exception handlers
- `n` = native wrapper

Tier column:
- `1` = C1 compiled (fast, less optimized)
- `4` = C2 compiled (slow to compile, heavily optimized)
- `3` = C1 with profiling (transitional)

**Questions:**
- Does the hot loop start at C1 or C2?
- How long until it reaches C2?
- Can you see deoptimization (`made not entrant`) happen?

---

## Part 4: Profile with async-profiler

async-profiler can show you both Java frames and native frames (including JIT code).

```bash
# Download async-profiler
wget https://github.com/async-profiler/async-profiler/releases/latest/download/async-profiler-*-linux-x64.tar.gz
tar xzf async-profiler-*.tar.gz

# Profile a running JVM for 10 seconds
PID=$(pgrep -f HotLoop)
./profiler.sh -d 10 -f hotloop.html $PID
# Open hotloop.html in browser — flame graph
```

**Questions:**
- Can you see the Java method frames in the flame graph?
- What percentage of time is in JIT-compiled code vs interpreter?

---

## What you learned

- JIT compiled code lives in anonymous `mmap` regions in the process address space
- The JVM uses `mprotect()` to transition pages from writable to executable (W^X)
- HotSpot has a tiered compilation system: interpreter → C1 → C2
- Deoptimization happens when assumptions made by the JIT turn out to be wrong
