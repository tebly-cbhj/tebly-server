package com.example.teblyserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({ClovaOcrProperties.class, GeminiProperties.class})
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 애플리케이션 공용 ObjectMapper.
     * LocalDate/LocalDateTime 직렬화를 위해 JavaTimeModule을 등록하고,
     * 날짜를 타임스탬프(숫자)가 아닌 ISO-8601 문자열로 직렬화한다.
     * (record 바인딩 등 클래스패스의 나머지 모듈은 findAndRegisterModules로 함께 등록)
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Bean
    public OkHttpClient okHttpClient(GeminiProperties geminiProperties) {
        Duration timeout = Duration.ofSeconds(geminiProperties.getTimeout());
        return new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();
    }
}
