package com.example.teblyserver.schedule.service;

import com.example.teblyserver.schedule.client.dto.ClovaOcrApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 레퍼런스 시간표 블록들의 텍스트 구성(여러 줄로 감긴 제목, 강의실 코드 줄,
 * 건물명 단독 줄)을 그대로 재현해 buildTitle의 제목 조립 규칙을 검증한다.
 *
 * 좌표계: 줄 높이 20px, 줄 간격 26px, pixelsPerHour=80.
 */
class ScheduleOcrTitleTest {

    private static final double PPH = 80;

    private final ScheduleOcrService service = new ScheduleOcrService(null, null, null, null);

    @Test
    @DisplayName("3줄로 감긴 긴 제목은 끝까지 이어붙이고, 강의실 줄(건물명+호실)에서 멈춘다")
    void threeLineWrappedTitle_joinedAndStopsAtRoomLine() {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(field("사용자인", 100, 200));
        fields.add(field("터페이스", 100, 226));
        fields.add(field("및실습(나)", 100, 252));
        fields.add(field("정보과학관", 100, 278));
        fields.add(field("21203", 160, 278));

        assertThat(service.buildTitle(fields, PPH)).isEqualTo("사용자인터페이스및실습(나)");
    }

    @Test
    @DisplayName("강의실 코드 줄(T0101 등)은 제목에 포함되지 않는다")
    void compactRoomCodeLine_excludedFromTitle() {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(field("컴퓨터네트워크", 100, 200));
        fields.add(field("T0101", 100, 226));

        assertThat(service.buildTitle(fields, PPH)).isEqualTo("컴퓨터네트워크");
    }

    @Test
    @DisplayName("건물약칭+호실 줄(학412 등)도 제목에 포함되지 않는다")
    void shortRoomCodeLine_excludedFromTitle() {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(field("브랜드스토리", 100, 200));
        fields.add(field("텔링", 100, 226));
        fields.add(field("학412", 100, 252));

        assertThat(service.buildTitle(fields, PPH)).isEqualTo("브랜드스토리텔링");
    }

    @Test
    @DisplayName("호실 없이 건물명만 적힌 줄(정보과학관 등)에서도 제목이 멈춘다")
    void buildingOnlyLine_excludedFromTitle() {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(field("사운드디자인", 100, 200));
        fields.add(field("정보과학관", 100, 226));

        assertThat(service.buildTitle(fields, PPH)).isEqualTo("사운드디자인");
    }

    @Test
    @DisplayName("블록에 강의실 코드 한 줄만 있으면(첫 줄) 그대로 제목이 된다")
    void singleCodeLineBlock_keptAsTitle() {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(field("가0306", 100, 200));

        assertThat(service.buildTitle(fields, PPH)).isEqualTo("가0306");
    }

    @Test
    @DisplayName("제목보다 작은 폰트의 교수명 줄은 제목에 포함되지 않는다")
    void smallerFontProfessorLine_excludedFromTitle() {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(field("심리학의이해", 100, 200, 20)); // 제목 (큰 폰트)
        fields.add(field("오자영", 100, 226, 14));       // 교수명 (작은 폰트)
        fields.add(field("포B161", 100, 248, 14));       // 장소 (작은 폰트)

        assertThat(service.buildTitle(fields, PPH)).isEqualTo("심리학의이해");
    }

    @Test
    @DisplayName("제목과 같은 폰트 크기의 이어쓰기 조각은 교수명으로 오인되지 않고 유지된다")
    void sameFontWrappedFragment_keptInTitle() {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(field("미디어와문화", 100, 200, 20)); // 제목 1행
        fields.add(field("산업", 100, 226, 20));         // 제목 이어쓰기 (같은 폰트)
        fields.add(field("진예원", 100, 252, 14));       // 교수명 (작은 폰트)
        fields.add(field("정보508", 100, 274, 14));      // 장소

        assertThat(service.buildTitle(fields, PPH)).isEqualTo("미디어와문화산업");
    }

