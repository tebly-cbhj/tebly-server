package com.example.teblyserver.schedule.util;

import com.example.teblyserver.schedule.client.dto.ClovaOcrApiResponse;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 에브리타임 시각표 이미지를 픽셀+OCR 혼합 방식으로 분석해 수업 블록을 감지합니다.
 *
 * <pre>
 * 1단계 — 시간 눈금 y좌표 추출 (OCR Field 기반)
 * 2단계 — 요일 컬럼 x 범위 추출 (OCR Field 기반)
 * 3단계 — 색상 블록 감지          (픽셀 스캔 유지, 채도 기반 판별)
 * </pre>
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ImageBlockDetector {

    // ── 스캔 파라미터 ────────────────────────────────────────────────────────
    /** 같은 블록으로 병합할 y 간격 임계값(px) */
    private static final int BLOCK_MERGE_GAP_PX = 5;
    /** 배경 픽셀 판별: 최대 채널값이 이 이하이면 격자선/어두운 경계 픽셀로 제외 */
    private static final int PIXEL_MIN_BRIGHTNESS = 50;
    /** 배경 픽셀 판별: 최대 채널값이 이 이상이면 흰색 텍스트 픽셀로 제외 */
    private static final int PIXEL_MAX_BRIGHTNESS = 220;
    /** 색 경계 판정 hue 임계값(도) — 이 값 이상이면 다른 색으로 판단 */
    private static final int HUE_CHANGE_THRESHOLD = 30;
    /** 색 경계로 인정하는 최소 연속 행 수 기준 비율 (pixelsPerHour × 이 비율) */
    private static final double COLOR_BOUNDARY_ROW_RATIO = 0.15;
    /** 시간 눈금 OCR 탐지: 이미지 너비 대비 왼쪽 여백 비율 */
    private static final double TICK_LEFT_RATIO = 0.15;
    /** 요일 헤더 OCR 탐지: 이미지 높이 대비 상단 헤더 비율 */
    private static final double DAY_HEADER_TOP_RATIO = 0.10;
    /** 컬럼 색상 스캔 시 좌우로 잘라낼 폭 비율 — 테두리·눈금·옆 컬럼 색 혼입 차단 */
    private static final double COLUMN_SCAN_INSET_RATIO = 0.20;

    private static final String[] DAY_NAMES =
            {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};

    // ── 공개 API ─────────────────────────────────────────────────────────────

    /**
     * @param image  분석할 시각표 이미지 (픽셀 스캔 — 3단계)
     * @param fields CLOVA OCR Field 목록 (시간 눈금·요일 컬럼 추출 — 1·2단계)
     * @return 감지된 수업 블록 목록 (감지 실패 시 빈 리스트)
     */
    public static List<DetectedBlock> detect(
            BufferedImage image, List<ClovaOcrApiResponse.Field> fields) {

        List<DetectedBlock> result = new ArrayList<>();
        try {
            int imgWidth  = image.getWidth();
            int imgHeight = image.getHeight();

            // ── 1단계: OCR 기반 시간 눈금 y좌표 추출 ─────────────────────────
            Map<Integer, Double> hourToY = extractHourToY(fields, imgWidth);
            if (hourToY.isEmpty()) {
                log.warn("[ImageBlockDetector] 시간 눈금 미발견 → 빈 리스트 반환");
                return result;
            }
            double pixelsPerHour = computePixelsPerHour(hourToY);
            log.info("[OCR-DIAG] 이미지={}x{}, pixelsPerHour={}, 시간눈금({}개)={}",
                    imgWidth, imgHeight, String.format("%.1f", pixelsPerHour), hourToY.size(), formatHourToY(hourToY));

            // ── 2단계: OCR 기반 요일 컬럼 x 범위 추출 ────────────────────────
            List<double[]> columns = extractColumns(fields, imgWidth, imgHeight);
            if (columns.isEmpty()) {
                log.warn("[ImageBlockDetector] 요일 컬럼 미발견 → 빈 리스트 반환");
                return result;
            }
            log.debug("[ImageBlockDetector] 감지된 컬럼: {}개", columns.size());

            // 시간 눈금 범위 — 격자 밖(상태바·하단 UI) 블록 필터용
            double firstTickY = Collections.min(hourToY.values());
            double lastTickY  = Collections.max(hourToY.values());

            // ── 3단계: 픽셀 스캔 → 색상 블록 감지 ───────────────────────────
            for (int colIdx = 0; colIdx < columns.size(); colIdx++) {
                double[] col    = columns.get(colIdx);
                int      dayIdx = (int) col[2];
                String   day    = dayIdx < DAY_NAMES.length ? DAY_NAMES[dayIdx] : "DAY" + dayIdx;

                List<int[]> blocks = scanColumnForBlocks(image, (int) col[0], (int) col[1], pixelsPerHour, day);
                for (int[] block : blocks) {
                    int blockHeight = block[1] - block[0];
                    if (pixelsPerHour > 0 && blockHeight <= pixelsPerHour * 0.2) {
                        log.info("[OCR-DIAG] 노이즈 블록 제외: {} y={}-{} (height={}px, hue={})",
                                day, block[0], block[1], blockHeight, block[2]);
                        continue;
                    }
                    // 시간 눈금 범위 밖(상태바 '42' 등 격자 위/아래 UI) 블록 제외
                    if (pixelsPerHour > 0
                            && (block[1] < firstTickY - pixelsPerHour * 0.5
                                || block[0] > lastTickY + pixelsPerHour)) {
                        log.info("[OCR-DIAG] 격자 밖 블록 제외: {} y={}-{} (tick범위 {}~{})",
                                day, block[0], block[1], (int) firstTickY, (int) lastTickY);
                        continue;
                    }
                    String startTime = yToTime(block[0], hourToY, pixelsPerHour, true);
                    String endTime   = yToTime(block[1], hourToY, pixelsPerHour, false);
                    if (startTime == null || endTime == null) {
                        log.info("[OCR-DIAG] 시간 변환 실패로 블록 제외: {} y={}-{} (start={}, end={})",
                                day, block[0], block[1], startTime, endTime);
                        continue;
                    }

                    result.add(DetectedBlock.builder()
                            .xStart(col[0])
                            .xEnd(col[1])
                            .yStart(block[0])
                            .yEnd(block[1])
                            .dayOfWeek(day)
                            .startTime(startTime)
                            .endTime(endTime)
                            .build());

                    log.info("[OCR-DIAG] 블록 확정: {} {}~{} (y={}-{}, height={}px, hue={})",
                            day, startTime, endTime, block[0], block[1], blockHeight, block[2]);
                }
            }

        } catch (Exception e) {
            log.warn("[ImageBlockDetector] 블록 감지 중 오류 발생, 빈 리스트 반환: {}", e.getMessage());
        }
        return result;
    }

    /**
     * OCR Field 목록에서 pixelsPerHour를 계산합니다. {@link #detect} 와 동일한 내부 로직을 사용합니다.
     * 서비스에서 title 그룹화 임계값 계산에 활용합니다.
     */
    public static double computePixelsPerHour(List<ClovaOcrApiResponse.Field> fields, int imgWidth) {
        return computePixelsPerHour(extractHourToY(fields, imgWidth));
    }

    // ── 1단계: OCR 기반 시간 눈금 추출 ──────────────────────────────────────

    /**
     * inferText가 정수(9~23)인 Field 중 x좌표가 가장 왼쪽에 모인 것을 시간 눈금으로 인식합니다.
     * 동적 기준: 숫자 Field 전체의 x 최솟값 * 3.0 이내인 것만 채택 (고정 비율 fallback: {@value TICK_LEFT_RATIO}).
     * 같은 시각이 중복 감지된 경우 y가 가장 위(작은)인 것을 채택합니다.
     */
    private static Map<Integer, Double> extractHourToY(
            List<ClovaOcrApiResponse.Field> fields, int imgWidth) {

        double minNumericX = fields.stream()
                .filter(f2 -> {
                    try { int h = Integer.parseInt(f2.getInferText().trim());
                          return h >= 9 && h <= 23; }
                    catch (Exception e) { return false; }
                })
                .filter(ImageBlockDetector::hasBoundingPoly)
                .mapToDouble(ImageBlockDetector::avgX)
                .min().orElse(imgWidth * TICK_LEFT_RATIO);

        Map<Integer, Double> hourToY  = new TreeMap<>();
        Map<Integer, Double> hourMinY = new TreeMap<>(); // 중복 제거용

        // 1차 패스: 24시간 표기 (9~23)
        for (ClovaOcrApiResponse.Field f : fields) {
            if (!hasBoundingPoly(f) || f.getInferText() == null) continue;
            try {
                int hour = Integer.parseInt(f.getInferText().trim());
                if (hour < 9 || hour > 23) continue;
                if (avgX(f) > minNumericX * 3.0) continue;

                double cy = minY(f);
                if (!hourMinY.containsKey(hour) || cy < hourMinY.get(hour)) {
                    hourMinY.put(hour, cy);
                    hourToY.put(hour, cy);
                }
            } catch (NumberFormatException ignored) {}
        }

        // 2차 패스: 12시간 표기 보완 (1~8 → 13~20)
        // 에브리타임 등에서 오후 시각을 "1","2","3"... 으로 표기하는 경우 처리
        Double y12 = hourToY.get(12);
        if (y12 != null) {
            for (ClovaOcrApiResponse.Field f : fields) {
                if (!hasBoundingPoly(f) || f.getInferText() == null) continue;
                try {
                    int num = Integer.parseInt(f.getInferText().trim());
                    if (num < 1 || num > 8) continue;              // PM 라벨 범위
                    if (avgX(f) > minNumericX * 3.0) continue;     // 왼쪽 열 한정
                    if (minY(f) <= y12) continue;                   // "12" 눈금 아래에만

                    int    hour = num + 12;
                    double cy   = minY(f);
                    if (!hourMinY.containsKey(hour) || cy < hourMinY.get(hour)) {
                        hourMinY.put(hour, cy);
                        hourToY.put(hour, cy);
                        log.debug("[ImageBlockDetector] 12시간 표기 보완: '{}' → {}시 (y={})", num, hour, cy);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return hourToY;
    }

    // ── 2단계: OCR 기반 요일 컬럼 추출 ──────────────────────────────────────

    /**
     * OCR로 감지된 요일 헤더를 앵커로 삼아 컬럼 x 범위를 구성합니다.
     *
     * <ul>
     *   <li>unitWidth = (lastAnchorX - firstAnchorX) / (lastDayIdx - firstDayIdx) — dayIndex 기준</li>
     *   <li>dayIdx 0(월) ~ lastDetectedDayIdx 범위를 생성 (주말은 감지된 경우만 포함)</li>
     *   <li>미감지 요일은 첫 앵커에서 unitWidth 배수로 외삽해 보완 (좌측 포함)</li>
     *   <li>각 컬럼 경계: cx ± unitWidth/2 (마지막 컬럼도 imgWidth로 늘리지 않음)</li>
     *   <li>감지 헤더가 1개 이하이면 빈 리스트 반환</li>
     * </ul>
     *
     * @return 각 요일 컬럼의 {@code [xMin, xMax, dayIndex]} 리스트 (dayIndex 오름차순)
     */
    private static List<double[]> extractColumns(
            List<ClovaOcrApiResponse.Field> fields, int imgWidth, int imgHeight) {

        Map<String, Integer> KOR_TO_IDX = Map.of(
                "월", 0, "화", 1, "수", 2, "목", 3, "금", 4, "토", 5, "일", 6);
        Set<String> KOR_DAYS = KOR_TO_IDX.keySet();

        double minDayY = fields.stream()
                .filter(f2 -> KOR_DAYS.contains(
                        f2.getInferText() != null ? f2.getInferText().trim() : ""))
                .filter(ImageBlockDetector::hasBoundingPoly)
                .mapToDouble(ImageBlockDetector::avgY)
                .min().orElse(imgHeight * DAY_HEADER_TOP_RATIO);

        // dayIdx → centerX (TreeMap: dayIdx 오름차순 자동 정렬)
        TreeMap<Integer, Double> dayIdxToCenterX = new TreeMap<>();
        Map<Integer, Double>     dayIdxToMinY    = new HashMap<>();

        for (ClovaOcrApiResponse.Field f : fields) {
            if (!hasBoundingPoly(f) || f.getInferText() == null) continue;
            String text = f.getInferText().trim();
            if (!KOR_DAYS.contains(text)) continue;
            double cy = avgY(f);
            if (cy > minDayY + (imgHeight * 0.05)) continue;

            int dayIdx = KOR_TO_IDX.get(text);
            if (!dayIdxToMinY.containsKey(dayIdx) || cy < dayIdxToMinY.get(dayIdx)) {
                dayIdxToMinY.put(dayIdx, cy);
                dayIdxToCenterX.put(dayIdx, avgX(f));
            }
        }

        if (dayIdxToCenterX.size() < 2) {
            log.warn("[ImageBlockDetector] 요일 헤더 1개 이하 감지 → 빈 리스트 반환");
            return List.of();
        }

        // dayIndex 기준 단위 폭 계산 (감지 개수가 아닌 dayIdx 간격으로 나눔)
        int    firstDetectedIdx = dayIdxToCenterX.firstKey();
        int    lastDetectedIdx  = dayIdxToCenterX.lastKey();
        double firstDetectedX   = dayIdxToCenterX.get(firstDetectedIdx);
        double lastDetectedX    = dayIdxToCenterX.get(lastDetectedIdx);
        double unitWidth = (lastDetectedX - firstDetectedX) / (lastDetectedIdx - firstDetectedIdx);

        // dayIdx 0 ~ lastDetectedIdx 범위의 모든 컬럼 생성 (미감지 요일은 외삽으로 보완)
        List<double[]> columns = new ArrayList<>();
        for (int dayIdx = 0; dayIdx <= lastDetectedIdx; dayIdx++) {
            double cx = dayIdxToCenterX.containsKey(dayIdx)
                    ? dayIdxToCenterX.get(dayIdx)
                    : firstDetectedX + (dayIdx - firstDetectedIdx) * unitWidth;
            double xMin = Math.max(0, cx - unitWidth / 2.0);
            double xMax = Math.min(imgWidth, cx + unitWidth / 2.0);
            columns.add(new double[]{xMin, xMax, dayIdx});
            String dayName = dayIdx < DAY_NAMES.length ? DAY_NAMES[dayIdx] : "DAY" + dayIdx;
            log.info("[OCR-DIAG] 컬럼: {} cx={} x=[{}-{}] unitWidth={} {}",
                    dayName, (int) cx, (int) xMin, (int) xMax, (int) unitWidth,
                    dayIdxToCenterX.containsKey(dayIdx) ? "(헤더감지)" : "(외삽보완)");
        }

        return columns;
    }

    // ── 3단계: 픽셀 스캔 — 색상 블록 감지 (유지) ─────────────────────────────

    /**
     * 컬럼 x 범위 전체를 y행별로 스캔해 색상 블록을 감지합니다.
     *
     * <ul>
     *   <li>각 행: isBlockColor() 픽셀 비율 ≥ 50% → 블록 행</li>
     *   <li>배경 픽셀(흰 텍스트·검정 격자 제외)의 median hue를 행 대표색으로 사용</li>
     *   <li>블록 시작 hue 대비 변화가 {@value COLOR_BOUNDARY_ROW_RATIO} × pixelsPerHour 행 이상
     *       연속될 때만 색 경계로 인정 — 1~2행짜리 텍스트 줄에 의한 false 분리 방지</li>
     *   <li>분리 결과 얇은 조각(높이 &lt; pixelsPerHour × 0.5)은 인접 동색 블록에 흡수</li>
     *   <li>rawBlocks 배열: [yStart, yEnd, hue]</li>
     *   <li>{@value BLOCK_MERGE_GAP_PX}px 이하 간격이라도 hue가 다르면 병합 금지</li>
     * </ul>
     */
    private static List<int[]> scanColumnForBlocks(BufferedImage img, int xStart, int xEnd, double pixelsPerHour, String day) {
        // 컬럼 전체 폭 대신 중앙 밴드(좌우 20% 인셋)만 스캔.
        // 맨 왼쪽 컬럼의 표 테두리·시간 눈금 숫자나 외삽으로 어긋난 컬럼의 옆 컬럼 색 혼입을 차단해
        // 비어 있어야 할 흰 간격이 블록으로 잡혀 위 블록이 늘어나거나 두 수업이 묶이는 현상을 방지한다.
        int width  = xEnd - xStart;
        int inset  = (int) Math.round(width * COLUMN_SCAN_INSET_RATIO);
        int clampedStart = Math.max(0, xStart + inset);
        int clampedEnd   = Math.min(img.getWidth() - 1, xEnd - inset);
        if (clampedStart > clampedEnd) {
            // 인셋 후 폭이 사라지면(컬럼이 너무 좁음) 원래 범위로 폴백
            clampedStart = Math.max(0, xStart);
            clampedEnd   = Math.min(img.getWidth() - 1, xEnd);
        }
        if (clampedStart > clampedEnd) return List.of();

        int scanWidth         = clampedEnd - clampedStart + 1;
        int height            = img.getHeight();
        int colorBoundaryRows = Math.max(1, (int)(pixelsPerHour * COLOR_BOUNDARY_ROW_RATIO));
        List<int[]> rawBlocks = new ArrayList<>();

        // ── 진단: 컬럼을 세로로 균등 샘플해 픽셀 프로파일(RGB·채도·블록판정) 출력 ──
        // 빈 셀 배경이 회색 블록으로 오검출되는지, 경계 밝기가 얼마인지 직접 확인용
        int xMid = (clampedStart + clampedEnd) / 2;
        StringBuilder profile = new StringBuilder();
        int samples = 16;
        for (int s = 0; s < samples; s++) {
            int y = (int) ((long) s * (height - 1) / (samples - 1));
            int argb = img.getRGB(xMid, y);
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
            profile.append(String.format("y%d(%d,%d,%d sat%d %s) ",
                    y, r, g, b, mx - mn, isBlockColor(argb) ? "B" : "-"));
        }
        log.info("[OCR-DIAG] {} 픽셀프로파일 xMid={}: {}", day, xMid, profile.toString().trim());

        int blockStart       = -1;
        int blockStartHue    = -1; // 현재 블록 첫 유효 행의 hue (색 경계 앵커)
        int colorChangeCount = 0;  // hue 변화가 지속된 연속 행 수
        int colorChangeSince = -1; // 색 변화가 처음 감지된 y

        for (int y = 0; y < height; y++) {
            int          blockCount = 0;
            List<Integer> hues      = new ArrayList<>();

            for (int x = clampedStart; x <= clampedEnd; x++) {
                int argb = img.getRGB(x, y);
                if (isBlockColor(argb)) {
                    blockCount++;
                    if (isBackgroundPixel(argb)) hues.add(pixelHue(argb));
                }
            }
            boolean isBlock = blockCount * 2 >= scanWidth;
            int     rowHue  = hues.isEmpty() ? -1 : medianHue(hues);

            if (isBlock) {
                if (blockStart < 0) {
                    blockStart       = y;
                    blockStartHue    = rowHue;
                    colorChangeCount = 0;
                    colorChangeSince = -1;
                } else {
                    boolean hueChanged = rowHue >= 0 && blockStartHue >= 0
                            && hueDiff(blockStartHue, rowHue) >= HUE_CHANGE_THRESHOLD;
                    if (hueChanged) {
                        if (colorChangeSince < 0) colorChangeSince = y;
                        colorChangeCount++;
                        if (colorChangeCount >= colorBoundaryRows) {
                            // 연속 N행 이상 hue 변화 → 진짜 색 경계로 판정
                            rawBlocks.add(new int[]{blockStart, colorChangeSince - 1, blockStartHue});
                            log.debug("[ImageBlockDetector] 색 경계 블록 분리: y={} hueDiff={}",
                                    colorChangeSince, hueDiff(blockStartHue, rowHue));
                            blockStart       = colorChangeSince;
                            blockStartHue    = rowHue;
                            colorChangeCount = 0;
                            colorChangeSince = -1;
                        }
                    } else {
                        // 일시적 색 튐 또는 변화 없음 → 카운터 리셋
                        colorChangeCount = 0;
                        colorChangeSince = -1;
                        if (rowHue >= 0 && blockStartHue < 0) blockStartHue = rowHue;
                    }
                }
            } else {
                if (blockStart >= 0) {
                    rawBlocks.add(new int[]{blockStart, y - 1, blockStartHue});
                    blockStart       = -1;
                    blockStartHue    = -1;
                    colorChangeCount = 0;
                    colorChangeSince = -1;
                }
            }
        }
        if (blockStart >= 0) rawBlocks.add(new int[]{blockStart, height - 1, blockStartHue});

        List<int[]> absorbed = absorbThinFragments(rawBlocks, pixelsPerHour);
        List<int[]> merged   = mergeBlocks(absorbed);
        log.info("[OCR-DIAG] {} 스캔밴드 x=[{}-{}] (폭{}px) → raw={} absorb={} merge={}",
                day, clampedStart, clampedEnd, scanWidth,
                formatSegments(rawBlocks), formatSegments(absorbed), formatSegments(merged));
        return merged;
    }

    /** 블록 세그먼트 목록을 "[yStart-yEnd h색상]" 형태 문자열로 변환 (진단 로그용). */
    private static String formatSegments(List<int[]> segs) {
        if (segs.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < segs.size(); i++) {
            int[] s = segs.get(i);
            if (i > 0) sb.append(", ");
            sb.append(s[0]).append('-').append(s[1])
              .append("(h").append(s[2]).append(",H").append(s[1] - s[0]).append(')');
        }
        return sb.append(']').toString();
    }

    /** hourToY 맵을 "9:y,10:y,..." 형태로 변환 (진단 로그용). */
    private static String formatHourToY(Map<Integer, Double> hourToY) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Integer, Double> e : hourToY.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append("h=").append(e.getValue().intValue());
            first = false;
        }
        return sb.append('}').toString();
    }

    /**
     * 색 경계 판정 과정에서 생긴 false 분리 조각(텍스트 줄 등으로 1~2행 끊긴 세그먼트)을
     * <b>바로 맞닿은</b> 동색 블록에 흡수합니다.
     *
     * <p>흡수 조건:
     * <ul>
     *   <li>높이가 pixelsPerHour × 0.5 미만인 얇은 조각일 것</li>
     *   <li>대상 블록과 hue가 같을 것({@value HUE_CHANGE_THRESHOLD} 미만)</li>
     *   <li><b>두 세그먼트 사이 세로 간격이 absorbGap 이하일 것</b> — 흰 공백을 건너뛴 흡수 금지.
     *       이 게이트가 없으면 짧은 수업(예: '채플')이 한 칸 떨어진 다른 수업에 흡수되어
     *       여러 수업이 한 블록으로 묶인다.</li>
     * </ul>
     */
    private static List<int[]> absorbThinFragments(List<int[]> blocks, double pixelsPerHour) {
        if (blocks.size() < 2) return new ArrayList<>(blocks);
        int minHeight  = Math.max(1, (int)(pixelsPerHour * 0.5));
        int absorbGap  = Math.max(BLOCK_MERGE_GAP_PX, (int)(pixelsPerHour * 0.1));
        List<int[]> result = new ArrayList<>(blocks);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < result.size(); i++) {
                int[] cur = result.get(i);
                if (cur[1] - cur[0] >= minHeight) continue;
                // 앞 블록 흡수 우선 시도 — 맞닿아 있을 때만
                if (i > 0) {
                    int[] prev = result.get(i - 1);
                    boolean adjacent = cur[0] - prev[1] <= absorbGap;
                    if (adjacent && (cur[2] < 0 || prev[2] < 0 || hueDiff(cur[2], prev[2]) < HUE_CHANGE_THRESHOLD)) {
                        result.set(i - 1, new int[]{prev[0], cur[1], prev[2] >= 0 ? prev[2] : cur[2]});
                        result.remove(i);
                        changed = true;
                        break;
                    }
                }
                // 뒤 블록 흡수 시도 — 맞닿아 있을 때만
                if (i + 1 < result.size()) {
                    int[] next = result.get(i + 1);
                    boolean adjacent = next[0] - cur[1] <= absorbGap;
                    if (adjacent && (cur[2] < 0 || next[2] < 0 || hueDiff(cur[2], next[2]) < HUE_CHANGE_THRESHOLD)) {
                        result.set(i + 1, new int[]{cur[0], next[1], next[2] >= 0 ? next[2] : cur[2]});
                        result.remove(i);
                        changed = true;
                        break;
                    }
                }
            }
        }
        return result;
    }

    /** 같은 색 블록끼리만 병합 — hue가 다르면 {@value BLOCK_MERGE_GAP_PX}px 이내여도 병합 금지. */
    private static List<int[]> mergeBlocks(List<int[]> blocks) {
        if (blocks.isEmpty()) return blocks;
        List<int[]> merged = new ArrayList<>();
        int[] cur = blocks.get(0).clone();
        for (int i = 1; i < blocks.size(); i++) {
            int[] next     = blocks.get(i);
            boolean gapOk  = next[0] - cur[1] <= BLOCK_MERGE_GAP_PX;
            boolean colorOk = cur[2] < 0 || next[2] < 0
                    || hueDiff(cur[2], next[2]) < HUE_CHANGE_THRESHOLD;
            if (gapOk && colorOk) {
                cur[1] = next[1];
                cur[2] = (cur[2] >= 0 && next[2] >= 0)
                        ? (cur[2] + next[2]) / 2
                        : (cur[2] >= 0 ? cur[2] : next[2]);
            } else {
                merged.add(cur);
                cur = next.clone();
            }
        }
        merged.add(cur);
        return merged;
    }

    /** 유채색 배경 픽셀 판별: 흰색 텍스트(max > {@value PIXEL_MAX_BRIGHTNESS})와 검정 격자(max < {@value PIXEL_MIN_BRIGHTNESS}) 제외. */
    private static boolean isBackgroundPixel(int argb) {
        int max = Math.max((argb >> 16) & 0xFF, Math.max((argb >> 8) & 0xFF, argb & 0xFF));
        return max >= PIXEL_MIN_BRIGHTNESS && max <= PIXEL_MAX_BRIGHTNESS;
    }

    /** 픽셀의 hue 각도(0~359)를 반환합니다. 무채색이면 0. */
    private static int pixelHue(int argb) {
        float r = (argb >> 16) & 0xFF;
        float g = (argb >>  8) & 0xFF;
        float b =  argb        & 0xFF;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        if (max == min) return 0;
        float delta = max - min;
        float hue;
        if      (max == r) hue = (g - b) / delta + (g < b ? 6 : 0);
        else if (max == g) hue = (b - r) / delta + 2;
        else               hue = (r - g) / delta + 4;
        return ((int)(hue * 60) + 360) % 360;
    }

    /** hue 리스트의 중앙값을 반환합니다. */
    private static int medianHue(List<Integer> hues) {
        Collections.sort(hues);
        return hues.get(hues.size() / 2);
    }

    /** 두 hue 값의 원형 거리(0~180)를 반환합니다. */
    private static int hueDiff(int h1, int h2) {
        int diff = Math.abs(h1 - h2);
        return Math.min(diff, 360 - diff);
    }

    /** 회색 블록 판별: 채도가 낮아도 흰 배경/검정 격자가 아닌 중간 밝기면 블록으로 인정하는 밝기 범위 */
    private static final int GRAY_BLOCK_MIN_BRIGHTNESS = 60;
    private static final int GRAY_BLOCK_MAX_BRIGHTNESS = 205;

    /**
     * 블록 픽셀 판별.
     *
     * <ul>
     *   <li>유채색: RGB 최댓값-최솟값(채도) ≥ 30 → 수업 블록</li>
     *   <li>무채색(회색) 보완: 채도가 낮아도 흰 배경({@value GRAY_BLOCK_MAX_BRIGHTNESS} 초과)·
     *       검정 격자({@value GRAY_BLOCK_MIN_BRIGHTNESS} 미만)가 아닌 중간 밝기면 블록으로 인정
     *       — 에브리타임 '채플' 등 회색 블록 및 아주 연한 파스텔 블록 누락 방지</li>
     * </ul>
     */
    private static boolean isBlockColor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8)  & 0xFF;
        int b =  argb        & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if ((max - min) >= 30) return true;                       // 유채색 블록
        return max >= GRAY_BLOCK_MIN_BRIGHTNESS && max <= GRAY_BLOCK_MAX_BRIGHTNESS; // 회색 블록
    }

    // ── 픽셀 → 시간 역산 (hourToY 맵 선형 보간) ──────────────────────────────

    /**
     * y 픽셀 좌표를 "HH:mm" 시간 문자열로 변환합니다.
     *
     * <ol>
     *   <li>hourToY 맵에서 y에 가장 가까운 눈금(nearest) 찾기</li>
     *   <li>nearest까지의 px 차이를 분(minute)으로 변환</li>
     *   <li>nearest 정각에서 ± 분 적용</li>
     *   <li>분 차이가 10분 이내면 정각으로 snap</li>
     *   <li>5분 단위 반올림</li>
     * </ol>
     */
    private static String yToTime(double y, Map<Integer, Double> hourToY, double pixelsPerHour, boolean isStart) {
        if (hourToY.isEmpty() || pixelsPerHour <= 0) return null;

        // 가장 가까운 눈금 찾기
        Map.Entry<Integer, Double> nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Map.Entry<Integer, Double> entry : hourToY.entrySet()) {
            double dist = Math.abs(y - entry.getValue());
            if (dist < minDist) {
                minDist = dist;
                nearest = entry;
            }
        }
        if (nearest == null) return null;

        // px 차이 → 분 변환 (양수: 눈금 아래, 음수: 눈금 위)
        double diffPx  = y - nearest.getValue();
        double diffMin = (diffPx / pixelsPerHour) * 60.0;

        // 10분 이내면 정각으로 snap
        if (Math.abs(diffMin) <= 10.0) {
            diffMin = 0;
        }

        int totalMinutes = (int) Math.round(nearest.getKey() * 60.0 + diffMin);
        int rounded      = isStart
                ? roundToHalfHour(totalMinutes)
                : snapEndMinute(totalMinutes);
        int hour         = rounded / 60;
        int minute       = rounded % 60;
        if (hour < 0 || hour > 23) return null;
        return String.format("%02d:%02d", hour, minute);
    }

    // ── OCR Field 헬퍼 ───────────────────────────────────────────────────────

    private static boolean hasBoundingPoly(ClovaOcrApiResponse.Field f) {
        return f.getBoundingPoly() != null
                && f.getBoundingPoly().getVertices() != null
                && !f.getBoundingPoly().getVertices().isEmpty();
    }

    private static double avgX(ClovaOcrApiResponse.Field f) {
        return f.getBoundingPoly().getVertices().stream()
                .mapToDouble(ClovaOcrApiResponse.Vertex::getX).average().orElse(0);
    }

    private static double avgY(ClovaOcrApiResponse.Field f) {
        return f.getBoundingPoly().getVertices().stream()
                .mapToDouble(ClovaOcrApiResponse.Vertex::getY).average().orElse(0);
    }

    private static double minY(ClovaOcrApiResponse.Field f) {
        return f.getBoundingPoly().getVertices().stream()
                .mapToDouble(ClovaOcrApiResponse.Vertex::getY)
                .min().orElse(0);
    }

    // ── 공통 유틸 ───────────────────────────────────────────────────────────

    /**
     * hourToY 맵에서 연속 눈금의 평균 y 간격으로 pixelsPerHour를 계산합니다.
     * 눈금이 2개 미만이면 0.0 반환.
     */
    private static double computePixelsPerHour(Map<Integer, Double> hourToY) {
        if (hourToY.size() < 2) return 0.0;
        List<Map.Entry<Integer, Double>> sorted = hourToY.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toList());
        double totalPx    = sorted.get(sorted.size() - 1).getValue() - sorted.get(0).getValue();
        int    totalHours = sorted.get(sorted.size() - 1).getKey()   - sorted.get(0).getKey();
        return (totalHours > 0) ? totalPx / totalHours : 0.0;
    }

    private static int roundToHalfHour(int totalMinutes) {
        int remainder = totalMinutes % 30;
        return (remainder < 15) ? totalMinutes - remainder : totalMinutes + (30 - remainder);
    }

    private static final int[] VALID_END_MINUTES = {0, 15, 30, 45, 50, 60};

    private static int snapEndMinute(int totalMinutes) {
        int hourBase = (totalMinutes / 60) * 60;
        int minute   = totalMinutes % 60;
        int best = 0, bestDist = Integer.MAX_VALUE;
        for (int cand : VALID_END_MINUTES) {
            int dist = Math.abs(minute - cand);
            if (dist < bestDist) { bestDist = dist; best = cand; }
        }
        return hourBase + best; // best=60이면 다음 정각으로 자동 롤오버
    }

    // ── 반환 DTO ─────────────────────────────────────────────────────────────

    /**
     * 픽셀+OCR 혼합 분석으로 감지된 수업 블록 단건 DTO.
     */
    @Getter
    @Builder
    public static class DetectedBlock {
        /** 블록의 x 픽셀 범위 */
        private final double xStart;
        private final double xEnd;
        /** 블록의 y 픽셀 범위 */
        private final double yStart;
        private final double yEnd;
        /** 요일 (MON~SUN) */
        private final String dayOfWeek;
        /** 시작 시간 "HH:mm" */
        private final String startTime;
        /** 종료 시간 "HH:mm" */
        private final String endTime;
    }
}
