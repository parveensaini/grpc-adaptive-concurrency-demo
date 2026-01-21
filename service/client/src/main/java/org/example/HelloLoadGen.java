package org.example;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class HelloLoadGen {

    public static void main(String[] args) throws Exception {
        // ---- config (env overridable) ----
        String target = getenv("TARGET", "localhost:50051");

        int steadyRps = getenvInt("STEADY_RPS", 200);
        int burstRps  = getenvInt("BURST_RPS", 2000);

        int steadySec   = getenvInt("STEADY_SEC", 60);
        int burstSec    = getenvInt("BURST_SEC", 90);
        int recoverySec = getenvInt("RECOVERY_SEC", 60);

        int deadlineMs  = getenvInt("DEADLINE_MS", 300);

        int maxInflight = getenvInt("MAX_INFLIGHT", 200);
        int channelsN   = getenvInt("CHANNELS", 4);

        int failEvery   = getenvInt("FAIL_EVERY", 50); // 0 disables

        // ---- build channels + stubs ----
        List<ManagedChannel> channels = new ArrayList<>(channelsN);
        List<HelloServiceGrpc.HelloServiceStub> stubs = new ArrayList<>(channelsN);

        for (int c = 0; c < channelsN; c++) {
            ManagedChannel ch = NettyChannelBuilder.forTarget(target)
                    .usePlaintext()
                    .build();
            channels.add(ch);
            stubs.add(HelloServiceGrpc.newStub(ch));
        }

        // ---- concurrency guard ----
        Semaphore permits = new Semaphore(maxInflight);

        // ---- stats ----
        AtomicLong sent = new AtomicLong();
        AtomicLong completed = new AtomicLong();
        AtomicLong errors = new AtomicLong();
        AtomicLong rejectedByInflightCap = new AtomicLong();

        // ---- scheduler ----
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setName("loadgen-scheduler");
            t.setDaemon(true);
            return t;
        });

        // ---- callback executor (avoid doing callback work on netty threads) ----
        Executor callbackExecutor = Executors.newFixedThreadPool(
                getenvInt("CALLBACK_WORKERS", 16),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("loadgen-callback");
                    t.setDaemon(true);
                    return t;
                }
        );

        // ---- periodic stats print ----
        scheduler.scheduleAtFixedRate(() -> {
            long s = sent.get();
            long c = completed.get();
            long e = errors.get();
            long r = rejectedByInflightCap.get();
            int inflightNow = maxInflight - permits.availablePermits();
            System.out.printf(
                    "sent=%d completed=%d errors=%d inflight=%d rejectedByCap=%d%n",
                    s, c, e, inflightNow, r
            );
        }, 5, 5, TimeUnit.SECONDS);

        // ---- phase runner ----
        runPhase("steady", steadyRps, steadySec, stubs, permits, sent, completed, errors, rejectedByInflightCap,
                failEvery, deadlineMs, scheduler, callbackExecutor);

        runPhase("burst", burstRps, burstSec, stubs, permits, sent, completed, errors, rejectedByInflightCap,
                failEvery, deadlineMs, scheduler, callbackExecutor);

        runPhase("recovery", steadyRps, recoverySec, stubs, permits, sent, completed, errors, rejectedByInflightCap,
                failEvery, deadlineMs, scheduler, callbackExecutor);

        // keep process alive after phases (useful if you want to keep steady traffic running)
        boolean loop = Boolean.parseBoolean(getenv("LOOP", "true"));
        if (loop) {
            while (true) {
                runPhase("steady(loop)", steadyRps, steadySec, stubs, permits, sent, completed, errors, rejectedByInflightCap,
                        failEvery, deadlineMs, scheduler, callbackExecutor);
            }
        } else {
            // shutdown
            scheduler.shutdownNow();
            shutdownChannels(channels);
        }
    }

    private static void runPhase(
            String name,
            int rps,
            int seconds,
            List<HelloServiceGrpc.HelloServiceStub> stubs,
            Semaphore permits,
            AtomicLong sent,
            AtomicLong completed,
            AtomicLong errors,
            AtomicLong rejectedByInflightCap,
            int failEvery,
            int deadlineMs,
            ScheduledExecutorService scheduler,
            Executor callbackExecutor
    ) throws InterruptedException {

        System.out.printf("== phase=%s rps=%d seconds=%d deadlineMs=%d maxInflight=%d channels=%d ==%n",
                name, rps, seconds, deadlineMs, (permits.availablePermits() + (permits.availablePermits() == 0 ? 0 : 0)), stubs.size());

        if (rps <= 0 || seconds <= 0) return;

        // schedule at a fixed period. For high RPS, we use microsecond period where possible.
        long periodNanos = (long) (1_000_000_000.0 / rps);
        long endAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);

        AtomicLong rr = new AtomicLong(0);

        // We schedule a tight ticker; each tick attempts to send 1 request.
        ScheduledFuture<?> ticker = scheduler.scheduleAtFixedRate(() -> {
            if (System.nanoTime() >= endAt) return;

            // cap inflight (critical)
            if (!permits.tryAcquire()) {
                rejectedByInflightCap.incrementAndGet();
                return;
            }

            long seq = sent.incrementAndGet();
            String who = (failEvery > 0 && seq % failEvery == 0) ? "fail" : "world";

            HelloRequest req = HelloRequest.newBuilder().setName(who).build();

            // round-robin across channels/stubs
            int idx = (int) (rr.getAndIncrement() % stubs.size());
            HelloServiceGrpc.HelloServiceStub stub = stubs.get(idx).withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);

            stub.sayHello(req, new StreamObserver<>() {
                @Override
                public void onNext(HelloReply value) {
                    // ignore
                }

                @Override
                public void onError(Throwable t) {
                    // don’t block netty thread; count on callback executor
                    callbackExecutor.execute(() -> {
                        errors.incrementAndGet();
                        completed.incrementAndGet();
                        permits.release();
                    });
                }

                @Override
                public void onCompleted() {
                    callbackExecutor.execute(() -> {
                        completed.incrementAndGet();
                        permits.release();
                    });
                }
            });

        }, 0, Math.max(1, periodNanos), TimeUnit.NANOSECONDS);

        // wait for phase duration
        Thread.sleep(seconds * 1000L);

        // stop ticker
        ticker.cancel(false);

        // allow some drain time (queue + inflight)
        Thread.sleep(1000L);

        System.out.printf("== phase done: %s ==%n", name);
    }

    private static void shutdownChannels(List<ManagedChannel> channels) {
        for (ManagedChannel ch : channels) {
            try {
                ch.shutdown().awaitTermination(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                ch.shutdownNow();
            }
        }
    }

    private static String getenv(String k, String d) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? d : v;
    }

    private static int getenvInt(String k, int d) {
        String v = System.getenv(k);
        if (v == null || v.isBlank()) return d;
        return Integer.parseInt(v.trim());
    }
}