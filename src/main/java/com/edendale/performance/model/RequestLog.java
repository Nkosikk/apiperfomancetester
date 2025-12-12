package com.edendale.performance.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestLog {
    private int requestNumber;
    private String timestamp;
    private String method;
    private String url;
    private String requestBody;
    private int statusCode;
    private String statusText;
    private long responseTime;
    private String responseBody;
    private String error;
}
