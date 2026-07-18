package com.example.teblyserver.schedule.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.schedule.client.ClovaOcrClient;
import com.example.teblyserver.schedule.client.dto.ClovaOcrApiResponse;
import com.example.teblyserver.schedule.domain.Category;
import com.example.teblyserver.schedule.domain.OcrCategoryType;
import com.example.teblyserver.schedule.domain.RepeatType;
import com.example.teblyserver.schedule.domain.Schedule;
import com.example.teblyserver.schedule.dto.OcrScheduleItem;
import com.example.teblyserver.schedule.dto.ScheduleOcrResponse;
import com.example.teblyserver.schedule.dto.request.ScheduleOcrConfirmRequest;
import com.example.teblyserver.schedule.dto.response.ScheduleOcrConfirmResponse;
import com.example.teblyserver.schedule.repository.CategoryRepository;
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
import java.util.regex.Pattern;
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

    // 알바·근로 추정 키워드 (공백 제거한 블록 텍스트에 하나라도 포함되면 WORK)
    private static final Set<String> WORK_KEYWORDS = Set.of(
            "알바", "근로", "근무", "출근", "교내근로", "교외근로", "조교", "인턴", "TA"
    );

    // 강의실 패턴: 건물명(2~8자) + 선택적 영문 + 호실(3~5자리 숫자)
    // 예) 백마관304, 형설관 B201, 조만식기념관 12317, (공)504
    private static final Pattern LECTURE_ROOM_PATTERN =
            Pattern.compile("[가-힣A-Za-z()]{2,8}\\s?[A-Za-z]?\\d{3,5}");

    // 강의실 코드 한 줄 패턴: "T0101", "C526", "학412", "포B161", "21203" 등
    // (건물 약칭 0~4자 + 호실 숫자 2~5자리로만 이뤄진 줄 → 제목이 아니라 위치 줄)
    private static final Pattern COMPACT_ROOM_CODE_PATTERN =
            Pattern.compile("^[가-힣A-Za-z]{0,4}\\d{2,5}$");

    // 건물명 단독 줄 패턴: "정보과학관"처럼 호실 없이 건물명만 적힌 위치 줄
    private static final Pattern BUILDING_ONLY_PATTERN =
            Pattern.compile("^[가-힣]{2,10}(관|홀|당)$");

    /**
     * 제목 대비 상세줄(교수명·장소) 폰트 높이 비율 임계값.
     * 에브리타임류 시간표는 상세줄을 제목의 ~70% 크기로 렌더링하므로,
     * OCR 박스 높이 노이즈(±10% 내외)를 감안해 0.82 미만이면 상세줄로 판정한다.
     */
    private static final double DETAIL_FONT_RATIO = 0.82;

    // 교수명으로 추정되는 줄 패턴: 2~4자 순수 한글.
    // 단독으로는 제목 이어쓰기 조각("산업", "텔링" 등)과 구분할 수 없으므로,
    // "성씨로 시작" + "바로 다음 줄이 장소 줄"이라는 조건과 반드시 함께 사용한다.
    private static final Pattern PERSON_NAME_PATTERN = Pattern.compile("^[가-힣]{2,4}$");

    // 주요 한국 성씨(단자) — 교수명 줄 판정 시 첫 글자 확인용.
    // "텔링"(제목 조각)은 성씨로 시작하지 않아 걸러지고, "정은혜"는 성씨 시작이라 교수명으로 판정된다.
    // 주의: 초희귀 성씨(어·설·석 등)까지 넣으면 제목 이어쓰기 조각과 충돌한다.
    // 실제로 "프로그래밍언/어론" 의 "어론"이 어씨 교수명으로 오판된 사례가 있어 상위 성씨만 유지한다.
    private static final String KOREAN_SURNAME_CHARS =
            "김이박최정강조윤장임한오서신권황안송류전홍고문양손배백허유남심노하곽성차주우구민진지엄채원천방공현함변염여추";

    private final ClovaOcrClient clovaOcrClient;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final CategoryRepository categoryRepository;

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
                .map(item -> {
                    // 프론트가 최종 선택한 카테고리를 조회 후 소유자 검증 → 그대로 저장
                    Category category = categoryRepository.findById(item.getCategoryId())
                            .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
                    if (!category.getUser().getId().equals(userId)) {
                        throw new CustomException(ErrorCode.CATEGORY_FORBIDDEN);
                    }

                    if (item.getStartTime() == null || item.getEndTime() == null) {
                        throw new CustomException(ErrorCode.INVALID_INPUT);
                    }

                    if (!item.getStartTime().isBefore(item.getEndTime())) {
                        throw new CustomException(ErrorCode.INVALID_SCHEDULE_TIME);
                    }
                    return Schedule.create(
                            user,
                            category,
                            item.getTitle(),
                            item.getStartTime(),
                            item.getEndTime(),
                            item.getRepeatType(),
                            null);
                })
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

                String title = buildTitle(blockFields, pixelsPerHour);
                if (title.isBlank()) {
                    title = "기타";
                    log.debug("텍스트 미매핑 블록 → 기타: {} {}~{}",
                            block.getDayOfWeek(), block.getStartTime(), block.getEndTime());
                }

                // 분류는 강의실·교수명까지 포함한 블록 전체 텍스트로 추정 (title 조립과 별개)
                OcrCategoryType categoryType = classifyBlock(blockFields);

                result.add(OcrScheduleItem.builder()
                        .dayOfWeek(resolveDay(block.getDayOfWeek()))
                        .startTime(normalizeTime(block.getStartTime()))
                        .endTime(normalizeTime(block.getEndTime()))
                        .title(title)
                        .categoryType(categoryType)
                        .repeatType(RepeatType.WEEKLY)
                        .build());

                String mappedTexts = blockFields.stream()
                        .map(f -> f.getInferText().trim())
                        .collect(Collectors.joining("|"));
                log.info("[OCR-DIAG] 블록→일정: {} {}~{} title='{}' category={} (매핑필드 {}개: {})",
                        block.getDayOfWeek(), block.getStartTime(), block.getEndTime(),
                        title, categoryType, blockFields.size(), mappedTexts);
            }

        } catch (Exception e) {
            log.warn("블록-OCR 매핑 중 오류 발생, 빈 리스트 반환: {}", e.getMessage());
        }
        return result;
    }

    // ── title 조합 ───────────────────────────────────────────────────────────

    /**
     * 블록에 매핑된 Field를 줄 단위로 묶은 뒤, 위에서부터 이어지는 줄들을 제목으로 이어붙입니다.
     *
     * <ol>
     *   <li>Field를 y좌표 오름차순 정렬 후 근접한 y끼리 한 줄로 클러스터링</li>
     *   <li>첫 줄부터 아래로 체인 — 좁은 컬럼에서 3줄 이상으로 감긴 긴 제목도 온전히 복원
     *       (예: "사용자인" + "터페이스" + "및실습(나)")</li>
     *   <li>강의실/건물 위치 줄에서 중단 — "정보과학관 21203", "T0101", "학412", "정보과학관" 등은
     *       제목에 포함하지 않음</li>
     *   <li>첫 줄보다 작은 폰트(필드 높이 &lt; {@value #DETAIL_FONT_RATIO}배) 줄에서 중단 —
     *       에브리타임류 시간표는 교수명·장소 줄을 제목보다 작은 폰트로 렌더링하므로,
     *       패턴으로 못 잡는 교수명("정은혜")·비정형 장소("한경직기념관 08")도 여기서 걸러짐</li>
     *   <li>교수명 줄(2~4자 순수 한글 + 성씨 시작 + 바로 다음 줄이 장소 줄)에서 중단 —
     *       CLOVA 박스 높이 노이즈로 폰트 크기 판정이 빗나가도, '제목 → 교수명 → 장소'라는
     *       블록 구조와 성씨 사전으로 교수명을 걸러냄</li>
     *   <li>줄 간격이 벌어지면(1.8×줄높이 초과) 제목 종료로 간주</li>
     *   <li>공백 없이 이어붙임 (한국어 줄바꿈은 단어 중간에서 끊기므로)</li>
     *   <li>pixelsPerHour를 알 수 없을 때(≤0)는 전체 Field를 이어붙임</li>
     * </ol>
     *
     * <p>한계: 제목의 마지막 이어쓰기 조각이 성씨로 시작하는 2~4자 순수 한글("설계", "구조" 등)이면서
     * 교수명 없이 곧바로 장소 줄이 이어지는 드문 배치에서는 그 조각이 교수명으로 오인되어 잘릴 수 있다.
     *
     * <p>package-private: 테스트에서 제목 조립 규칙을 직접 검증하기 위해 접근 허용.
     */
    String buildTitle(List<ClovaOcrApiResponse.Field> fields, double pixelsPerHour) {
        if (fields.isEmpty()) return "";

        if (pixelsPerHour <= 0) {
            return fields.stream()
                    .map(f -> f.getInferText().trim())
                    .collect(Collectors.joining());
        }

        List<ClovaOcrApiResponse.Field> sorted = fields.stream()
                .sorted(Comparator.comparingDouble(this::fieldCenterY))
                .collect(Collectors.toList());

        double lineHeight = fieldHeight(sorted.get(0));
        if (lineHeight <= 2) lineHeight = pixelsPerHour * 0.15;

        // 1) y가 근접한 Field끼리 한 줄로 클러스터링
        List<List<ClovaOcrApiResponse.Field>> lines = new ArrayList<>();
        for (ClovaOcrApiResponse.Field f : sorted) {
            if (!lines.isEmpty()) {
                List<ClovaOcrApiResponse.Field> lastLine = lines.get(lines.size() - 1);
                double lastLineY = fieldCenterY(lastLine.get(0));
                if (Math.abs(fieldCenterY(f) - lastLineY) <= lineHeight * 0.6) {
                    lastLine.add(f);
                    continue;
                }
            }
            lines.add(new ArrayList<>(List.of(f)));
        }

        // 첫 줄(제목) 폰트 높이 — 이후 줄의 상세줄(교수/장소) 여부 판정 기준
        double titleFontHeight = medianFieldHeight(lines.get(0));

        // 줄별 텍스트 미리 계산 (교수명 판정 시 다음 줄 look-ahead에 필요)
        List<String> lineTexts    = new ArrayList<>();
        List<String> lineCompacts = new ArrayList<>();
        for (List<ClovaOcrApiResponse.Field> line : lines) {
            line.sort(Comparator.comparingDouble(this::fieldCenterX));
            String text = line.stream()
                    .map(f -> f.getInferText().trim())
                    .collect(Collectors.joining(" "));
            lineTexts.add(text);
            lineCompacts.add(text.replaceAll("\\s+", ""));
        }

        // 2) 위에서부터 체인으로 제목 줄 채택 (위치 줄·교수명 줄·작은 폰트 줄·큰 세로 간격에서 중단)
        StringBuilder title = new StringBuilder();
        double prevLineY = 0;
        for (int i = 0; i < lines.size(); i++) {
            List<ClovaOcrApiResponse.Field> line = lines.get(i);
            double lineY   = fieldCenterY(line.get(0));
            String text    = lineTexts.get(i);
            String compact = lineCompacts.get(i);

            if (i > 0) {
                if (lineY - prevLineY > lineHeight * 1.8) break; // 줄 간격 벌어짐 → 제목 종료
                if (isLocationLine(text, compact)) break;         // 강의실/건물 줄 → 제목 종료
                // 폰트 크기 판정은 3자 이상 줄에만 적용 — "크", ")", "어론" 같은 1~2자
                // 이어쓰기 조각은 글리프 잉크가 적어 OCR 박스 높이가 실제 폰트보다 작게
                // 측정되는 경우가 잦아(받침 없는 글자·괄호 등), 상세줄로 오판하면 제목
                // 마지막 글자가 잘린다. 1~2자 교수명 줄은 구조 규칙(아래)이 커버한다.
                if (titleFontHeight > 2
                        && compact.length() >= 3
                        && medianFieldHeight(line) < titleFontHeight * DETAIL_FONT_RATIO) {
                    break; // 제목보다 작은 폰트 → 교수명/장소 상세줄 → 제목 종료
                }
                // 교수명 줄(구조 기반): 2~4자 순수 한글 + 성씨로 시작 + 바로 다음 줄이 장소 줄.
                // 에브리타임 블록은 항상 '제목 → 교수명 → 장소' 순서이므로, 폰트 높이가
                // OCR 노이즈로 비슷하게 측정돼도 이 구조 조건으로 교수명을 걸러낼 수 있다.
                // - "산업"(조각): 다음 줄이 교수명(장소 아님) → 유지
                // - "텔링"(조각, 바로 아래가 장소): 성씨 시작이 아님 → 유지
                if (PERSON_NAME_PATTERN.matcher(compact).matches()
                        && KOREAN_SURNAME_CHARS.indexOf(compact.charAt(0)) >= 0
                        && i + 1 < lines.size()
                        && isLocationLine(lineTexts.get(i + 1), lineCompacts.get(i + 1))) {
                    break;
                }
            }

            title.append(compact);
            prevLineY = lineY;
        }
        return title.toString();
    }

    /** 줄을 구성하는 Field들의 중앙값 높이 (OCR 박스 높이 노이즈에 강건하도록 median 사용). */
    private double medianFieldHeight(List<ClovaOcrApiResponse.Field> line) {
        List<Double> heights = line.stream()
                .map(this::fieldHeight)
                .sorted()
                .collect(Collectors.toList());
        return heights.get(heights.size() / 2);
    }

    /** 강의실·건물 위치로 보이는 줄인지 판별 (제목 체인 중단 조건). */
    private boolean isLocationLine(String text, String compact) {
        return LECTURE_ROOM_PATTERN.matcher(text).find()
                || COMPACT_ROOM_CODE_PATTERN.matcher(compact).matches()
                || BUILDING_ONLY_PATTERN.matcher(compact).matches();
    }

    // ── 카테고리 추정 ────────────────────────────────────────────────────────

    /**
     * 블록 전체 텍스트(강의실·교수명 포함)를 기반으로 카테고리를 추정합니다.
     * title 조립(buildTitle)과 달리 위치 줄 등을 제외하지 않고 blockFields 전부를 사용합니다.
     *
     * <ol>
     *   <li>텍스트가 비어있으면 ETC</li>
     *   <li>알바/근로 키워드가 하나라도 포함되면 WORK</li>
     *   <li>강의실 패턴(건물명+호실)이 매칭되면 LECTURE</li>
     *   <li>그 외에는 기본값 LECTURE (에타 시간표 특성상 강의일 확률이 가장 높음)</li>
     * </ol>
     */
    private OcrCategoryType classifyBlock(List<ClovaOcrApiResponse.Field> blockFields) {
        // 1) 블록 전체 텍스트 (공백 join)
        String text = blockFields.stream()
                .map(f -> f.getInferText() == null ? "" : f.getInferText().trim())
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));

        if (text.isBlank()) return OcrCategoryType.ETC;

        // 2) 알바·근로 키워드 (공백 제거 후 부분 일치)
        String compact = text.replaceAll("\\s+", "");
        for (String keyword : WORK_KEYWORDS) {
            if (compact.contains(keyword)) return OcrCategoryType.WORK;
        }

        // 3) 강의실 패턴 매칭 → 강의
        if (LECTURE_ROOM_PATTERN.matcher(text).find()) return OcrCategoryType.LECTURE;

        // 4) 분류 불명 블록은 강의로 기본 처리
        return OcrCategoryType.LECTURE;
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
