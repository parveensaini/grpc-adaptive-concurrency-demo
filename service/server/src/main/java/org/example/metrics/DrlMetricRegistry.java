package org.example.metrics;

import com.netflix.concurrency.limits.MetricRegistry;

import java.util.function.Supplier;

public class DrlMetricRegistry implements MetricRegistry {

    public Counter counter(String id, String... tagNameValuePairs) {
        return Metrics.DRL_COUNTERS.labelValues(tagNameValuePairs)::inc;
    }

    public SampleListener registerDistribution(String id, String... tagNameValuePairs) {
        return value -> Metrics.DRL_LATENCY_MS.labelValues(id).observe(value.doubleValue()/1000000);
    }

    public void gauge(String id, Supplier<Number> supplier, String... tagNameValuePairs) {
    };

}
