package com.example.teblyserver.schedule.util;

import com.example.teblyserver.schedule.client.dto.ClovaOcrApiResponse;
import com.example.teblyserver.schedule.util.ImageBlockDetector.DetectedBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 레퍼런스 시간표 스크린샷들의 특성(유사색 인접 블록, 12시간 표기, 다크모드,
 * 앱 UI 크롬 포함 전체 캡처)을 합성 이미지 + 가짜 OCR 필드로 재현해 검증한다.
 *
 * 좌표계: 9시 눈금 y=100, 시간당 80px (pixelsPerHour=80).
 * 요일 컬럼: 월 cx=150, 화 cx=280 (unitWidth 130 → 월 컬럼 x=[85,215]).
 */
class ImageBlockDetectorTest {

    private static final int IMG_W = 800;
    private static final int IMG_H = 1000;
    private static final int TICK_X = 30;
    private static final int HOUR9_Y = 100;
    private static final int PPH = 80; // pixels per hour

    // ── 레퍼런스 1 재현: 근로(코랄) 바로 아래 수업(오렌지) — 유사 hue 인접 블록 ──

    @Test
    @DisplayName("코랄색 근로 블록과 바로 아래 오렌지색 수업 블록은 별개 블록으로 분리되어야 한다")
    void adjacentSimilarHueBlocks_areSplit() {
        BufferedImage img = blankImage(Color.WHITE);
        // 월요일 10:30~12:00 코랄(근로), 12:00~14:00 오렌지(수업) — 딱 붙어 있음
        fillMonColumn(img, hourToY(10.5), hourToY(12.0), new Color(231, 112, 100));
        fillMonColumn(img, hourToY(12.0), hourToY(14.0), new Color(246, 166, 98));

        List<DetectedBlock> blocks = ImageBlockDetector.detect(img, standardFields(true));

        List<DetectedBlock> mon = blocksOf(blocks, "MON");
        assertThat(mon).hasSize(2);
        assertThat(mon.get(0).getStartTime()).isEqualTo("10:30");
        assertThat(mon.get(0).getEndTime()).isEqualTo("12:00");
        assertThat(mon.get(1).getStartTime()).isEqualTo("12:00");
        assertThat(mon.get(1).getEndTime()).isEqualTo("14:00");
    }

    // ── 12시간 표기에서 "12" 눈금이 OCR에 안 잡힌 경우 ──

    @Test
    @DisplayName("'12' 눈금이 OCR에서 누락돼도 오후(1~8) 라벨을 활용해 오후 블록을 감지한다")
    void pmLabels_usedEvenWhenNoonTickMissing() {
        BufferedImage img = blankImage(Color.WHITE);
        // 월요일 15:00~16:00 수업 하나
        fillMonColumn(img, hourToY(15.0), hourToY(16.0), new Color(168, 204, 122));

        // 눈금: 9,10,11만 인식되고 12는 누락. 오후 라벨 1~5는 인식됨.
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(tick("9", 9));
        fields.add(tick("10", 10));
        fields.add(tick("11", 11));
        fields.add(tick("1", 13));
        fields.add(tick("2", 14));
        fields.add(tick("3", 15));
        fields.add(tick("4", 16));
        fields.add(tick("5", 17));
        fields.add(dayHeader("월", 150));
        fields.add(dayHeader("화", 280));

        List<DetectedBlock> blocks = ImageBlockDetector.detect(img, fields);

        List<DetectedBlock> mon = blocksOf(blocks, "MON");
        assertThat(mon).hasSize(1);
        assertThat(mon.get(0).getStartTime()).isEqualTo("15:00");
        assertThat(mon.get(0).getEndTime()).isEqualTo("16:00");
    }

    // ── 레퍼런스 5 재현: 다크모드 (검정 배경 + 회색 격자선) ──

    @Test
    @DisplayName("다크모드: 회색 격자선은 블록으로 오인되지 않고 실제 수업 블록만 감지된다")
    void darkMode_gridLinesIgnored() {
        BufferedImage img = blankImage(Color.BLACK);
        // 시간 격자선 (다크모드 회색, 2px) — 9시~17시 매 정시
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(58, 58, 60));
        for (int h = 9; h <= 17; h++) {
            g.fillRect(0, hourToY(h), IMG_W, 2);
        }
        g.dispose();
        // 월요일 10:00~12:00 초록 수업 (격자선 위에 덮어 그림)
        fillMonColumn(img, hourToY(10.0), hourToY(12.0), new Color(52, 199, 89));

        List<DetectedBlock> blocks = ImageBlockDetector.detect(img, standardFields(true));

