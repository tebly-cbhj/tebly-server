package com.example.teblyserver.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * LocalDateTime 응답에 KST 오프셋(+09:00)을 붙여서 내려주기 위한 설정.
 *
 * 기존: "2026-07-18T17:30:00"  -> 프론트(iOS)에서 UTC로 해석되어 9시간 어긋남
 * 변경: "2026-07-18T17:30:00+09:00"
 *
 * 직렬화(응답)만 변경하며, 역직렬화(요청)는 기존 동작을 그대로 유지한다.
 */
@Configuration
public class JacksonConfig {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Bean
    public SimpleModule kstLocalDateTimeModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalDateTime.class, new StdSerializer<LocalDateTime>(LocalDateTime.class) {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider)
                    throws IOException {
                gen.writeString(value.atZone(KST).format(FORMATTER));
            }
        });
        return module;
    }
}