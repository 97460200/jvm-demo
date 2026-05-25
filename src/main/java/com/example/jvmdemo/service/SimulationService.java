package com.example.jvmdemo.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SimulationService {
    private final AtomicInteger activeCpuThreads = new AtomicInteger(0);
    private final List<byte[]> memoryLeakList = new ArrayList<>();
    private volatile boolean cpuRunning = false;
    private volatile boolean deadlockCreated = false;
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();
    private final List<Thread> deadlockThreads = new ArrayList<>();

    public void startCpuSpike(int threadCount) {
        if (cpuRunning) {
            return;
        }
        cpuRunning = true;
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                activeCpuThreads.incrementAndGet();
                while (cpuRunning) {
                    double result = 0;
                    for (int j = 0; j < 1000000; j++) {
                        result += Math.sin(j) * Math.cos(j);
                    }
                }
                activeCpuThreads.decrementAndGet();
            }, "CPU-Worker-" + i).start();
        }
    }

    public void stopCpuSpike() {
        cpuRunning = false;
    }

    public int getActiveCpuThreads() {
        return activeCpuThreads.get();
    }

    public void causeOom() {
        new Thread(() -> {
            try {
                while (true) {
                    memoryLeakList.add(new byte[10 * 1024 * 1024]);
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "OOM-Generator").start();
    }

    public void clearMemory() {
        memoryLeakList.clear();
        System.gc();
    }

    public long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public long getMaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    public long getTotalMemory() {
        return Runtime.getRuntime().totalMemory();
    }

    public long getFreeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    public int getActiveThreadCount() {
        return Thread.activeCount();
    }

    public void createDeadlock() {
        if (deadlockCreated) {
            return;
        }
        deadlockCreated = true;

        Thread t1 = new Thread(() -> {
            synchronized (lock1) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (lock2) {
                }
            }
        }, "Deadlock-Thread-1");

        Thread t2 = new Thread(() -> {
            synchronized (lock2) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (lock1) {
                }
            }
        }, "Deadlock-Thread-2");

        deadlockThreads.add(t1);
        deadlockThreads.add(t2);
        t1.start();
        t2.start();
    }

    public boolean isDeadlockCreated() {
        return deadlockCreated;
    }

    public String getThreadDump() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        StringBuilder sb = new StringBuilder();
        sb.append("===== THREAD DUMP =====\n\n");

        long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        if (deadlockedThreads != null && deadlockedThreads.length > 0) {
            sb.append("⚠️  DEADLOCK DETECTED!\n");
            for (long id : deadlockedThreads) {
                ThreadInfo info = threadBean.getThreadInfo(id);
                sb.append("  - ").append(info.getThreadName()).append("\n");
            }
            sb.append("\n");
        }

        long[] allThreadIds = threadBean.getAllThreadIds();
        for (long id : allThreadIds) {
            ThreadInfo info = threadBean.getThreadInfo(id, Integer.MAX_VALUE);
            sb.append("\"").append(info.getThreadName()).append("\" ");
            sb.append("id=").append(id).append(" ").append(info.getThreadState()).append("\n");
            for (StackTraceElement ste : info.getStackTrace()) {
                sb.append("    at ").append(ste).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public Map<String, Object> getDetailedMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("usedMemory", getUsedMemory());
        metrics.put("maxMemory", getMaxMemory());
        metrics.put("totalMemory", getTotalMemory());
        metrics.put("freeMemory", getFreeMemory());
        metrics.put("activeThreads", getActiveThreadCount());
        metrics.put("cpuThreads", getActiveCpuThreads());
        metrics.put("deadlockCreated", deadlockCreated);

        Runtime runtime = Runtime.getRuntime();
        metrics.put("availableProcessors", runtime.availableProcessors());

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] deadlocked = threadBean.findDeadlockedThreads();
        metrics.put("deadlockedThreads", deadlocked != null ? deadlocked.length : 0);

        return metrics;
    }
}