        List<DetectedBlock> mon = blocksOf(blocks, "MON");
        assertThat(mon).hasSize(1);
        assertThat(mon.get(0).getStartTime()).isEqualTo("10:00");
        assertThat(mon.get(0).getEndTime()).isEqualTo("12:00");
        // 화요일(빈 컬럼)에서는 격자선만 있으므로 아무 블록도 안 나와야 함
        assertThat(blocksOf(blocks, "TUE")).isEmpty();
    }

    // ── 레퍼런스 4 재현: 앱 전체 스크린샷 (상태바 배터리 칩 등 격자 밖 UI) ──

    @Test
    @DisplayName("앱 전체 캡처: 시간 격자 위쪽의 색상 UI(배터리 칩 등)는 블록에서 제외된다")
    void uiChromeAboveGrid_excluded() {
        BufferedImage img = blankImage(Color.WHITE);
        // 상태바 노란 배터리 칩 (y=20~50, 격자 시작 y=100보다 위)
        fillMonColumn(img, 20, 50, new Color(255, 204, 0));
        // 월요일 10:00~11:00 정상 수업
        fillMonColumn(img, hourToY(10.0), hourToY(11.0), new Color(127, 206, 192));

        List<DetectedBlock> blocks = ImageBlockDetector.detect(img, standardFields(true));

        List<DetectedBlock> mon = blocksOf(blocks, "MON");
        assertThat(mon).hasSize(1);
        assertThat(mon.get(0).getStartTime()).isEqualTo("10:00");
        assertThat(mon.get(0).getEndTime()).isEqualTo("11:00");
    }

    // ── 레퍼런스 1 재현: 주말(토·일) 포함 7일 시간표 ──

    @Test
    @DisplayName("토·일 헤더가 감지되면 주말 컬럼의 블록도 SAT/SUN으로 감지된다")
    void weekendColumns_detected() {
        // 7컬럼: unitWidth 100, 월 cx=120 … 일 cx=720
        BufferedImage img = blankImage(Color.WHITE);
        int satX0 = 620 - 50, satX1 = 620 + 50;
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(231, 112, 100));
        g.fillRect(satX0 + 5, hourToY(12.0), satX1 - satX0 - 10, hourToY(19.0) - hourToY(12.0));
        g.dispose();

        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        for (int h = 9; h <= 12; h++) fields.add(tick(String.valueOf(h), h));
        for (int n = 1; n <= 7; n++) fields.add(tick(String.valueOf(n), n + 12));
        String[] days = {"월", "화", "수", "목", "금", "토", "일"};
        for (int i = 0; i < days.length; i++) fields.add(dayHeader(days[i], 120 + i * 100));

        List<DetectedBlock> blocks = ImageBlockDetector.detect(img, fields);

        List<DetectedBlock> sat = blocksOf(blocks, "SAT");
        assertThat(sat).hasSize(1);
        assertThat(sat.get(0).getStartTime()).isEqualTo("12:00");
        assertThat(sat.get(0).getEndTime()).isEqualTo("19:00");
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private static int hourToY(double hour) {
        return (int) (HOUR9_Y + (hour - 9) * PPH);
    }

    private static BufferedImage blankImage(Color bg) {
        BufferedImage img = new BufferedImage(IMG_W, IMG_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, IMG_W, IMG_H);
        g.dispose();
        return img;
    }

    /** 월요일 컬럼(x=[85,215]) 안쪽에 y0~y1 색상 블록을 채운다. */
    private static void fillMonColumn(BufferedImage img, int y0, int y1, Color color) {
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(90, y0, 120, y1 - y0);
        g.dispose();
    }

    /** 표준 눈금(9~12 + 오후 1~8) + 월/화 헤더 필드 세트. */
    private static List<ClovaOcrApiResponse.Field> standardFields(boolean withNoonTick) {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        for (int h = 9; h <= (withNoonTick ? 12 : 11); h++) {
            fields.add(tick(String.valueOf(h), h));
        }
        for (int n = 1; n <= 8; n++) {
            fields.add(tick(String.valueOf(n), n + 12));
        }
        fields.add(dayHeader("월", 150));
        fields.add(dayHeader("화", 280));
        return fields;
    }

    private static ClovaOcrApiResponse.Field tick(String text, int hour) {
        return field(text, TICK_X, hourToY(hour), 20, 14);
    }

    private static ClovaOcrApiResponse.Field dayHeader(String text, double cx) {
        return field(text, cx - 10, 45, 20, 14);
    }

    private static ClovaOcrApiResponse.Field field(String text, double x, double y, double w, double h) {
        ClovaOcrApiResponse.Field f = new ClovaOcrApiResponse.Field();
        f.setInferText(text);
        ClovaOcrApiResponse.BoundingPoly poly = new ClovaOcrApiResponse.BoundingPoly();
        List<ClovaOcrApiResponse.Vertex> vertices = new ArrayList<>();
        double[][] corners = {{x, y}, {x + w, y}, {x + w, y + h}, {x, y + h}};
        for (double[] c : corners) {
            ClovaOcrApiResponse.Vertex v = new ClovaOcrApiResponse.Vertex();
            v.setX(c[0]);
            v.setY(c[1]);
            vertices.add(v);
        }
        poly.setVertices(vertices);
        f.setBoundingPoly(poly);
        return f;
    }

    private static List<DetectedBlock> blocksOf(List<DetectedBlock> blocks, String day) {
        return blocks.stream()
                .filter(b -> b.getDayOfWeek().equals(day))
                .sorted((a, b) -> Double.compare(a.getYStart(), b.getYStart()))
                .toList();
    }
}
