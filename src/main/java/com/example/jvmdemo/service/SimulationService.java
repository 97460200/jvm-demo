package com.example.jvmdemo.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SimulationService {
    private final AtomicInteger activeCpuThreads = new AtomicInteger(0);
    private final List<byte[]> memoryLeakList = new ArrayList<>();
    private volatile boolean cpuRunning = false;

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
            }).start();
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
        }).start();
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
}
