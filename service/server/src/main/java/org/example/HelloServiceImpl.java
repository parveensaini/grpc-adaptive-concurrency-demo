package org.example;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.example.metrics.Metrics;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {

    // ---- env knobs (keep defaults sane for local demos) ----
    private static final int WORK_MS   = envInt("WORK_MS", 0);              // e.g. 10
    private static final String MODE   = envStr("WORK_MODE", "sleep");      // sleep|cpu
    private static final int WORKERS   = envInt("WORKERS", 8);              // server worker threads
    private static final int QUEUE     = envInt("QUEUE", 50);               // bounded queue size

    // ---- bounded executor: deterministic overload + queueing ----
    // AbortPolicy throws RejectedExecutionException when saturated (queue full).
    private static final ThreadPoolExecutor EXEC = new ThreadPoolExecutor(
            WORKERS,
            WORKERS,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE),
            r -> {
                Thread t = new Thread(r);
                t.setName("hello-worker");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    static {
        Metrics.HELLO_WORK_MS.set(WORK_MS);
        Metrics.HELLO_WORKERS.set(WORKERS);
        Metrics.HELLO_QUEUE_CAPACITY.set(QUEUE);
    }

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        if ("fail".equals(request.getName())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("forced failure")
                    .asRuntimeException());
            return;
        }

        try {
            Metrics.HELLO_EXEC_QUEUE.set(EXEC.getQueue().size());
            Metrics.HELLO_EXEC_ACTIVE.set(EXEC.getActiveCount());
            Metrics.DRL_VEGAS_LIMIT.labelValues("vegas_limit").set(HelloServer.drlInterceptor.vegasLimit.getLimit());

            EXEC.execute(() -> handle(request, responseObserver));

        } catch (RejectedExecutionException rejected) {
            Metrics.HELLO_EXEC_REJECTED_TOTAL.inc();
            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription("server saturated (queue full)")
                    .asRuntimeException());
        }
    }

    private static void handle(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        try {
            // Simulate real work
            doWork(WORK_MS, MODE);

            String msg = "Hello, " + request.getName();
            HelloReply reply = HelloReply.newBuilder()
                    .setMessage(msg)
                    .build();

            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            // Be defensive: ensure stream completes with error if anything goes wrong
            responseObserver.onError(Status.INTERNAL
                    .withDescription("handler error: " + t.getClass().getSimpleName())
                    .withCause(t)
                    .asRuntimeException());
        }
    }

    private static void doWork(int workMs, String mode) {
        if (workMs <= 0) return;

        if ("cpu".equalsIgnoreCase(mode)) {
            long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(workMs);
            long x = 0;
            while (System.nanoTime() < end) {
                x += 1;
            }
            // prevent JIT from completely eliding the loop
            if (x == 42) {
                System.out.print("");
            }
        } else {
            try {
                Thread.sleep(workMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static int envInt(String key, int def) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) return def;
        return Integer.parseInt(v.trim());
    }

    private static String envStr(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}