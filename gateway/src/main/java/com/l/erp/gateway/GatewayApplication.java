package com.l.erp.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.ServerRequest;

import java.net.InetSocketAddress;
import java.util.function.Function;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

//    @Bean
//    public Function<ServerRequest, String> ipKeyResolver() {
//        return request -> request.remoteAddress()
//                .map(InetSocketAddress::getHostString)
//                .orElse("unknown");
//    }

}
