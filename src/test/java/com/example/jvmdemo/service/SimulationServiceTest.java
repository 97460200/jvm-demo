package com.example.jvmdemo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationServiceTest {

    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new SimulationService();
    }

    @Test
    void testGetUsedMemory() {
        long usedMemory = simulationService.getUsedMemory();
        assertTrue(usedMemory > 0, "Used memory should be greater than 0");
    }

    @Test
    void testGetMaxMemory() {
        long maxMemory = simulationService.getMaxMemory();
        assertTrue(maxMemory > 0, "Max memory should be greater than 0");
    }

    @Test
    void testGetActiveCpuThreads() {
        int activeThreads = simulationService.getActiveCpuThreads();
        assertEquals(0, activeThreads, "Initial active CPU threads should be 0");
    }

    @Test
    void testIsDeadlockCreated() {
        assertFalse(simulationService.isDeadlockCreated(), "Deadlock should not be created initially");
    }

    @Test
    void testClearMemory() {
        assertDoesNotThrow(() -> simulationService.clearMemory(), "Clear memory should not throw exception");
    }
}
