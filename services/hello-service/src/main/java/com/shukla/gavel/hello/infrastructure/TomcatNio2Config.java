package com.shukla.gavel.hello.infrastructure;

import org.springframework.boot.tomcat.TomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TomcatNio2Config {

    // JDK 21's WEPollSelectorImpl uses Unix Domain Socket loopback for its internal pipe,
    // which returns WSAEINVAL on Windows 11 Enterprise. NIO2 uses Windows IOCP directly
    // and never calls Selector.open(), bypassing the issue entirely.
    // On Linux (Docker/k8s) NIO2 maps to epoll — no regression.
    @Bean
    WebServerFactoryCustomizer<TomcatWebServerFactory> forceNio2() {
        return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }
}
