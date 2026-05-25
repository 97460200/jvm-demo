package com.example.jvmdemo.controller;

import com.example.jvmdemo.service.SimulationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Controller
public class SimulationController {

    @Autowired
    private SimulationService simulationService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("activeThreads", simulationService.getActiveCpuThreads());
        model.addAttribute("usedMemory", simulationService.getUsedMemory() / 1024 / 1024);
        model.addAttribute("maxMemory", simulationService.getMaxMemory() / 1024 / 1024);
        return "index";
    }

    @PostMapping("/api/cpu/start")
    @ResponseBody
    public Map<String, Object> startCpuSpike(@RequestParam(defaultValue = "4") int threads) {
        simulationService.startCpuSpike(threads);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("activeThreads", simulationService.getActiveCpuThreads());
        return result;
    }

    @PostMapping("/api/cpu/stop")
    @ResponseBody
    public Map<String, Object> stopCpuSpike() {
        simulationService.stopCpuSpike();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("activeThreads", simulationService.getActiveCpuThreads());
        return result;
    }

    @PostMapping("/api/memory/oom")
    @ResponseBody
    public Map<String, Object> causeOom() {
        simulationService.causeOom();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @PostMapping("/api/memory/clear")
    @ResponseBody
    public Map<String, Object> clearMemory() {
        simulationService.clearMemory();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("usedMemory", simulationService.getUsedMemory() / 1024 / 1024);
        return result;
    }

    @PostMapping("/api/deadlock/create")
    @ResponseBody
    public Map<String, Object> createDeadlock() {
        simulationService.createDeadlock();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @GetMapping("/api/thread-dump")
    @ResponseBody
    public ResponseEntity<byte[]> getThreadDump() {
        String dump = simulationService.getThreadDump();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", "thread-dump.txt");
        return ResponseEntity.ok()
                .headers(headers)
                .body(dump.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/api/status")
    @ResponseBody
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeThreads", simulationService.getActiveCpuThreads());
        status.put("usedMemory", simulationService.getUsedMemory() / 1024 / 1024);
        status.put("maxMemory", simulationService.getMaxMemory() / 1024 / 1024);
        return status;
    }

    @GetMapping("/api/metrics")
    @ResponseBody
    public Map<String, Object> getMetrics() {
        return simulationService.getDetailedMetrics();
    }
}
