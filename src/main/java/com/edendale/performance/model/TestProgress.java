package com.edendale.performance.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestProgress {
    private boolean running;
    private int totalRequests;
    private int successfulRequests;
    private int failedRequests;
    private double averageResponseTime;
    private long minResponseTime;
    private long maxResponseTime;
    private double requestsPerSecond;
    private long elapsedSeconds;
    private int targetDurationSeconds;
    private long p50ResponseTime;
    private long p95ResponseTime;
    private long p99ResponseTime;
    
    // For real-time chart updates - last N response times
    private List<Long> recentResponseTimes;
}
