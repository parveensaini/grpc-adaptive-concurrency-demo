package org.example.metrics;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;

import java.io.IOException;

/**
 * Minimal Prometheus metrics for gRPC + executor saturation:
 *
 * gRPC:
 *  - latency histogram
 *  - request count
 *  - errors by status
 *  - inflight gauge
 *
 * Executor (HelloService):
 *  - queue depth
 *  - active threads
 *  - pool size
 *  - rejected tasks
 *
 * Config (for demo clarity):
 *  - work_ms
 *  - workers
 *  - queue_capacity
 */
public final class Metrics {

    // ---------------------------
    // gRPC metrics
    // ---------------------------

    public static final Histogram GRPC_LATENCY_MS = Histogram.builder()
            .name("grpc_server_latency_ms")
            .help("gRPC server latency in milliseconds")
            .labelNames("service", "method", "status")
            .classicOnly()
            .classicUpperBounds(1, 2, 5, 10, 25, 50, 100, 200, 500, 1000, 2000)
            .register();

    public static final Counter GRPC_REQUESTS = Counter.builder()
            .name("grpc_server_requests_total")
            .help("Total gRPC requests")
            .labelNames("service", "method")
            .register();

    public static final Counter GRPC_ERRORS = Counter.builder()
            .name("grpc_server_errors_total")
            .help("Total gRPC errors by status")
            .labelNames("service", "method", "status")
            .register();

    public static final Gauge GRPC_INFLIGHT = Gauge.builder()
            .name("grpc_server_inflight")
            .help("In-flight gRPC requests")
            .labelNames("service", "method")
            .register();

    // ---------------------------
    // HelloService executor metrics
    // ---------------------------

    public static final Gauge HELLO_EXEC_QUEUE = Gauge.builder()
            .name("hello_exec_queue_depth")
            .help("HelloService executor queue depth")
            .register();

    public static final Gauge HELLO_EXEC_ACTIVE = Gauge.builder()
            .name("hello_exec_active_threads")
            .help("HelloService executor active threads")
            .register();

    public static final Gauge HELLO_EXEC_POOL = Gauge.builder()
            .name("hello_exec_pool_size")
            .help("HelloService executor pool size")
            .register();

    public static final Counter HELLO_EXEC_REJECTED_TOTAL = Counter.builder()
            .name("hello_exec_rejected_total")
            .help("HelloService executor task rejections (queue full)")
            .register();

    // ---------------------------
    // Demo configuration gauges
    // (makes screenshots self-describing)
    // ---------------------------

    public static final Gauge HELLO_WORK_MS = Gauge.builder()
            .name("hello_work_ms")
            .help("Simulated work per request (ms)")
            .register();

    public static final Gauge HELLO_WORKERS = Gauge.builder()
            .name("hello_workers")
            .help("Configured executor worker threads")
            .register();

    public static final Gauge HELLO_QUEUE_CAPACITY = Gauge.builder()
            .name("hello_queue_capacity")
            .help("Configured executor queue capacity")
            .register();

    // drl metrics

    public static final Histogram DRL_LATENCY_MS = Histogram.builder()
            .name("drl_histogram")
            .help("drl histogram ms")
            .labelNames("id")
            .classicOnly()
            .classicUpperBounds(1, 2, 5, 10, 25, 50, 100, 200, 500, 1000, 2000)
            .register();


    public static final Gauge DRL_VEGAS_LIMIT = Gauge.builder()
            .name("drl_vegas_limit")
            .labelNames("vegas_limit")
            .help("drl vegas limit")
            .register();

    public static final Counter DRL_COUNTERS = Counter.builder()
            .name("drl_counters")
            .help("drl counters")
            .labelNames("id","idname","status","statusvalue")
            .register();

    private Metrics() {}

    /** Starts an HTTP endpoint for Prometheus to scrape: http://localhost:<port>/metrics */
    public static HTTPServer startPrometheusServer(int port) throws IOException {
        return HTTPServer.builder().port(port).buildAndStart();
    }
}
