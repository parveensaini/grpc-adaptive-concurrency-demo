package org.example;

import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import org.example.metrics.Metrics;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class HelloServer {

    public static DrlInterceptor drlInterceptor = new DrlInterceptor();

    public static void main(String[] args) throws Exception {
        int grpcPort = 50051;
        int metricsPort = 9090;

        // Start Prometheus scrape endpoint: http://localhost:9090/metrics
        HTTPServer prom = Metrics.startPrometheusServer(metricsPort);
        System.out.println("Prometheus metrics: http://localhost:" + metricsPort + "/metrics");

        ExecutorService executor =
                new ThreadPoolExecutor(
                        8,                  // fixed workers
                        8,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(50), // <- critical
                        new ThreadPoolExecutor.AbortPolicy()
                );

        HelloServiceImpl service = new HelloServiceImpl();


        Server server = NettyServerBuilder.forPort(grpcPort)
                .addService(ServerInterceptors.intercept(service,/* drlInterceptor.getInterceptor(),*/ new GrpcMetricsInterceptor()))
                //.executor(executor)
                .build()
                .start();

        System.out.println("gRPC server started on port " + grpcPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            server.shutdown();
            prom.stop();
        }));

        server.awaitTermination();
    }
}