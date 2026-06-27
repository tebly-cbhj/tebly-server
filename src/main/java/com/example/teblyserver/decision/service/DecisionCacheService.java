package com.example.teblyserver.decision.service;

import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.decision.dto.DecisionCacheDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 추천 알고리즘 결과를 Redis에 저장/조회한다.
 * Key 형식: "decision:{roomId}", TTL: 30분, 값: DecisionCacheDto의 JSON 문자열.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionCacheService {

    private static final String KEY_PREFIX = "decision:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void saveDecisionCache(Long roomId, DecisionCacheDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(buildKey(roomId), json, TTL);
        } catch (JsonProcessingException e) {
            log.error("결정 캐시 직렬화 실패: roomId={}", roomId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public DecisionCacheDto getDecisionCache(Long roomId) {
        String json = redisTemplate.opsForValue().get(buildKey(roomId));

        if (json == null) {
            throw new CustomException(ErrorCode.DECISION_CACHE_NOT_FOUND);
        }

        try {
            return objectMapper.readValue(json, DecisionCacheDto.class);
        } catch (JsonProcessingException e) {
            log.error("결정 캐시 역직렬화 실패: roomId={}", roomId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildKey(Long roomId) {
        return KEY_PREFIX + roomId;
    }
}
