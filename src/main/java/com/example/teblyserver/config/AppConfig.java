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
     * decision 패키지(PromptBuilder/GeminiClient/DecisionHelperService)가 쓰는 Jackson 2 ObjectMapper.
     * Spring Boot 4의 MVC(@RequestBody/@ResponseBody)는 Jackson 3(tools.jackson)를 쓰기 때문에,
     * 이 Jackson 2(com.fasterxml.jackson) 타입 빈은 MVC와 완전히 별개이며 서로 간섭하지 않는다.
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
