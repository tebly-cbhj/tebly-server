package com.example.teblyserver.decision.service;

import com.example.teblyserver.decision.client.GeminiClient;
import com.example.teblyserver.decision.dto.AdjacentScheduleDto;
import com.example.teblyserver.decision.dto.CandidateSlotDto;
import com.example.teblyserver.decision.dto.MemberSummaryDto;
import com.example.teblyserver.promise.service.PromiseRecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DecisionHelperServiceTest {

    private static final LocalDateTime BASE_START = LocalDateTime.of(2026, 7, 6, 9, 0);

    @Mock
    private PromiseRecommendationService promiseRecommendationService;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private SanitizeService sanitizeService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DecisionHelperService decisionHelperService;

    @Test
    @DisplayName("가용 인원/촉박 인원이 같으면 더 긴 시간대를 fallback으로 선택한다")
    void resolveFallbackCandidate_prefersLongerDuration() {
        CandidateSlotDto shortSlot = candidateWithDuration("slot-1", 60);
        CandidateSlotDto longSlot = candidateWithDuration("slot-2", 120);

        CandidateSlotDto result = decisionHelperService.resolveFallbackCandidate(List.of(shortSlot, longSlot));

        assertThat(result.slotId()).isEqualTo("slot-2");
    }

    @Test
    @DisplayName("가용 인원/시간이 같으면 촉박한 멤버가 적은 후보를 fallback으로 선택한다")
    void resolveFallbackCandidate_prefersFewerTightMembers() {
        CandidateSlotDto tightSlot = candidateWithTightMemberCount("slot-1", 1);
        CandidateSlotDto looseSlot = candidateWithTightMemberCount("slot-2", 0);

        CandidateSlotDto result = decisionHelperService.resolveFallbackCandidate(List.of(tightSlot, looseSlot));

        assertThat(result.slotId()).isEqualTo("slot-2");
    }

    @Test
    @DisplayName("촉박 인원/시간이 같으면 가용 인원이 많은 후보를 fallback으로 선택한다")
    void resolveFallbackCandidate_prefersMoreAvailableMembers() {
        CandidateSlotDto fewerAvailable = candidateWithAvailableCount("slot-1", 2);
        CandidateSlotDto moreAvailable = candidateWithAvailableCount("slot-2", 3);

        CandidateSlotDto result = decisionHelperService.resolveFallbackCandidate(List.of(fewerAvailable, moreAvailable));

        assertThat(result.slotId()).isEqualTo("slot-2");
    }

    @Test
    @DisplayName("위 우선순위가 모두 같으면 더 이른 시간대를 fallback으로 선택한다")
    void resolveFallbackCandidate_prefersEarlierStartTime() {
        CandidateSlotDto later = candidateWithStartTime("slot-1", BASE_START.plusHours(5));
        CandidateSlotDto earlier = candidateWithStartTime("slot-2", BASE_START);

        CandidateSlotDto result = decisionHelperService.resolveFallbackCandidate(List.of(later, earlier));

        assertThat(result.slotId()).isEqualTo("slot-2");
    }

    @Test
    @DisplayName("한 멤버가 beforeSlot/afterSlot 둘 다 촉박해도 1명으로만 집계되어 fallback 순위에 반영된다")
    void resolveFallbackCandidate_dedupesTightMemberAcrossBeforeAndAfterSlot() {
        AdjacentScheduleDto tightBefore = new AdjacentScheduleDto("헬스", BASE_START.minusMinutes(30), BASE_START.minusMinutes(10), 10);
        AdjacentScheduleDto tightAfter = new AdjacentScheduleDto("스터디", BASE_START.plusMinutes(70), BASE_START.plusMinutes(90), 10);

        // 한 멤버가 앞뒤 모두 촉박 (실제로는 촉박한 멤버 1명)
        MemberSummaryDto bothTightMember = new MemberSummaryDto("A", tightBefore, tightAfter);
        MemberSummaryDto looseMember = new MemberSummaryDto("B", null, null);
        CandidateSlotDto oneTightMemberSlot = new CandidateSlotDto(
                "slot-1", BASE_START, BASE_START.plusMinutes(60), 60, true, 2, 2,
                List.of(bothTightMember, looseMember), List.of()
        );

        // 촉박한 멤버가 아예 없는 후보
        CandidateSlotDto noTightMemberSlot = candidateWithTightMemberCount("slot-2", 0);

        CandidateSlotDto result = decisionHelperService.resolveFallbackCandidate(
                List.of(oneTightMemberSlot, noTightMemberSlot)
        );

        // 촉박한 멤버 수가 1명(중복 집계 X)인 slot-1이 촉박 0명인 slot-2보다 우선순위가 낮아야 한다
        assertThat(result.slotId()).isEqualTo("slot-2");
    }

    private CandidateSlotDto candidateWithDuration(String slotId, int durationMinutes) {
        return new CandidateSlotDto(
                slotId, BASE_START, BASE_START.plusMinutes(durationMinutes), durationMinutes,
                true, 2, 2, List.of(looseMember("A"), looseMember("B")), List.of()
        );
    }

    private CandidateSlotDto candidateWithTightMemberCount(String slotId, int tightCount) {
        List<MemberSummaryDto> members = new ArrayList<>();
        for (int i = 0; i < tightCount; i++) {
            members.add(tightMember("Tight" + i));
        }
        for (int i = tightCount; i < 2; i++) {
            members.add(looseMember("Loose" + i));
        }

        return new CandidateSlotDto(slotId, BASE_START, BASE_START.plusMinutes(60), 60, true, 2, 2, members, List.of());
    }

    private CandidateSlotDto candidateWithAvailableCount(String slotId, int availableCount) {
        List<MemberSummaryDto> members = new ArrayList<>();
        for (int i = 0; i < availableCount; i++) {
            members.add(looseMember("M" + i));
        }

        return new CandidateSlotDto(slotId, BASE_START, BASE_START.plusMinutes(60), 60, true, availableCount, availableCount, members, List.of());
    }

    private CandidateSlotDto candidateWithStartTime(String slotId, LocalDateTime startTime) {
        return new CandidateSlotDto(
                slotId, startTime, startTime.plusMinutes(60), 60, true, 2, 2,
                List.of(looseMember("A"), looseMember("B")), List.of()
        );
    }

    private MemberSummaryDto looseMember(String name) {
        return new MemberSummaryDto(name, null, null);
    }

    private MemberSummaryDto tightMember(String name) {
        AdjacentScheduleDto tightBefore = new AdjacentScheduleDto("헬스", BASE_START.minusMinutes(30), BASE_START.minusMinutes(10), 10);
        return new MemberSummaryDto(name, tightBefore, null);
    }
}
