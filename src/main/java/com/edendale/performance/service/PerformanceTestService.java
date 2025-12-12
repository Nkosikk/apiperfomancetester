package com.edendale.performance.service;

import com.edendale.performance.model.LoginRequest;
import com.edendale.performance.model.PerformanceResult;
import com.edendale.performance.model.RequestLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import com.edendale.performance.model.TestProgress;

@Slf4j
@Service
public class PerformanceTestService {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private static final String LOGS_DIR = "logs";
    private static final String LOG_FILE_NAME = "performance_test.log";
    
    // Shared connection-pooled HTTP client for better performance
    private final CloseableHttpClient sharedHttpClient;
    
    // Real-time progress tracking
    private final AtomicBoolean testRunning = new AtomicBoolean(false);
    private final AtomicInteger liveSuccessCount = new AtomicInteger(0);
    private final AtomicInteger liveFailureCount = new AtomicInteger(0);
    private final AtomicLong liveStartTime = new AtomicLong(0);
    private final AtomicInteger liveDurationTarget = new AtomicInteger(0);
    private final CopyOnWriteArrayList<Long> liveResponseTimes = new CopyOnWriteArrayList<>();
    
    public TestProgress getProgress() {
        TestProgress progress = new TestProgress();
        progress.setRunning(testRunning.get());
        
        if (!testRunning.get() && liveResponseTimes.isEmpty()) {
            return progress;
        }
        
        int success = liveSuccessCount.get();
        int failed = liveFailureCount.get();
        int total = success + failed;
        
        progress.setTotalRequests(total);
        progress.setSuccessfulRequests(success);
        progress.setFailedRequests(failed);
        progress.setTargetDurationSeconds(liveDurationTarget.get());
        
        long startTime = liveStartTime.get();
        if (startTime > 0) {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            progress.setElapsedSeconds(elapsed);
            if (elapsed > 0) {
                progress.setRequestsPerSecond((double) total / elapsed);
            }
        }
        
        if (!liveResponseTimes.isEmpty()) {
            List<Long> times = new ArrayList<>(liveResponseTimes);
            
            double avg = times.stream().mapToLong(Long::longValue).average().orElse(0);
            long min = times.stream().mapToLong(Long::longValue).min().orElse(0);
            long max = times.stream().mapToLong(Long::longValue).max().orElse(0);
            
            progress.setAverageResponseTime(avg);
            progress.setMinResponseTime(min);
            progress.setMaxResponseTime(max);
            
            // Calculate percentiles
            Collections.sort(times);
            progress.setP50ResponseTime(getPercentile(times, 50));
            progress.setP95ResponseTime(getPercentile(times, 95));
            progress.setP99ResponseTime(getPercentile(times, 99));
            
            // Return last 100 response times for chart
            int size = times.size();
            int start = Math.max(0, size - 100);
            progress.setRecentResponseTimes(times.subList(start, size));
        }
        
        return progress;
    }
    
    private void resetProgress() {
        liveSuccessCount.set(0);
        liveFailureCount.set(0);
        liveStartTime.set(System.currentTimeMillis());
        liveResponseTimes.clear();
    }
    
    public PerformanceTestService() {
        // Create a high-performance connection-pooled HTTP client
        org.apache.http.impl.conn.PoolingHttpClientConnectionManager connManager = 
            new org.apache.http.impl.conn.PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(500);  // Max total connections
        connManager.setDefaultMaxPerRoute(100);  // Max connections per route
        
        org.apache.http.client.config.RequestConfig requestConfig = org.apache.http.client.config.RequestConfig.custom()
            .setConnectTimeout(30000)
            .setSocketTimeout(60000)
            .setConnectionRequestTimeout(5000)
            .build();
        
        this.sharedHttpClient = HttpClients.custom()
            .setConnectionManager(connManager)
            .setDefaultRequestConfig(requestConfig)
            .build();
    }

