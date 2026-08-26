package com.hackaton.papasud.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GroqClientConfig {

    @Bean(name = "groqRestTemplate")
    public RestTemplate groqRestTemplate(
            @Value("${groq.api.timeout-ms:30000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofMillis(Math.max(1_000, timeoutMs)));
        return new RestTemplate(factory);
    }
}
