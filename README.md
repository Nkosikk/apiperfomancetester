# Edendale Performance Tester

A professional performance testing tool for API load testing with a beautiful web interface.

## 🎯 Features

- **Load Testing**: Send multiple concurrent requests to your API
- **Performance Metrics**: 
  - Min/Max/Average response times
  - Percentile metrics (P50, P95, P99)
  - Success/Failure rates
  - Requests per second (RPS)
- **Beautiful UI**: Modern, responsive web interface
- **Preset Configurations**: Quick presets for different load profiles
- **Real-time Visualization**: Chart-based metrics display
- **Concurrent Testing**: Adjustable thread concurrency

## 📋 Prerequisites

- Java 11 or higher
- Maven 3.6 or higher

## 🚀 Getting Started

### 1. Build the Project

```bash
cd "/Users/nkosicele/Documents/Mathe/edendale perfomance"
mvn clean install
```

### 2. Run the Application

```bash
mvn spring-boot:run
```

Or:

```bash
java -jar target/performance-tester-1.0.0.jar
```

The application will start on `http://localhost:8080`

### 3. Access the Web Interface

Open your browser and navigate to:
```
http://localhost:8080
```

## 📊 Using the Performance Tester

### Input Parameters

1. **API Endpoint URL**: The full URL of the API endpoint you want to test
   - Default: `https://www.ndosiautomation.co.za/EDENDALESPORTSPROJECTNPC/api/auth/login`

2. **Request Payload**: The JSON payload to send with each request
   - Default: `{"email":"admin@gmail.com","password":"@Dontforget1"}`

3. **Number of Requests**: Total number of requests to send (1-10,000)
   - Default: 100

4. **Concurrent Threads**: Number of simultaneous requests (1-500)
   - Default: 10

### Preset Configurations

Quick-select presets for common testing scenarios:

| Preset | Requests | Threads | Use Case |
|--------|----------|---------|----------|
| Light | 10 | 5 | Quick validation |
| Medium | 50 | 10 | Standard testing |
| Heavy | 100 | 20 | Load assessment |
| Stress | 200 | 30 | Stress testing |
| Extreme | 500 | 50 | Extreme load |
| Beast | 1000 | 100 | Maximum stress |

### Results Metrics

After running a test, you'll see:

- **✓ Successful Requests**: Number of successful API calls
- **✗ Failed Requests**: Number of failed API calls
- **⏱ Average Response Time**: Mean response time across all requests
- **⬇ Min Response Time**: Fastest response
- **⬆ Max Response Time**: Slowest response
- **⏱ Median (P50)**: 50th percentile response time
- **⏱ P95**: 95th percentile response time (95% of requests faster)
- **⏱ P99**: 99th percentile response time (99% of requests faster)
- **📊 Total Requests**: Count of all requests sent
- **⏱ Total Test Duration**: Total time taken for the test

## 🔧 API Endpoints

### POST /api/performance/test

Run a performance test on an API endpoint.

**Query Parameters:**
- `url`: (required) The API endpoint URL to test
- `numberOfRequests`: (optional, default: 100) Total requests to send
- `concurrentThreads`: (optional, default: 10) Concurrent threads

**Request Body:**
```json
{
  "email": "admin@gmail.com",
  "password": "@Dontforget1"
}
```

**Response:**
```json
{
  "totalRequests": 100,
  "successfulRequests": 98,
  "failedRequests": 2,
  "totalTime": 5234,
  "averageResponseTime": 52.34,
  "minResponseTime": 45,
  "maxResponseTime": 125,
  "p50ResponseTime": 50,
  "p95ResponseTime": 85,
  "p99ResponseTime": 110,
  "requestsPerSecond": 19.1,
  "status": "SUCCESS"
}
```

## 📁 Project Structure

```
edendale perfomance/
├── pom.xml                                  # Maven configuration
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/edendale/performance/
│   │   │       ├── PerformanceTesterApplication.java
│   │   │       ├── controller/
│   │   │       │   └── PerformanceController.java
│   │   │       ├── model/
│   │   │       │   ├── LoginRequest.java
│   │   │       │   └── PerformanceResult.java
│   │   │       └── service/
│   │   │           └── PerformanceTestService.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── static/
│   │           └── index.html
│   └── test/
└── README.md
```

## 🛠 Dependencies

- Spring Boot 2.7.14
- Apache HttpClient 4.5.14
- Gson 2.10.1
- Lombok 1.18.30

## 📝 Example Usage

### Testing Your Edendale API

1. Open `http://localhost:8080` in your browser
2. API URL is pre-filled: `https://www.ndosiautomation.co.za/EDENDALESPORTSPROJECTNPC/api/auth/login`
3. Payload is pre-filled with demo credentials
4. Click "Medium (50req)" preset or customize your settings
5. Click "▶ Run Test"
6. View results and chart visualization

## ⚠️ Important Notes

- The tool sends actual HTTP requests to your API
- Use appropriate credentials for testing
- Be mindful of API rate limits
- Consider the impact on your server when running large loads
- P50, P95, P99 are percentile metrics useful for understanding response time distribution
- RPS (Requests Per Second) indicates throughput

## 🔒 Security Considerations

- This tool sends credentials in the request payload
- Use test credentials, not production credentials
- The tool runs locally and does not store sensitive data
- Ensure your test environment is properly isolated

## 💡 Performance Testing Best Practices

1. **Start Small**: Begin with light load and gradually increase
2. **Monitor**: Watch server resources during tests
3. **Multiple Runs**: Run tests multiple times for consistency
4. **Analyze Percentiles**: Focus on P95/P99, not just averages
5. **Compare**: Run tests before and after optimizations

## 📞 Support

For issues or questions, check:
- Application logs in the console
- Browser console for JavaScript errors
- Network tab in browser dev tools

## 📄 License

This project is for testing purposes.

---

**Created**: December 2025
**Project**: Edendale Performance Testing
