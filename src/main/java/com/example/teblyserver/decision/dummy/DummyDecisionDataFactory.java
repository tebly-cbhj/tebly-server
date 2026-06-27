package com.example.teblyserver.decision.dummy;

import com.example.teblyserver.decision.dto.CandidateSlotDto;
import com.example.teblyserver.decision.dto.DecisionCacheDto;
import com.example.teblyserver.decision.dto.MemberAvailabilityDto;
import com.example.teblyserver.decision.dto.MemberSummaryDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// TODO: 알고리즘 구현 완료 후 삭제 예정
/**
 * 실제 알고리즘(PromiseRecommendationService) 결과를 받아오기 전까지 사용하는 테스트용 더미 데이터 팩토리.
 * noCandidate=false(후보 비교) / true(대안 제시) 두 케이스를 모두 제공한다.
 */
public final class DummyDecisionDataFactory {

    private DummyDecisionDataFactory() {
    }

    /**
     * Redis 캐시 미스 시 폴백용 더미 캐시(noCandidate=false, 후보 비교 케이스).
     */
    public static DecisionCacheDto dummyDecisionCache() {
        return new DecisionCacheDto(
                false,
                dummyCandidates(),
                null,
                dummyRangeFrom(),
                dummyRangeTo()
        );
    }

    // noCandidate=false 케이스: 전원 가능/일부 충돌을 섞은 후보 3개
    public static List<CandidateSlotDto> dummyCandidates() {
        return List.of(
                new CandidateSlotDto(
                        "slot-1",
                        LocalDateTime.of(2026, 6, 24, 14, 0),
                        LocalDateTime.of(2026, 6, 24, 16, 0),
                        120,
                        true,
                        4,
                        4,
                        List.of(new MemberSummaryDto("철수"), new MemberSummaryDto("영희"),
                                new MemberSummaryDto("민수"), new MemberSummaryDto("지영")),
                        List.of()
                ),
                new CandidateSlotDto(
                        "slot-2",
                        LocalDateTime.of(2026, 6, 25, 19, 0),
                        LocalDateTime.of(2026, 6, 25, 21, 0),
                        120,
                        false,
                        3,
                        4,
                        List.of(new MemberSummaryDto("철수"), new MemberSummaryDto("영희"),
                                new MemberSummaryDto("지영")),
                        List.of(new MemberSummaryDto("민수"))
                ),
                new CandidateSlotDto(
                        "slot-3",
                        LocalDateTime.of(2026, 6, 27, 11, 0),
                        LocalDateTime.of(2026, 6, 27, 12, 30),
                        90,
                        false,
                        2,
                        4,
                        List.of(new MemberSummaryDto("철수"), new MemberSummaryDto("민수")),
                        List.of(new MemberSummaryDto("영희"), new MemberSummaryDto("지영"))
                )
        );
    }

    // noCandidate=true 케이스: 멤버별 가능 시간대
    public static List<MemberAvailabilityDto> dummyMemberAvailability() {
        return List.of(
                new MemberAvailabilityDto("철수", List.of("09:00-11:00", "14:00-17:00")),
                new MemberAvailabilityDto("영희", List.of("10:00-12:00", "15:00-18:00")),
                new MemberAvailabilityDto("민수", List.of("13:00-16:00")),
                new MemberAvailabilityDto("지영", List.of("09:00-10:30", "16:00-19:00"))
        );
    }

    public static LocalDate dummyRangeFrom() {
        return LocalDate.of(2026, 6, 24);
    }

    public static LocalDate dummyRangeTo() {
        return LocalDate.of(2026, 6, 28);
    }
}