    /**
     * Execute a duration-based load test - runs for specified seconds
     */
    public PerformanceResult executeDurationTest(String url, LoginRequest payload, int durationSeconds, int concurrentThreads) {
        // Initialize real-time progress tracking
        testRunning.set(true);
        resetProgress();
        liveDurationTarget.set(durationSeconds);
        
        PerformanceResult result = new PerformanceResult();
        List<Long> responseTimes = new CopyOnWriteArrayList<>();
        List<RequestLog> requestLogs = new CopyOnWriteArrayList<>();
        AtomicInteger requestCounter = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        String jsonPayload = gson.toJson(payload);
        
        // Create logs directory
        File logsDir = new File(LOGS_DIR);
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        
        String logFilePath = LOGS_DIR + "/" + LOG_FILE_NAME;
        
        // Delete previous log file
        File logFile = new File(logFilePath);
        if (logFile.exists()) {
            logFile.delete();
            log.info("Previous log file deleted");
        }
        
        log.info("========================================");
        log.info("🚀 STARTING DURATION-BASED PERFORMANCE TEST");
        log.info("========================================");
        log.info("Target URL: {}", url);
        log.info("Duration: {} seconds", durationSeconds);
        log.info("Concurrent Threads: {}", concurrentThreads);
        log.info("Log File: {}", logFilePath);
        log.info("========================================");
        
        try (PrintWriter logWriter = new PrintWriter(new FileWriter(logFilePath))) {
            // Write header
            writeDurationLogHeader(logWriter, url, durationSeconds, concurrentThreads, jsonPayload);
            logWriter.flush();
            
            // Lock object for synchronized log writing
            final Object logLock = new Object();
            
            ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreads);
            long startTime = System.currentTimeMillis();
            long endTimeTarget = startTime + (durationSeconds * 1000L);
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            // Keep submitting requests until duration expires
            while (System.currentTimeMillis() < endTimeTarget) {
                final int requestNum = requestCounter.incrementAndGet();
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    if (System.currentTimeMillis() < endTimeTarget) {
                        RequestLog reqLog = sendRequestWithLogging(url, payload, jsonPayload, requestNum);
                        requestLogs.add(reqLog);
                        
                        // Write log immediately (thread-safe)
                        synchronized (logLock) {
                            writeRequestLog(logWriter, reqLog);
                            logWriter.flush();
                        }
                        
                        // Update live progress
                        liveResponseTimes.add(reqLog.getResponseTime());
                        
                        if (reqLog.getStatusCode() >= 200 && reqLog.getStatusCode() < 300) {
                            responseTimes.add(reqLog.getResponseTime());
                            successCount.incrementAndGet();
                            liveSuccessCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                            liveFailureCount.incrementAndGet();
                        }
                    }
                }, executorService);
                futures.add(future);
                
                // Small delay to prevent overwhelming thread pool
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // Wait for remaining tasks (with timeout)
            for (CompletableFuture<Void> future : futures) {
                try {
                    future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Ignore timeouts for stragglers
                }
            }
            
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            
            executorService.shutdownNow();
            
            int totalRequests = requestLogs.size();
            
            // Calculate statistics
            if (!responseTimes.isEmpty()) {
                List<Long> sortedTimes = new ArrayList<>(responseTimes);
                Collections.sort(sortedTimes);
                
                double averageTime = sortedTimes.stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0);
                
                long minTime = sortedTimes.stream()
                        .mapToLong(Long::longValue)
                        .min()
                        .orElse(0);
                
                long maxTime = sortedTimes.stream()
                        .mapToLong(Long::longValue)
                        .max()
                        .orElse(0);
                
                long p50 = getPercentile(sortedTimes, 50);
                long p95 = getPercentile(sortedTimes, 95);
                long p99 = getPercentile(sortedTimes, 99);
                
                double requestsPerSecond = (totalRequests / (double) totalTime) * 1000;
                
                writeLogSummary(logWriter, totalRequests, successCount.get(), failureCount.get(), 
                               totalTime, averageTime, minTime, maxTime, p50, p95, p99, requestsPerSecond);
                
                log.info("========================================");
                log.info("📊 DURATION TEST COMPLETED - SUMMARY");
                log.info("========================================");
                log.info("Duration: {} seconds", durationSeconds);
                log.info("Total Requests: {}", totalRequests);
                log.info("Successful: {} ✓", successCount.get());
                log.info("Failed: {} ✗", failureCount.get());
                log.info("TPS: {}", String.format("%.2f", requestsPerSecond));
                log.info("========================================");
                
