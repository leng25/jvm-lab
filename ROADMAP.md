# JVM Internals from a Linux Perspective — Learning Roadmap

A hands-on investigation into what the JVM actually does at the OS level.
Each module has theory, a lab, and specific tools to use.

---

## How to use this roadmap

- Work through modules in order — each one builds on the previous
- Every module has a `labs/` directory with runnable Java code and instructions
- The goal is always: write Java → observe what Linux does → understand why

---

## Modules

| # | Topic | Linux Concept | Key Tool |
|---|-------|--------------|----------|
| 01 | [Process Startup](#01) | `exec`, ELF, address space | `strace`, `/proc` |
| 02 | [Virtual Memory & the Heap](#02) | `mmap`, pages, overcommit | `pmap`, `/proc/maps` |
| 03 | [Threads](#03) | `clone`, CFS scheduler | `ps`, `top -H` |
| 04 | [Synchronization](#04) | `futex`, memory barriers | `strace -e futex` |
| 05 | [File & Network I/O](#05) | file descriptors, `epoll` | `lsof`, `strace` |
| 06 | [JIT Compilation](#06) | executable pages, `mprotect` | `perf`, `/proc/maps` |
| 07 | [Garbage Collection](#07) | `madvise`, safepoints | `jstat`, GC logs |
| 08 | [Signals](#08) | `sigaction`, SIGSEGV, NPE | `strace -e signal` |
| 09 | [Stack & Stack Overflow](#09) | stack pages, guard pages | `/proc/maps`, `ulimit` |
| 10 | [Putting it all together](#10) | flame graphs, perf, BPF | `perf`, `bpftrace` |

---

## <a name="01"></a>01 — Process Startup

**What happens when you type `java MyApp`**

### Theory
- The shell calls `execve("/usr/bin/java", ...)` — this is a syscall that replaces the current process image with the JVM binary
- The kernel reads the ELF header of the `java` binary, loads its segments into virtual memory
- The dynamic linker (`ld.so`) loads shared libraries (libc, libjvm.so)
- The JVM then parses your classpath, loads bytecode, and starts the main thread
- Your entire program runs inside one Linux process

### Lab
`labs/01_process_startup/`

### Key questions to answer
- What syscalls does `java -version` make?
- How many shared libraries does the JVM load?
- What does the process address space look like before `main()` runs?

---

## <a name="02"></a>02 — Virtual Memory & the Heap

**Where does `new Object()` actually live?**

### Theory
- Linux gives every process its own virtual address space (typically 128TB on x86-64)
- Physical RAM is mapped to virtual pages (4KB each) on demand — pages start as "not present"
- The JVM requests a large chunk of memory upfront with `mmap()` — this is your heap (`-Xmx`)
- Virtual memory is committed (reserved) but pages aren't backed by physical RAM until touched
- When GC runs, it may return memory to the OS via `madvise(MADV_DONTNEED)`

### Lab
`labs/02_virtual_memory/`

### Key questions to answer
- How does `-Xmx` relate to `mmap()` calls?
- What's the difference between virtual size (VSZ) and resident size (RSS)?
- Where in the address space does the heap live vs the JIT code vs the stack?

---

## <a name="03"></a>03 — Threads

**Java threads are Linux threads**

### Theory
- Every `new Thread()` in Java creates a POSIX thread via `pthread_create()` which calls `clone()` syscall
- `clone()` creates a new task that shares the same address space as the parent (unlike `fork()`)
- The kernel schedules Java threads directly — the JVM has no control over which thread runs next
- Each thread has its own stack (default 512KB-1MB on Linux for Java threads)
- You can see all threads at `/proc/<pid>/task/`

### Lab
`labs/03_threads/`

### Key questions to answer
- Can you match Java thread names to kernel thread IDs (TID)?
- What does `clone()` look like in strace?
- How does thread count affect `/proc/<pid>/status` (Threads field)?

---

## <a name="04"></a>04 — Synchronization

**What `synchronized` and `volatile` really do**

### Theory
- `synchronized` has two paths:
  - Uncontended: JVM uses object header bits (no syscall — pure userspace)
  - Contended: JVM calls `futex(FUTEX_WAIT)` to park the thread in the kernel
- `volatile` is not a syscall — it's a CPU memory barrier instruction (`mfence`, `lock xchg`)
  that prevents CPU reordering and forces cache coherency across cores
- `LockSupport.park()` / `unpark()` → `futex()` syscall

### Lab
`labs/04_synchronization/`

### Key questions to answer
- Under contention, how many `futex` syscalls per second?
- Can you see the difference between uncontended vs contended lock in strace?
- What assembly does `volatile` read/write generate vs a plain field?

---

## <a name="05"></a>05 — File & Network I/O

**From `InputStream.read()` to `read()` syscall**

### Theory
- Every file/socket is a file descriptor (integer) in Linux
- `FileInputStream.read()` → `read(fd, buf, count)` syscall
- Java NIO Selectors use `epoll` — the kernel's scalable I/O event notification mechanism
  - `epoll_create1()` → create epoll instance
  - `epoll_ctl()` → register file descriptors to watch
  - `epoll_wait()` → block until one or more FDs are ready
- Netty, Tomcat, and virtually all Java servers are built on top of `epoll`
- You can see all open file descriptors: `ls -la /proc/<pid>/fd/`

### Lab
`labs/05_io/`

### Key questions to answer
- How does reading a file look in strace vs reading a socket?
- How many `epoll_wait` calls does a simple HTTP server make per request?
- What's the difference between blocking and non-blocking I/O at the syscall level?

---

## <a name="06"></a>06 — JIT Compilation

**The JVM writing machine code at runtime**

### Theory
- HotSpot profiles bytecode and compiles "hot" methods to native x86-64 instructions at runtime
- The JIT allocates memory regions with `mmap(PROT_READ|PROT_WRITE)`, writes code, then calls
  `mprotect(PROT_READ|PROT_EXEC)` to make it executable (W^X security policy)
- These code regions show up in `/proc/<pid>/maps` as anonymous executable mappings
- `perf` can profile JIT code if you use `-XX:+PreserveFramePointer` and perf map agent
- JIT compilation happens on separate compiler threads (C1, C2)

### Lab
`labs/06_jit/`

### Key questions to answer
- Can you find the JIT code cache in `/proc/<pid>/maps`?
- What does `mprotect` look like in strace during JVM warmup?
- Can you see a method go from interpreted → C1 → C2 compiled?

---

## <a name="07"></a>07 — Garbage Collection

**Memory reclamation from the OS perspective**

### Theory
- The GC manages the heap internally — most GC work does NOT involve syscalls
- The JVM uses `madvise(MADV_DONTNEED)` to tell the kernel "this memory is free,
  you can reclaim these physical pages" without giving up the virtual address range
- Stop-the-world pauses: JVM signals all threads to reach a "safepoint" — a point
  in the bytecode where the GC can safely inspect/move objects
- Safepoints use a memory page trick: JVM makes a page unreadable (`mprotect`),
  threads poll it, and fault when they hit it → kernel delivers SIGSEGV → JVM catches it
- ZGC/Shenandoah use load barriers and concurrent marking to minimize pauses

### Lab
`labs/07_gc/`

### Key questions to answer
- Can you see `madvise` calls during a GC cycle in strace?
- How does RSS (resident memory) change before/after GC?
- What do safepoint pauses look like in GC logs?

---

## <a name="08"></a>08 — Signals

**The JVM's dark use of SIGSEGV**

### Theory
- Linux signals are asynchronous notifications sent to a process (like hardware interrupts, but in software)
- The JVM installs custom signal handlers for:
  - `SIGSEGV` — used to implement NullPointerException (dereferencing null triggers a page fault → SIGSEGV → JVM converts to NPE)
  - `SIGBUS` — stack overflow detection via guard pages
  - `SIGUSR1` / `SIGUSR2` — used internally for thread coordination
  - `SIGQUIT` — triggers thread dump (`kill -3 <pid>`)
  - `SIGTERM` / `SIGINT` — graceful shutdown hooks
- This is why you should never use `SIGSEGV` or `SIGUSR1` from outside the JVM

### Lab
`labs/08_signals/`

### Key questions to answer
- Can you catch a SIGSEGV (NPE) in strace?
- What happens at the OS level when you do `kill -3 <pid>`?
- How does the JVM's signal handler chain work?

---

## <a name="09"></a>09 — Stack & Stack Overflow

**Thread stacks and guard pages**

### Theory
- Each thread gets a fixed-size stack allocated with `mmap()`
- The kernel grows the stack on demand (page faults), but within a limit (`ulimit -s`)
- The JVM places a "guard page" (`mprotect(PROT_NONE)`) at the bottom of each thread stack
- When you overflow the stack, you touch the guard page → `SIGSEGV` → JVM throws `StackOverflowError`
- Java's default thread stack size is 512KB (client) or 1MB (server) — controllable with `-Xss`

### Lab
`labs/09_stack/`

### Key questions to answer
- Where is a thread's stack in `/proc/<pid>/maps`?
- Can you observe guard pages with `pmap -x`?
- How does `-Xss` change the `mmap` call size?

---

## <a name="10"></a>10 — Putting It All Together

**Profiling a real JVM workload end-to-end**

### Theory
- `perf` is Linux's built-in profiler — it uses hardware performance counters
- `bpftrace` / BPF lets you write small scripts that hook into kernel events with near-zero overhead
- Flame graphs visualize where CPU time is spent across the full stack (Java → JVM → kernel)
- `async-profiler` is the best Java profiler for this because it correctly captures JIT frames

### Lab
`labs/10_profiling/`

### Key questions to answer
- Can you build a flame graph of a Java program that shows both Java frames and kernel frames?
- Can you write a bpftrace script that counts `mmap` calls from a specific JVM?
- Can you identify a GC pause in a `perf` trace?

---

## Tools Reference

| Tool | Install | Purpose |
|------|---------|---------|
| `strace` | `apt install strace` | Trace syscalls |
| `perf` | `apt install linux-perf` | CPU profiling, hardware counters |
| `pmap` | built-in | Process memory map |
| `lsof` | `apt install lsof` | Open file descriptors |
| `bpftrace` | `apt install bpftrace` | BPF-based dynamic tracing |
| `async-profiler` | GitHub release | Java-aware profiler with kernel frames |
| `jstat` | JDK built-in | GC stats |
| `jstack` | JDK built-in | Thread dumps |
| `/proc/<pid>/` | built-in | Everything about a running process |

---

## Prerequisites

- Linux machine or VM (Ubuntu 22.04+ recommended)
- JDK 17+ (`apt install openjdk-21-jdk`)
- `sudo` access (some tools need it)
- Basic comfort with the terminal
