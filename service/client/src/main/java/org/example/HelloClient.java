package org.example;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

public final class HelloClient {

    public static void main(String[] args) {
        String target = "localhost:50051";

        ManagedChannel channel = NettyChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();

        HelloServiceGrpc.HelloServiceBlockingStub stub = HelloServiceGrpc.newBlockingStub(channel);

        HelloReply reply = stub.sayHello(
                HelloRequest.newBuilder().setName("World").build()
        );

        System.out.println("Response: " + reply.getMessage());

        channel.shutdown();
    }
}