package org.example;

import io.grpc.*;
import org.example.metrics.Metrics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GrpcMetricsInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        final long startNanos = System.nanoTime();

        final String fullMethod = call.getMethodDescriptor().getFullMethodName();
        final String service = serviceName(fullMethod);
        final String method = methodName(fullMethod);

        Metrics.GRPC_REQUESTS.labelValues(service, method).inc();
        Metrics.GRPC_INFLIGHT.labelValues(service, method).inc();

        // Ensure we decrement inflight only once no matter what path closes the call.
        final AtomicBoolean inflightDecOnce = new AtomicBoolean(false);

        ServerCall<ReqT, RespT> wrapped =
                new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                        String statusCode = status.getCode().name();

                        Metrics.GRPC_LATENCY_MS.labelValues(service, method, statusCode).observe(tookMs);

                        if (!status.isOk()) {
                            Metrics.GRPC_ERRORS.labelValues(service, method, statusCode).inc();
                        }

                        if (inflightDecOnce.compareAndSet(false, true)) {
                            Metrics.GRPC_INFLIGHT.labelValues(service, method).dec();
                        }

                        super.close(status, trailers);
                    }
                };

        // Optional: also wrap listener to handle cancellation signal explicitly (nice for debugging)
        ServerCall.Listener<ReqT> listener = next.startCall(wrapped, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onCancel() {
                // onCancel typically leads to close() too, but onCancel helps confirm behavior during deadlines.
                super.onCancel();
            }
        };
    }

    private static String serviceName(String fullMethod) {
        int slash = fullMethod.indexOf('/');
        return (slash > 0) ? fullMethod.substring(0, slash) : "unknown";
    }

    private static String methodName(String fullMethod) {
        int slash = fullMethod.indexOf('/');
        return (slash > 0 && slash + 1 < fullMethod.length()) ? fullMethod.substring(slash + 1) : "unknown";
    }
}
