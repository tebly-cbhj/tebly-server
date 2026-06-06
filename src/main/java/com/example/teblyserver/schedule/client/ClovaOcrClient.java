package com.example.teblyserver.schedule.client;

import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.config.ClovaOcrProperties;
import com.example.teblyserver.schedule.client.dto.ClovaOcrApiResponse;
import com.example.teblyserver.schedule.client.dto.ClovaOcrRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClovaOcrClient {

    private final RestTemplate restTemplate;
    private final ClovaOcrProperties clovaOcrProperties;

    /**
     * CLOVA OCR API를 호출하여 응답을 반환합니다.
     *
     * @param imageBytes 이미지 바이트 배열
     * @param format     파일 형식 (jpg | png)
     * @return CLOVA OCR API 응답
     */
    public ClovaOcrApiResponse requestOcr(byte[] imageBytes, String format) {
        ClovaOcrRequest request = buildRequest(imageBytes, format);
        HttpEntity<ClovaOcrRequest> httpEntity = buildHttpEntity(request);

        log.debug("CLOVA OCR API 호출: url={}", clovaOcrProperties.getApiUrl());

        try {
            ResponseEntity<ClovaOcrApiResponse> response = restTemplate.exchange(
                    clovaOcrProperties.getApiUrl(),
                    HttpMethod.POST,
                    httpEntity,
                    ClovaOcrApiResponse.class
            );

            if (response.getBody() == null) {
                log.error("CLOVA OCR API 응답 바디가 null");
                throw new CustomException(ErrorCode.OCR_API_CALL_FAILED);
            }

            return response.getBody();

        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("CLOVA OCR API RestClient 오류: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.OCR_API_CALL_FAILED);
        } catch (Exception e) {
            log.error("CLOVA OCR API 알 수 없는 오류: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.OCR_API_CALL_FAILED);
        }
    }

    private ClovaOcrRequest buildRequest(byte[] imageBytes, String format) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        return ClovaOcrRequest.builder()
                .version("V2")
                .requestId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .images(List.of(
                        ClovaOcrRequest.ImageData.builder()
                                .format(format)
                                .name("image")
                                .data(base64Image)
                                .build()
                ))
                .build();
    }

    private HttpEntity<ClovaOcrRequest> buildHttpEntity(ClovaOcrRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-OCR-SECRET", clovaOcrProperties.getSecretKey());
        return new HttpEntity<>(request, headers);
    }
}
