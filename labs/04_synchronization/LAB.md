# Lab 04 — Synchronization

**Goal:** See the difference between uncontended locks (no syscall) and
contended locks (futex syscall), and understand what `volatile` does at the CPU level.

---

## Background

`synchronized` in Java has two modes:
- **Uncontended:** the lock is free, the JVM flips a bit in the object header — pure userspace, zero syscalls
- **Contended:** another thread holds the lock, the JVM calls `futex(FUTEX_WAIT)` to put
  the thread to sleep in the kernel until the lock is released

`futex` = "fast userspace mutex". The fast path never enters the kernel.
Only the slow path (contention) does.

---

## Part 1: Uncontended lock — no syscalls

```bash
# Single-threaded synchronized — should show zero futex calls
strace -e futex java -cp . UncontendedLock 2>&1 | grep -c futex
```

**Questions:**
- How many futex calls did you see?
- Where is the overhead going if not syscalls?

---

## Part 2: Contended lock — futex appears

```bash
strace -f -e futex -c java -cp . ContendedLock 2>&1
```

Now you'll see `futex` calls. The key ones:
- `futex(addr, FUTEX_WAIT, ...)` — thread going to sleep waiting for lock
- `futex(addr, FUTEX_WAKE, ...)` — thread waking up another thread

**Questions:**
- How many futex calls per second under heavy contention?
- What does contention cost in terms of throughput? (compare the two programs' output)

---

## Part 3: volatile — no syscall, but assembly matters

`volatile` does NOT use futex or any syscall. It emits CPU memory barrier instructions.

```bash
# Compile with javac, then disassemble the bytecode
javac VolatileDemo.java
javap -c VolatileDemo
```

To see the actual x86-64 instructions the JIT generates:
```bash
# Requires hsdis (HotSpot disassembler plugin) — see install note below
java -XX:+PrintAssembly -XX:CompileOnly=VolatileDemo.counter \
     -cp . VolatileDemo 2>&1 | grep -A 5 -B 5 "mfence\|lock "
```

Look for `mfence` or `lock addl` instructions — these are the memory barriers.

**Install hsdis (Ubuntu):**
```bash
apt install libhsdis0-fcml   # or build from source
# Then: -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly
```

**Questions:**
- A volatile write vs non-volatile write: what instruction is different?
- Why does a memory barrier matter on multi-core CPUs?
  (hint: CPU caches, store buffers, instruction reordering)

---

## Part 4: LockSupport.park/unpark

`LockSupport.park()` is used by `ReentrantLock`, `CompletableFuture`, virtual threads, etc.
It directly calls `futex`.

```bash
strace -f -e futex java -cp . ParkDemo 2>&1 | grep futex | head -20
```

**Questions:**
- Which futex operation corresponds to `park()`? Which to `unpark()`?
- How does this differ from `Object.wait()` / `notify()`?

---

## What you learned

- Uncontended `synchronized` = zero syscalls (fast path in object header)
- Contended `synchronized` = `futex(FUTEX_WAIT)` — the thread sleeps in the kernel
- `volatile` = CPU memory barrier instruction, not a syscall
- All high-level Java concurrency primitives (ReentrantLock, semaphores, etc.) bottom out at `futex`
