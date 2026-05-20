package com.shukla.gavel.auction.infrastructure;

import org.springframework.boot.tomcat.TomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Forces Tomcat to use the NIO2 (IOCP-based) connector on all platforms.
 *
 * <p>JDK 21's {@code WEPollSelectorImpl} opens a Unix Domain Socket loopback for its
 * internal pipe. On Windows 11 Enterprise (build 26100) this returns {@code WSAEINVAL},
 * and the TCP fallback inside {@code PipeImpl} sits in an {@code else} branch rather
 * than a catch block, so it is never reached.
 *
 * <p>NIO2 bypasses {@code Selector.open()} entirely: it uses Windows IOCP on Windows
 * and epoll on Linux, so there is no regression in container deployments.
 */
@Configuration
class TomcatNio2Config {

    /**
     * Registers a customizer that sets the HTTP/1.1 NIO2 protocol handler on the
     * embedded Tomcat factory before the server is started.
     *
     * @return a {@link WebServerFactoryCustomizer} targeting {@link TomcatWebServerFactory}
     */
    @Bean
    WebServerFactoryCustomizer<TomcatWebServerFactory> forceNio2() {
        return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }
}