    // ── 실제 CLOVA 응답 재현: 박스 높이 노이즈로 폰트 크기가 비슷하게 측정된 경우 ──

    @Test
    @DisplayName("폰트 높이가 같게 측정돼도 '다음 줄이 장소'인 2~4자 한글 줄(교수명)은 제거된다")
    void sameMeasuredHeightProfessor_excludedByStructure() {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(field("인지심리학", 100, 200, 20)); // 제목
        fields.add(field("이혜원", 100, 226, 20));     // 교수명 (높이 노이즈로 제목과 동일 측정)
        fields.add(field("포252", 100, 252, 20));      // 장소

        assertThat(service.buildTitle(fields, PPH)).isEqualTo("인지심리학");
    }

    @Test
    @DisplayName("폰트 높이가 전부 같아도 이어쓰기 조각은 유지되고 교수명만 구조 규칙으로 제거된다")
    void allSameHeight_fragmentKeptProfessorDropped() {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(field("브랜드스토리", 100, 200, 20)); // 제목 1행
        fields.add(field("텔링", 100, 226, 20));         // 제목 이어쓰기 (다음 줄이 교수명 → 유지)
        fields.add(field("정은혜", 100, 252, 20));       // 교수명 (다음 줄이 장소 → 제거)
        fields.add(field("학412", 100, 278, 20));        // 장소

        assertThat(service.buildTitle(fields, PPH)).isEqualTo("브랜드스토리텔링");
    }

    // ── 실제 CLOVA 응답 재현: 마지막 이어쓰기 조각(1~2자)이 잘리는 문제 ──

    @Test
    @DisplayName("1자 이어쓰기 조각('크')은 박스 높이가 작게 측정돼도 제목에서 잘리지 않는다")
    void singleCharFragment_keptDespiteSmallMeasuredHeight() {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(field("컴퓨터네트워", 100, 200, 20)); // 제목 1행
        fields.add(field("크", 100, 226, 13));           // 이어쓰기 (받침 없는 글자라 박스가 작게 측정)
        fields.add(field("T0101", 100, 252, 14));        // 장소

        assertThat(service.buildTitle(fields, PPH)).isEqualTo("컴퓨터네트워크");
    }

    @Test
    @DisplayName("희귀 성씨 글자로 시작하는 이어쓰기 조각('어론')은 교수명으로 오판되지 않는다")
    void rareSurnameLikeFragment_keptInTitle() {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(field("프로그래밍언", 100, 200, 20)); // 제목 1행
        fields.add(field("어론", 100, 226, 20));         // 이어쓰기 ('어'는 초희귀 성씨 → 목록 제외)
        fields.add(field("T0802", 100, 252, 14));        // 장소

        assertThat(service.buildTitle(fields, PPH)).isEqualTo("프로그래밍언어론");
    }

    @Test
    @DisplayName("닫는 괄호만 남은 이어쓰기 줄(')')도 제목에서 잘리지 않는다")
    void parenOnlyFragment_keptInTitle() {
        List<ClovaOcrApiResponse.Field> fields = new ArrayList<>();
        fields.add(field("교양독일어(1", 100, 200, 20)); // 제목 1행
        fields.add(field(")", 100, 226, 12));            // 이어쓰기 (얇은 글리프라 박스가 작게 측정)
        fields.add(field("C526", 100, 252, 14));         // 장소

        assertThat(service.buildTitle(fields, PPH)).isEqualTo("교양독일어(1)");
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private static ClovaOcrApiResponse.Field field(String text, double x, double y) {
        return field(text, x, y, 20);
    }

    private static ClovaOcrApiResponse.Field field(String text, double x, double y, double h) {
        ClovaOcrApiResponse.Field f = new ClovaOcrApiResponse.Field();
        f.setInferText(text);
        ClovaOcrApiResponse.BoundingPoly poly = new ClovaOcrApiResponse.BoundingPoly();
        List<ClovaOcrApiResponse.Vertex> vertices = new ArrayList<>();
        double w = 60;
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
}
