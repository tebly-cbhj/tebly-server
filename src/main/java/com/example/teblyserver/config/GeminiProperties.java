package com.example.teblyserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gemini.api")
public class GeminiProperties {

    private String key;
    private String url;
    private int timeout;
}
