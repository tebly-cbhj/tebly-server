package com.example.teblyserver.schedule.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.schedule.client.ClovaOcrClient;
import com.example.teblyserver.schedule.client.dto.ClovaOcrApiResponse;
import com.example.teblyserver.schedule.domain.Schedule;
import com.example.teblyserver.schedule.dto.OcrScheduleItem;
import com.example.teblyserver.schedule.dto.ScheduleOcrResponse;
import com.example.teblyserver.schedule.dto.request.ScheduleOcrConfirmRequest;
import com.example.teblyserver.schedule.dto.response.ScheduleOcrConfirmResponse;
import com.example.teblyserver.schedule.repository.ScheduleRepository;
import com.example.teblyserver.schedule.util.ImageBlockDetector;
import com.example.teblyserver.schedule.util.ImageBlockDetector.DetectedBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleOcrService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png"
    );

    // 한국어 요일 → 영문 변환 (긴 것부터 먼저 매칭)
    private static final Map<String, String> DAY_MAP = new LinkedHashMap<>() {{
        put("월요일", "MON"); put("화요일", "TUE"); put("수요일", "WED");
        put("목요일", "THU"); put("금요일", "FRI"); put("토요일", "SAT"); put("일요일", "SUN");
        put("월", "MON"); put("화", "TUE"); put("수", "WED");
        put("목", "THU"); put("금", "FRI"); put("토", "SAT"); put("일", "SUN");
        put("MON", "MON"); put("TUE", "TUE"); put("WED", "WED");
        put("THU", "THU"); put("FRI", "FRI"); put("SAT", "SAT"); put("SUN", "SUN");
    }};

    private final ClovaOcrClient clovaOcrClient;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;

    // ── 공개 API ─────────────────────────────────────────────────────────────

    /**
     * 시각표 이미지에서 수업 일정을 추출합니다.
     *
     * <ol>
     *   <li>validateImage()</li>
     *   <li>MultipartFile → BufferedImage 변환</li>
     *   <li>ImageBlockDetector.detect() → List&lt;DetectedBlock&gt;</li>
     *   <li>readImageBytes() → ClovaOcrClient.requestOcr() → ClovaOcrApiResponse</li>
     *   <li>extractRawText() (유지)</li>
     *   <li>matchBlocksWithOcr() → List&lt;OcrScheduleItem&gt;</li>
     * </ol>
     */
    public ScheduleOcrResponse extractSchedules(MultipartFile image) {
        // 1. 검증
        validateImage(image);

        // 2. BufferedImage 변환 (픽셀 분석용)
        BufferedImage bufferedImage = toBufferedImage(image);

        // 3. OCR 호출 — ImageBlockDetector의 1·2단계(시간/컬럼)가 fields를 필요로 하므로 먼저 실행
        byte[] imageBytes = readImageBytes(image);
        String format     = resolveFormat(image.getContentType());
        log.info("OCR 요청 시작: filename={}, size={}bytes, format={}",
                image.getOriginalFilename(), imageBytes.length, format);

        ClovaOcrApiResponse ocrResponse = clovaOcrClient.requestOcr(imageBytes, format);

        // 4. rawText 추출 (기존 유지 — 디버깅/Swagger 응답용)
        String rawText = extractRawText(ocrResponse);
        log.info("OCR 원본 텍스트 추출 완료: {}자", rawText.length());
        log.debug("OCR rawText:\n{}", rawText);

        // 5. OCR fields 추출 후 픽셀+OCR 혼합 분석 → 수업 블록 감지
        List<ClovaOcrApiResponse.Field> fields = getFields(ocrResponse);
        List<ClovaOcrApiResponse.Field> safeFields = fields != null ? fields : List.of();
        List<DetectedBlock> blocks = ImageBlockDetector.detect(bufferedImage, safeFields);
        log.info("픽셀+OCR 혼합 블록 감지 완료: {}개", blocks.size());

        double pixelsPerHour = ImageBlockDetector.computePixelsPerHour(safeFields, bufferedImage.getWidth());

        // 6. 블록 + OCR Field 매핑 → OcrScheduleItem 생성
        List<OcrScheduleItem> schedules = matchBlocksWithOcr(blocks, ocrResponse, pixelsPerHour);
        log.info("블록-OCR 매핑 완료: {}건", schedules.size());

        return ScheduleOcrResponse.builder()
                .rawText(rawText)
                .schedules(schedules)
                .build();
    }

    /**
     * OCR로 추출된 일정 목록을 확정 저장합니다.
     *
     * @param userId  인증된 사용자 ID
     * @param request 확정할 일정 목록
     * @return 저장된 일정 수
     */
    @Transactional
    public ScheduleOcrConfirmResponse confirmSchedules(Long userId, ScheduleOcrConfirmRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<Schedule> schedules = request.getSchedules().stream()
                .map(item -> Schedule.create(
                        user,
                        item.getTitle(),
                        item.getStartTime(),
                        item.getEndTime(),
                        item.getRepeatType()))
                .collect(Collectors.toList());

        List<Schedule> saved = scheduleRepository.saveAll(schedules);
        log.info("OCR 일정 확정 저장 완료: userId={}, savedCount={}", userId, saved.size());

        return ScheduleOcrConfirmResponse.builder()
                .savedCount(saved.size())
                .build();
    }

    // ── validation ──────────────────────────────────────────────────────────

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new CustomException(ErrorCode.OCR_EMPTY_FILE);
        }
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            log.warn("지원하지 않는 파일 형식 요청: {}", contentType);
            throw new CustomException(ErrorCode.OCR_UNSUPPORTED_FORMAT);
        }
    }

    private byte[] readImageBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            log.error("이미지 파일 읽기 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private BufferedImage toBufferedImage(MultipartFile image) {
        try {
            byte[] bytes = image.getBytes();
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bi == null) {
                log.warn("ImageIO.read() 결과 null — 지원하지 않는 이미지 포맷일 수 있음");
                throw new CustomException(ErrorCode.OCR_UNSUPPORTED_FORMAT);
            }
            return bi;
        } catch (CustomException ce) {
            throw ce;
        } catch (IOException e) {
            log.error("BufferedImage 변환 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String resolveFormat(String contentType) {
        if (contentType == null) return "jpg";
        return switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            default -> "jpg";
        };
    }

    // ── rawText 추출 (유지) ──────────────────────────────────────────────────

    private String extractRawText(ClovaOcrApiResponse response) {
        if (response.getImages() == null || response.getImages().isEmpty()) {
            log.warn("OCR 응답에 이미지 결과가 없음");
            return "";
        }
        ClovaOcrApiResponse.ImageResult imageResult = response.getImages().get(0);
        if (!"SUCCESS".equalsIgnoreCase(imageResult.getInferResult())) {
            log.warn("OCR 추론 실패: inferResult={}, message={}",
                    imageResult.getInferResult(), imageResult.getMessage());
            return "";
        }
        if (imageResult.getFields() == null || imageResult.getFields().isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (ClovaOcrApiResponse.Field field : imageResult.getFields()) {
            if (field.getInferText() == null) continue;
            sb.append(field.getInferText());
            sb.append(field.isLineBreak() ? "\n" : " ");
        }
        return sb.toString().trim();
    }

    // ── 블록-OCR 매핑 ───────────────────────────────────────────────────────

    /**
     * 픽셀 감지 블록과 CLOVA OCR Field를 매핑해 {@link OcrScheduleItem} 목록을 생성합니다.
     *
     * <ul>
     *   <li>각 Field의 boundingPoly 중심 좌표가 어느 DetectedBlock 안에 들어오는지 판정</li>
     *   <li>블록에 매핑된 Field를 y좌표 오름차순 정렬 후 첫 번째 y 그룹만 title로 채택</li>
     *   <li>매핑된 Field가 없는 블록은 "기타" 처리</li>
     *   <li>파싱 실패 시 빈 리스트 반환</li>
     * </ul>
     */
    private List<OcrScheduleItem> matchBlocksWithOcr(
            List<DetectedBlock> blocks,
            ClovaOcrApiResponse ocrResponse,
            double pixelsPerHour) {

        List<OcrScheduleItem> result = new ArrayList<>();
        try {
            if (blocks.isEmpty()) return result;

            List<ClovaOcrApiResponse.Field> fields = getFields(ocrResponse);
            if (fields == null || fields.isEmpty()) return result;

            // 블록별 매핑된 Field 수집
            Map<DetectedBlock, List<ClovaOcrApiResponse.Field>> blockFieldMap = new LinkedHashMap<>();
            for (DetectedBlock block : blocks) {
                blockFieldMap.put(block, new ArrayList<>());
            }

            for (ClovaOcrApiResponse.Field field : fields) {
                if (field.getInferText() == null || field.getInferText().isBlank()) continue;
                if (!hasBoundingPoly(field)) continue;

                double cx = fieldCenterX(field);
                double cy = fieldCenterY(field);

                for (DetectedBlock block : blocks) {
                    if (cx >= block.getXStart() && cx <= block.getXEnd()
                            && cy >= block.getYStart() && cy <= block.getYEnd()) {
                        blockFieldMap.get(block).add(field);
                        break; // 한 Field는 하나의 블록에만 매핑
                    }
                }
            }

            // 블록별 OcrScheduleItem 생성
            for (Map.Entry<DetectedBlock, List<ClovaOcrApiResponse.Field>> entry : blockFieldMap.entrySet()) {
                DetectedBlock block = entry.getKey();
                List<ClovaOcrApiResponse.Field> blockFields = entry.getValue();

                String title = buildTitleByFirstGroup(blockFields, pixelsPerHour);
                if (title.isBlank()) {
                    title = "기타";
                    log.debug("텍스트 미매핑 블록 → 기타: {} {}~{}",
                            block.getDayOfWeek(), block.getStartTime(), block.getEndTime());
                }

                result.add(OcrScheduleItem.builder()
                        .dayOfWeek(resolveDay(block.getDayOfWeek()))
                        .startTime(normalizeTime(block.getStartTime()))
                        .endTime(normalizeTime(block.getEndTime()))
                        .title(title)
                        .build());

                String mappedTexts = blockFields.stream()
                        .map(f -> f.getInferText().trim())
                        .collect(Collectors.joining("|"));
                log.info("[OCR-DIAG] 블록→일정: {} {}~{} title='{}' (매핑필드 {}개: {})",
                        block.getDayOfWeek(), block.getStartTime(), block.getEndTime(),
                        title, blockFields.size(), mappedTexts);
            }

        } catch (Exception e) {
            log.warn("블록-OCR 매핑 중 오류 발생, 빈 리스트 반환: {}", e.getMessage());
        }
        return result;
    }

    // ── title 조합 ───────────────────────────────────────────────────────────

    /**
     * 블록에 매핑된 Field 중 y좌표 기준 첫 번째 그룹만 이어붙여 title을 반환합니다.
     *
     * <ol>
     *   <li>Field를 y좌표 오름차순 정렬</li>
     *   <li>첫 번째 Field의 y ± (pixelsPerHour * 0.3) 이내인 Field만 채택</li>
     *   <li>공백 없이 이어붙임 (예: "브랜드스토리" + "텔링" → "브랜드스토리텔링")</li>
     *   <li>pixelsPerHour를 알 수 없을 때(≤0)는 전체 Field를 이어붙임</li>
     * </ol>
     */
    private String buildTitleByFirstGroup(
            List<ClovaOcrApiResponse.Field> fields, double pixelsPerHour) {
        if (fields.isEmpty()) return "";

        if (pixelsPerHour <= 0) {
            return fields.stream()
                    .map(f -> f.getInferText().trim())
                    .collect(Collectors.joining());
        }

        List<ClovaOcrApiResponse.Field> sorted = fields.stream()
                .sorted(Comparator.comparingDouble(this::fieldCenterY))
                .collect(Collectors.toList());

        ClovaOcrApiResponse.Field firstField = sorted.get(0);
        double firstY     = fieldCenterY(firstField);
        double lineHeight = fieldHeight(firstField);
        double threshold  = lineHeight > 2 ? lineHeight * 1.5 : pixelsPerHour * 0.15;

        return sorted.stream()
                .filter(f -> Math.abs(fieldCenterY(f) - firstY) <= threshold)
                .map(f -> f.getInferText().trim())
                .collect(Collectors.joining());
    }

    // ── OCR 필드 추출 헬퍼 ──────────────────────────────────────────────────

    private List<ClovaOcrApiResponse.Field> getFields(ClovaOcrApiResponse response) {
        if (response.getImages() == null || response.getImages().isEmpty()) return null;
        ClovaOcrApiResponse.ImageResult img = response.getImages().get(0);
        if (!"SUCCESS".equalsIgnoreCase(img.getInferResult())) return null;
        return img.getFields();
    }

    private boolean hasBoundingPoly(ClovaOcrApiResponse.Field f) {
        return f.getBoundingPoly() != null
                && f.getBoundingPoly().getVertices() != null
                && !f.getBoundingPoly().getVertices().isEmpty();
    }

    private double fieldCenterX(ClovaOcrApiResponse.Field f) {
        return f.getBoundingPoly().getVertices().stream()
                .mapToDouble(ClovaOcrApiResponse.Vertex::getX).average().orElse(0);
    }

    private double fieldCenterY(ClovaOcrApiResponse.Field f) {
        return f.getBoundingPoly().getVertices().stream()
                .mapToDouble(ClovaOcrApiResponse.Vertex::getY).average().orElse(0);
    }

    private double fieldHeight(ClovaOcrApiResponse.Field f) {
        if (!hasBoundingPoly(f)) return 0;
        double maxY = f.getBoundingPoly().getVertices().stream()
                .mapToDouble(ClovaOcrApiResponse.Vertex::getY).max().orElse(0);
        double minY = f.getBoundingPoly().getVertices().stream()
                .mapToDouble(ClovaOcrApiResponse.Vertex::getY).min().orElse(0);
        return maxY - minY;
    }

    // ── 기존 유틸 유지 ───────────────────────────────────────────────────────

    private String resolveDay(String raw) {
        for (Map.Entry<String, String> entry : DAY_MAP.entrySet()) {
            if (raw.startsWith(entry.getKey())) return entry.getValue();
        }
        return raw.toUpperCase();
    }

    /** "9:00" → "09:00" 형식 정규화 */
    private String normalizeTime(String time) {
        if (time != null && time.length() == 4 && time.charAt(1) == ':') {
            return "0" + time;
        }
        return time;
    }
}
