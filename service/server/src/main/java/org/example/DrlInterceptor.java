package org.example;

import com.netflix.concurrency.limits.grpc.server.ConcurrencyLimitServerInterceptor;
import com.netflix.concurrency.limits.limit.VegasLimit;
import com.netflix.concurrency.limits.limit.WindowedLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import io.grpc.ServerInterceptor;
import org.example.metrics.DrlMetricRegistry;

public class DrlInterceptor {

    VegasLimit vegasLimit;

    ServerInterceptor getInterceptor() {
        DrlMetricRegistry metricRegistry = new DrlMetricRegistry();

        vegasLimit = VegasLimit.newBuilder()
                .metricRegistry(metricRegistry)
                .build();

        ServerInterceptor serverInterceptor = ConcurrencyLimitServerInterceptor.newBuilder(
                SimpleLimiter.newBuilder()
                        .metricRegistry(metricRegistry)
                        .limit(WindowedLimit.newBuilder()
                                .build(vegasLimit))
                        .metricRegistry(metricRegistry)
                        .build()).build();
        return serverInterceptor;
    }
}