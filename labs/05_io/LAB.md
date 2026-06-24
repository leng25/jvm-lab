# Lab 05 — File & Network I/O

**Goal:** Trace I/O syscalls from Java all the way down to `read()`, `write()`,
and understand how Java NIO's `epoll`-based selector works.

---

## Part 1: Classic blocking I/O

```bash
strace -e trace=openat,read,write,close java -cp . FileReadDemo 2>&1
```

You will see:
- `openat()` — opening the file, returns a file descriptor (integer)
- `read(fd, buf, count)` — reading bytes into a buffer
- `close(fd)` — closing the file descriptor

**Questions:**
- What file descriptor number does your file get? (0=stdin, 1=stdout, 2=stderr)
- How many `read()` calls does Java make to read a 1MB file with an 8KB buffer?
- What happens at the end of the file? (hint: `read()` returns 0)

---

## Part 2: Open file descriptors

```bash
# Terminal 1
java -cp . ServerDemo   # a server that keeps files/sockets open

# Terminal 2
PID=$(pgrep -f ServerDemo)
ls -la /proc/$PID/fd/
lsof -p $PID
```

Every entry in `/proc/$PID/fd/` is a symlink to what the FD refers to:
- regular files → file path
- sockets → `socket:[inode]`
- pipes → `pipe:[inode]`
- `/dev/null`, `/dev/random`, etc.

**Questions:**
- What FDs does the JVM open that you didn't open in your code?
- Can you find the listening server socket?
- What does `lsof` show for a connected socket?

---

## Part 3: NIO Selector and epoll

Java NIO's `Selector` is a thin wrapper over Linux's `epoll`.

```bash
strace -f -e trace=epoll_create1,epoll_ctl,epoll_wait java -cp . NioServerDemo 2>&1 | \
    grep -E "epoll"
```

Watch the sequence:
1. `epoll_create1(0)` — creates the epoll instance, returns an FD
2. `epoll_ctl(epfd, EPOLL_CTL_ADD, fd, ...)` — register a socket to watch
3. `epoll_wait(epfd, events, maxevents, timeout)` — block until something is ready

When a client connects or sends data, `epoll_wait` returns. One thread handles all connections.

**Questions:**
- What timeout does `epoll_wait` use? (look at the last argument)
- When a client connects, which `epoll` operation fires?
- How many threads does the NIO server use vs a thread-per-connection server?

---

## Part 4: Compare blocking vs non-blocking

Run both servers, send 100 requests to each, and compare:

```bash
# Count syscalls for each
strace -c java -cp . BlockingServer &
strace -c java -cp . NioServerDemo &

# Send requests
for i in $(seq 1 100); do curl -s http://localhost:8080/ > /dev/null; done
```

**Questions:**
- Which server makes more syscalls per request?
- Which one needs more threads?
- At what connection count does non-blocking I/O win?

---

## What you learned

- Every Java file/socket operation maps to a specific Linux syscall
- File descriptors are the kernel's handle for everything I/O related
- Java NIO Selector = `epoll` — one thread, many connections, no blocking
- The `/proc/<pid>/fd/` directory shows you every open file descriptor live
