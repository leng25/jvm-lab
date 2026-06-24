# Lab 08 — Signals

**Goal:** Understand how the JVM hijacks SIGSEGV for NullPointerException,
and how signals drive things like thread dumps and shutdown hooks.

---

## Background

Linux signals are asynchronous notifications delivered to a process. They're like
software interrupts. The JVM installs custom handlers for several signals and
repurposes them for its own needs — including some that would normally be fatal.

| Signal | Default action | JVM use |
|--------|---------------|---------|
| SIGSEGV | crash | NPE detection, safepoints |
| SIGBUS | crash | stack overflow via guard page |
| SIGQUIT | core dump | thread dump to stderr |
| SIGTERM | terminate | graceful shutdown (shutdown hooks) |
| SIGUSR1 | terminate | (reserved, do not use) |
| SIGUSR2 | terminate | (reserved, do not use) |
| SIGPIPE | terminate | ignored (broken socket connections) |

---

## Part 1: NullPointerException is a SIGSEGV

When you dereference null in Java, the CPU tries to read from address 0.
This triggers a hardware page fault → kernel delivers SIGSEGV → JVM catches it
→ throws NullPointerException. No `if (ref == null)` check needed!

```bash
strace -e signal java -cp . NullTest 2>&1 | grep -i "sigaction\|signal"
```

You'll see the JVM registering its SIGSEGV handler at startup:
```
sigaction(SIGSEGV, {sa_handler=0x..., sa_flags=SA_RESTORER|SA_SIGINFO}, ...)
```

To actually catch the signal being delivered, use:
```bash
# -e signal traces signal delivery events
strace -e signal=SIGSEGV java -cp . NullTest 2>&1
```

**Questions:**
- Can you see SIGSEGV being delivered when the NPE happens?
- What address was accessed? (it will be near 0x0)

---

## Part 2: Thread dump with SIGQUIT

The JVM handles SIGQUIT (Ctrl+\\ on terminal) by printing a full thread dump.
This is one of the most useful production debugging tools.

```bash
# Terminal 1
java -cp . Sleep

# Terminal 2
PID=$(pgrep -f Sleep)
kill -3 $PID       # SIGQUIT = signal 3
# or: kill -SIGQUIT $PID
```

The thread dump appears in the JVM's stderr. It shows:
- All threads with their Java stack traces
- Thread state (RUNNABLE, WAITING, BLOCKED, etc.)
- Lock information and deadlock detection

**Questions:**
- How many threads does the minimal JVM have?
- Can you find the GC threads in the dump?
- What does `WAITING on <0x...>` mean?

---

## Part 3: Shutdown hooks and SIGTERM

```bash
# Terminal 1
java -cp . ShutdownDemo

# Terminal 2
PID=$(pgrep -f ShutdownDemo)
kill $PID           # sends SIGTERM (default)
```

Shutdown hooks run when the JVM receives SIGTERM or when `System.exit()` is called.

```bash
# Watch the signal delivery
strace -e signal -p $PID &
kill $PID
```

**Questions:**
- How long does the JVM wait for shutdown hooks before forcibly exiting?
- What is the difference between `kill $PID` (SIGTERM) and `kill -9 $PID` (SIGKILL)?
- Why can't you catch SIGKILL?

---

## Part 4: JVM signal chain

The JVM uses `sigchain` to allow both the JVM and application code (via sun.misc.Signal)
to handle signals without clobbering each other.

```bash
# What signal handlers are installed?
# (requires gdb or /proc/<pid>/smaps reading — advanced)
cat /proc/$PID/status | grep SigCgt   # caught signals bitmask
```

Decode the bitmask:
```bash
python3 -c "
mask = 0x$(cat /proc/$PID/status | grep SigCgt | awk '{print $2}')
for i in range(1, 65):
    if mask & (1 << (i-1)):
        print(f'Signal {i} is caught')
"
```

**Questions:**
- Which signals does the JVM catch?
- Which signals can never be caught? (hint: SIGKILL=9, SIGSTOP=19)

---

## What you learned

- The JVM repurposes fatal signals (SIGSEGV) for Java exceptions — NPE is a hardware fault
- SIGQUIT triggers thread dumps — useful in production without restart
- Shutdown hooks run on SIGTERM but not SIGKILL
- The JVM installs a signal chain so application code can also handle signals
