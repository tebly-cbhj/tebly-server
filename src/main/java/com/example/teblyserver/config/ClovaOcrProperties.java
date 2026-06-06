package com.example.teblyserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "clova.ocr")
public class ClovaOcrProperties {

    private String apiUrl;
    private String secretKey;
}
