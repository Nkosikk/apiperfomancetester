package com.edendale.performance.controller;

import com.edendale.performance.model.LoginRequest;
import com.edendale.performance.model.PerformanceResult;
import com.edendale.performance.model.TestProgress;
import com.edendale.performance.service.PerformanceTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PerformanceController {
    
    private final PerformanceTestService performanceTestService;
    
    @GetMapping("/progress")
    public ResponseEntity<TestProgress> getProgress() {
        return ResponseEntity.ok(performanceTestService.getProgress());
    }
    
    @PostMapping("/test")
    public ResponseEntity<PerformanceResult> runPerformanceTest(
            @RequestParam String url,
            @RequestParam(defaultValue = "100") int numberOfRequests,
            @RequestParam(defaultValue = "10") int concurrentThreads,
            @RequestParam(defaultValue = "0") int durationSeconds,
            @RequestBody LoginRequest payload) {
        
        PerformanceResult result;
        
        if (durationSeconds > 0) {
            // Duration-based test
            result = performanceTestService.executeDurationTest(
                    url, 
                    payload, 
                    durationSeconds,
                    concurrentThreads
            );
        } else {
            // Request count-based test
            result = performanceTestService.executeLoadTest(
                    url, 
                    payload, 
                    numberOfRequests, 
                    concurrentThreads
            );
        }
        
        return ResponseEntity.ok(result);
    }
}
