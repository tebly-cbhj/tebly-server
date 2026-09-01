package com.example.teblyserver.schedule.controller;

import com.example.teblyserver.common.response.ApiResponse;
import com.example.teblyserver.schedule.dto.ScheduleOcrResponse;
import com.example.teblyserver.schedule.dto.request.ScheduleOcrConfirmRequest;
import com.example.teblyserver.schedule.dto.response.ScheduleOcrConfirmResponse;
import com.example.teblyserver.schedule.service.ScheduleOcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Schedule OCR", description = "이미지 기반 일정 추출 API")
@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleOcrController {

    private final ScheduleOcrService scheduleOcrService;

    @Operation(
            summary = "이미지에서 일정 추출",
            description = "에브리타임·학교 캘린더 캡처 이미지를 업로드하면 CLOVA OCR로 텍스트를 추출하고 구조화된 일정 데이터를 반환합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "일정 추출 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ScheduleOcrResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "파일이 비어있거나 요청이 잘못됨",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "415",
                    description = "지원하지 않는 파일 형식 (jpg/png/jpeg만 허용)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "CLOVA OCR API 호출 실패",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
    })
    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ScheduleOcrResponse>> extractSchedule(
            @Parameter(description = "일정 캡처 이미지 (jpg, png, jpeg만 허용, 최대 10MB)", required = true)
            @RequestPart("image") MultipartFile image
    ) {
        ScheduleOcrResponse result = scheduleOcrService.extractSchedules(image);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(
            summary = "OCR 추출 일정 확정 저장",
            description = "OCR로 추출된 일정 목록을 사용자가 검토한 뒤 확정 저장합니다. 로그인한 사용자 기준으로 저장됩니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "일정 저장 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ScheduleOcrConfirmResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 데이터가 유효하지 않음 (빈 목록, 필수 필드 누락 등)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
    })
    @PostMapping("/ocr/confirm")
    public ResponseEntity<ApiResponse<ScheduleOcrConfirmResponse>> confirmSchedules(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid ScheduleOcrConfirmRequest request
    ) {
        ScheduleOcrConfirmResponse response = scheduleOcrService.confirmSchedules(userId, request);
        return ResponseEntity.ok(ApiResponse.success("일정이 성공적으로 저장되었습니다.", response));
    }
}
