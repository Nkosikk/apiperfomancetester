package com.edendale.performance.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceResult {
    private int totalRequests;
    private int successfulRequests;
    private int failedRequests;
    private long totalTime;
    private double averageResponseTime;
    private long minResponseTime;
    private long maxResponseTime;
    private double requestsPerSecond;
    private String status;
    private String errorMessage;
    private long p50ResponseTime;
    private long p95ResponseTime;
    private long p99ResponseTime;
    
    // Log file path
    private String logFilePath;
}
