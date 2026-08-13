package com.shukla.gavel.notification.infrastructure;

import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.GeneralSecurityException;
import java.security.Security;

@Configuration
public class WebPushConfiguration {

    static {
        // Must run once before any PushService/keypair code executes — the library's EC
        // key handling and JWT signing depend on the BC provider being registered.
        Security.addProvider(new BouncyCastleProvider());
    }

    @Bean
    public PushService pushService(
            @Value("${gavel.notification.vapid.public-key}") final String publicKey,
            @Value("${gavel.notification.vapid.private-key}") final String privateKey,
            @Value("${gavel.notification.vapid.subject}") final String subject) throws GeneralSecurityException {
        return new PushService(publicKey, privateKey, subject);
    }
}
