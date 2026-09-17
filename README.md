# Memory Profiler

A real time memory and CPU profiler for Linux processes. A C++ agent reads
live stats from `/proc` and streams them over a TCP socket to a Java
dashboard that draws a live updating chart in your terminal. There is also
an optional feature that sends your exported data to the Claude API and
gets back a plain English report on what happened.

## Architecture

```
 ┌──────────────┐        TCP socket        ┌───────────────────┐
 │  C++ Agent   │  ───────────────────────▶ │  Java Dashboard    │
 │              │   "RSS:43200,PEAK:...     │                     │
 │ reads /proc  │    THREADS:8,CPU:3.2"     │  renders live chart │
 │ every 500ms  │   one line every 500ms    │  in the terminal    │
 └──────────────┘                           └───────────┬─────────┘
                                                          │
                                                    press 'e' to export
                                                          ▼
                                              exports/profiler_export_*.csv
                                                          │
                                                    press 'r' to analyze
                                                          ▼
                                              Claude API (claude-sonnet-4-6)
                                                          │
                                                          ▼
                                              reports/report_*.txt
```

The agent and dashboard are two separate programs on purpose. If the
dashboard crashes, the agent keeps running and the data is not lost. This
is the same reasoning real monitoring tools use: keep the thing that
collects data separate from the thing that displays it.

## Prerequisites

You need these installed and available in your terminal:

- **g++** (C++17 support) — `sudo apt install g++`
- **make** — `sudo apt install make`
- **JDK 17 or newer** — `sudo apt install openjdk-17-jdk`
- **Maven** — `sudo apt install maven`

Check each with:
```
g++ --version
make --version
java -version
mvn -version
```

This project only runs on **Linux**, because it reads `/proc`, which does
not exist on macOS or Windows.

## VS Code setup

1. Open the `memory-profiler/` folder directly in VS Code (not a parent
   folder), so the `.vscode/` config loads.
2. Install these two extensions if you do not have them:
   - **C/C++** (Microsoft)
   - **Extension Pack for Java** (Microsoft)
3. Build both sides with `Ctrl+Shift+B` (this runs the "Build Agent (C++)"
   task), or open the Command Palette (`Ctrl+Shift+P`) and run
   `Tasks: Run Task` to see all available tasks, including
   "Build Dashboard (Java/Maven)" and "Build All".

## Running it

**Step 1: build both sides.**
```
make build
```
This compiles the C++ agent into `agent/profiler` and packages the Java
dashboard into `dashboard/target/dashboard.jar`.

**Step 2: get something to profile.** Either use the included demo program
(recommended for your first run) or point it at any real process.

```
cd demo
javac DemoTarget.java
java DemoTarget
```
It prints its own PID when it starts. Copy that number.

**Step 3: start the agent**, pointing it at that PID. This blocks and waits
for the dashboard to connect.
```
cd agent
./profiler --pid <PID> --port 9090 --interval 500
```

**Step 4: start the dashboard**, in a third terminal.
```
cd dashboard
java -jar target/dashboard.jar <PID> localhost 9090
```

You should see a live updating bar chart of memory usage, along with
current stats and a status line.

**Dashboard controls** (type the letter, then press Enter):
- `q` — quit
- `p` — pause / unpause the display
- `e` — export the last ~30 seconds of readings to `exports/`
- `r` — generate an AI report from the most recent export (requires the
  `ANTHROPIC_API_KEY` environment variable, see below)

## The AI report feature

This is optional. To use it:
```
export ANTHROPIC_API_KEY=your-key-here
```
Never put your API key directly in a source file. If you commit it to
GitHub, anyone who finds the repo can use it and run up charges on your
account. Reading it from an environment variable keeps it out of your
code entirely.

Once the key is set, press `e` to export data, then `r` to get a written
analysis. It gets saved to `reports/report_<timestamp>.txt` and also
printed in the terminal.

## What each metric means

- **RSS (Resident Set Size)** — how much physical RAM the process is
  actually using right now, in megabytes.
- **Peak** — the highest RSS the process has reached since it started.
- **Threads** — how many threads the process currently has running.
- **CPU %** — how much of one CPU core the process used, calculated by
  comparing CPU ticks between two readings 500ms apart.

## Project structure

```
memory-profiler/
├── .vscode/              VS Code tasks, debug configs, and settings
├── agent/                the C++ side
│   ├── src/
│   │   ├── main.cpp          entry point, sampling loop, CLI args
│   │   ├── proc_reader.*     reads /proc/<pid>/status and /proc/<pid>/stat
│   │   └── socket_sender.*   raw TCP server socket, sends data lines
│   └── Makefile
├── dashboard/            the Java side
│   ├── src/main/java/dashboard/
│   │   ├── Main.java              entry point, wires everything together
│   │   ├── SocketReceiver.java    background thread, reads the TCP stream
│   │   ├── MetricsParser.java     parses "RSS:...,CPU:..." lines
│   │   ├── Metric.java            immutable data model for one reading
│   │   ├── MetricsStore.java      thread-safe shared state (the "mailbox")
│   │   ├── Dashboard.java         UI thread, draws the terminal with ANSI
│   │   ├── CsvExporter.java       writes readings to a CSV file
│   │   └── AiReportGenerator.java calls the Claude API, no SDK
│   └── pom.xml
├── demo/
│   └── DemoTarget.java   a program that allocates memory so you have
│                         something interesting to watch
├── exports/              CSV files land here when you press 'e'
├── reports/              AI-generated reports land here when you press 'r'
└── Makefile              top-level build shortcut
```