                result.setTotalRequests(totalRequests);
                result.setSuccessfulRequests(successCount.get());
                result.setFailedRequests(failureCount.get());
                result.setTotalTime(totalTime);
                result.setAverageResponseTime(averageTime);
                result.setMinResponseTime(minTime);
                result.setMaxResponseTime(maxTime);
                result.setP50ResponseTime(p50);
                result.setP95ResponseTime(p95);
                result.setP99ResponseTime(p99);
                result.setRequestsPerSecond(requestsPerSecond);
                result.setStatus("SUCCESS");
                result.setLogFilePath(logFilePath);
                
            } else {
                result.setTotalRequests(totalRequests);
                result.setSuccessfulRequests(0);
                result.setFailedRequests(totalRequests);
                result.setStatus("FAILED");
                result.setErrorMessage("All requests failed");
                result.setLogFilePath(logFilePath);
            }
            
        } catch (Exception e) {
            log.error("❌ Duration test execution failed", e);
            result.setStatus("ERROR");
            result.setErrorMessage(e.getMessage());
        } finally {
            testRunning.set(false);
        }
        
        return result;
    }
    
    private void writeDurationLogHeader(PrintWriter writer, String url, int durationSeconds, int threads, String payload) {
        writer.println("================================================================================");
        writer.println("                       API PERFORMANCE TEST LOG                                 ");
        writer.println("================================================================================");
        writer.println();
        writer.println("Test Started: " + LocalDateTime.now().format(formatter));
        writer.println("Target URL: " + url);
        writer.println("Test Mode: DURATION-BASED");
        writer.println("Duration: " + durationSeconds + " seconds");
        writer.println("Concurrent Threads: " + threads);
        writer.println("Request Payload: " + payload);
        writer.println();
        writer.println("================================================================================");
        writer.println("                              REQUEST LOGS                                      ");
        writer.println("================================================================================");
        writer.println();
    }

    public PerformanceResult executeLoadTest(String url, LoginRequest payload, int numberOfRequests, int concurrentThreads) {
        // Initialize real-time progress tracking
        testRunning.set(true);
        resetProgress();
        liveDurationTarget.set(0);
        
        PerformanceResult result = new PerformanceResult();
        List<Long> responseTimes = new ArrayList<>();
        List<RequestLog> requestLogs = new CopyOnWriteArrayList<>();
        AtomicInteger requestCounter = new AtomicInteger(0);
        
        String jsonPayload = gson.toJson(payload);
        
        // Create logs directory
        File logsDir = new File(LOGS_DIR);
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        
        // Use fixed log file name (overwrites on each run)
        String logFilePath = LOGS_DIR + "/" + LOG_FILE_NAME;
        
        // Delete previous log file
        File logFile = new File(logFilePath);
        if (logFile.exists()) {
            logFile.delete();
            log.info("Previous log file deleted");
        }
        
        log.info("========================================");
        log.info("🚀 STARTING PERFORMANCE TEST");
        log.info("========================================");
        log.info("Target URL: {}", url);
        log.info("Total Requests: {}", numberOfRequests);
        log.info("Concurrent Threads: {}", concurrentThreads);
        log.info("Log File: {}", logFilePath);
        log.info("========================================");
        
        try (PrintWriter logWriter = new PrintWriter(new FileWriter(logFilePath))) {
            // Write header to log file
            writeLogHeader(logWriter, url, numberOfRequests, concurrentThreads, jsonPayload);
            logWriter.flush();
            
            // Lock object for synchronized log writing
            final Object logLock = new Object();
            
            ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreads);
            List<CompletableFuture<RequestLog>> futures = new ArrayList<>();
            
            long startTime = System.currentTimeMillis();
            
            // Submit all tasks
            for (int i = 0; i < numberOfRequests; i++) {
                final int requestNum = requestCounter.incrementAndGet();
                CompletableFuture<RequestLog> future = CompletableFuture.supplyAsync(() -> {
                    RequestLog reqLog = sendRequestWithLogging(url, payload, jsonPayload, requestNum);
                    
                    // Write log immediately (thread-safe)
                    synchronized (logLock) {
                        writeRequestLog(logWriter, reqLog);
                        logWriter.flush();
                    }
                    
                    return reqLog;
                }, executorService);
                futures.add(future);
            }
            
            // Wait for all tasks and collect results
            int successCount = 0;
            int failureCount = 0;
            
            for (CompletableFuture<RequestLog> future : futures) {
                RequestLog reqLog = future.join();
                requestLogs.add(reqLog);
                
                // Update live progress
                liveResponseTimes.add(reqLog.getResponseTime());
                
                if (reqLog.getStatusCode() >= 200 && reqLog.getStatusCode() < 300) {
                    responseTimes.add(reqLog.getResponseTime());
                    successCount++;
                    liveSuccessCount.incrementAndGet();
                } else {
                    failureCount++;
                    liveFailureCount.incrementAndGet();
                }
            }
            
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            
            executorService.shutdown();
            
            // Calculate statistics
            if (!responseTimes.isEmpty()) {
                Collections.sort(responseTimes);
                
                double averageTime = responseTimes.stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0);
                
                long minTime = responseTimes.stream()
                        .mapToLong(Long::longValue)
                        .min()
                        .orElse(0);
                
                long maxTime = responseTimes.stream()
                        .mapToLong(Long::longValue)
                        .max()
                        .orElse(0);
                
                long p50 = getPercentile(responseTimes, 50);
                long p95 = getPercentile(responseTimes, 95);
                long p99 = getPercentile(responseTimes, 99);
                
                double requestsPerSecond = (numberOfRequests / (double) totalTime) * 1000;
                
                // Write summary to log file
                writeLogSummary(logWriter, numberOfRequests, successCount, failureCount, 
                               totalTime, averageTime, minTime, maxTime, p50, p95, p99, requestsPerSecond);
                
                log.info("========================================");
                log.info("📊 TEST COMPLETED - SUMMARY");
                log.info("========================================");
                log.info("Log file saved: {}", logFilePath);
                log.info("Total Requests: {}", numberOfRequests);
                log.info("Successful: {} ✓", successCount);
                log.info("Failed: {} ✗", failureCount);
                log.info("========================================");
                
                result.setTotalRequests(numberOfRequests);
                result.setSuccessfulRequests(successCount);
                result.setFailedRequests(failureCount);
                result.setTotalTime(totalTime);
                result.setAverageResponseTime(averageTime);
                result.setMinResponseTime(minTime);
                result.setMaxResponseTime(maxTime);
                result.setP50ResponseTime(p50);
                result.setP95ResponseTime(p95);
                result.setP99ResponseTime(p99);
                result.setRequestsPerSecond(requestsPerSecond);
                result.setStatus("SUCCESS");
                result.setLogFilePath(logFilePath);
                
            } else {
                result.setTotalRequests(numberOfRequests);
                result.setSuccessfulRequests(0);
                result.setFailedRequests(numberOfRequests);
                result.setStatus("FAILED");
                result.setErrorMessage("All requests failed");
                result.setLogFilePath(logFilePath);
                log.error("❌ All requests failed!");
            }
            
        } catch (Exception e) {
            log.error("❌ Load test execution failed", e);
            result.setStatus("ERROR");
            result.setErrorMessage(e.getMessage());
        } finally {
            testRunning.set(false);
        }
        
        return result;
    }
    
    private void writeLogHeader(PrintWriter writer, String url, int totalRequests, int threads, String payload) {
        writer.println("================================================================================");
        writer.println("                       API PERFORMANCE TEST LOG                                 ");
        writer.println("================================================================================");
        writer.println();
        writer.println("Test Started: " + LocalDateTime.now().format(formatter));
        writer.println("Target URL: " + url);
        writer.println("Total Requests: " + totalRequests);
        writer.println("Concurrent Threads: " + threads);
        writer.println("Request Payload: " + payload);
        writer.println();
        writer.println("================================================================================");
        writer.println("                              REQUEST LOGS                                      ");
        writer.println("================================================================================");
        writer.println();
    }
    
    private void writeRequestLog(PrintWriter writer, RequestLog reqLog) {
        String statusIcon = (reqLog.getStatusCode() >= 200 && reqLog.getStatusCode() < 300) ? "✓ SUCCESS" : "✗ FAILED";
        
        writer.println("--------------------------------------------------------------------------------");
        writer.println("REQUEST #" + reqLog.getRequestNumber() + " | " + statusIcon + " | " + reqLog.getStatusCode() + " " + reqLog.getStatusText());
        writer.println("--------------------------------------------------------------------------------");
        writer.println("Timestamp:     " + reqLog.getTimestamp());
        writer.println("Method:        " + reqLog.getMethod());
        writer.println("URL:           " + reqLog.getUrl());
        writer.println("Response Time: " + reqLog.getResponseTime() + " ms");
        writer.println();
        writer.println("REQUEST BODY:");
        writer.println(reqLog.getRequestBody());
        writer.println();
        writer.println("RESPONSE BODY:");
        if (reqLog.getResponseBody() != null) {
            writer.println(reqLog.getResponseBody());
        } else if (reqLog.getError() != null) {
            writer.println("ERROR: " + reqLog.getError());
        } else {
            writer.println("(empty)");
        }
        writer.println();
    }
    
    private void writeLogSummary(PrintWriter writer, int total, int success, int failed,
                                  long totalTime, double avgTime, long minTime, long maxTime,
                                  long p50, long p95, long p99, double rps) {
        writer.println();
        writer.println("================================================================================");
        writer.println("                              TEST SUMMARY                                      ");
        writer.println("================================================================================");
        writer.println();
        writer.println("Test Completed: " + LocalDateTime.now().format(formatter));
        writer.println();
        writer.println("RESULTS:");
        writer.println("  Total Requests:      " + total);
        writer.println("  Successful:          " + success + " ✓");
        writer.println("  Failed:              " + failed + " ✗");
        writer.println("  Success Rate:        " + String.format("%.2f", (success * 100.0 / total)) + "%");
        writer.println();
        writer.println("TIMING:");
        writer.println("  Total Duration:      " + totalTime + " ms");
        writer.println("  Average Response:    " + String.format("%.2f", avgTime) + " ms");
        writer.println("  Min Response:        " + minTime + " ms");
        writer.println("  Max Response:        " + maxTime + " ms");
        writer.println("  P50 (Median):        " + p50 + " ms");
        writer.println("  P95:                 " + p95 + " ms");
        writer.println("  P99:                 " + p99 + " ms");
        writer.println();
        writer.println("THROUGHPUT:");
        writer.println("  Requests/Second:     " + String.format("%.2f", rps));
        writer.println();
        writer.println("================================================================================");
        writer.println("                           END OF LOG                                           ");
        writer.println("================================================================================");
    }
    
    private RequestLog sendRequestWithLogging(String url, LoginRequest payload, String jsonPayload, int requestNumber) {
        RequestLog reqLog = new RequestLog();
        reqLog.setRequestNumber(requestNumber);
        reqLog.setTimestamp(LocalDateTime.now().format(formatter));
        reqLog.setMethod("POST");
        reqLog.setUrl(url);
        reqLog.setRequestBody(jsonPayload);
        
        HttpPost httpPost = new HttpPost(url);
        
        try {
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Accept", "application/json");
            httpPost.setEntity(new StringEntity(jsonPayload, StandardCharsets.UTF_8));
            
            long startTime = System.currentTimeMillis();
            
            try (CloseableHttpResponse response = sharedHttpClient.execute(httpPost)) {
                long endTime = System.currentTimeMillis();
                long responseTime = endTime - startTime;
                
                int statusCode = response.getStatusLine().getStatusCode();
                String statusText = response.getStatusLine().getReasonPhrase();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                reqLog.setStatusCode(statusCode);
                reqLog.setStatusText(statusText);
                reqLog.setResponseTime(responseTime);
                reqLog.setResponseBody(responseBody);
                
                String statusIcon = (statusCode >= 200 && statusCode < 300) ? "✓" : "✗";
                log.info("Request #{} {} [{} {}] - {}ms", requestNumber, statusIcon, statusCode, statusText, responseTime);
            }
        } catch (Exception e) {
            reqLog.setStatusCode(0);
            reqLog.setStatusText("ERROR");
            reqLog.setResponseTime(0);
            reqLog.setError(e.getMessage());
            log.error("Request #{} ✗ ERROR - {}", requestNumber, e.getMessage());
        } finally {
            httpPost.releaseConnection();
        }
        
        return reqLog;
    }
    
    private long getPercentile(List<Long> sortedList, double percentile) {
        if (sortedList.isEmpty()) return 0;
        int index = (int) Math.ceil((percentile / 100) * sortedList.size()) - 1;
        index = Math.max(0, Math.min(index, sortedList.size() - 1));
        return sortedList.get(index);
    }
}
