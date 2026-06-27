package com.example.teblyserver.promise.service;

import com.example.teblyserver.auth.domain.Provider;
import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.promise.dto.internal.BusyScheduleTimeRange;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendRequest;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendationSortType;
import com.example.teblyserver.promise.dto.response.PromiseTimeRecommendationResponse;
import com.example.teblyserver.room.domain.InviteStatus;
import com.example.teblyserver.room.domain.Room;
import com.example.teblyserver.room.domain.RoomMember;
import com.example.teblyserver.room.domain.RoomRole;
import com.example.teblyserver.room.repository.RoomRepository;
import com.example.teblyserver.schedule.domain.RepeatType;
import com.example.teblyserver.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromiseRecommendationServiceTest {

    @InjectMocks
    private PromiseRecommendationService promiseRecommendationService;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    private Room createMockRoom(Long roomId) {
        Room room = Room.create("Test Room", "Description", "image.png");
        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(room, "members", new ArrayList<>());
        return room;
    }

    private User createMockUser(Long userId, String nickname) {
        User user = User.create(nickname + "@test.com", Provider.GOOGLE, "oauth_" + userId, nickname, "profile_" + userId);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private RoomMember createMockRoomMember(Room room, User user, RoomRole role, InviteStatus inviteStatus) {
        return RoomMember.create(room, user, role, inviteStatus);
    }

    @Test
    @DisplayName("1. 아무 일정도 없을 때 전체 시간이 전원 가능 후보로 나오는지")
    void test1_allAvailableWhenNoSchedules() {
        // given
        Long roomId = 1L;
        Long userId1 = 1L;
        Long userId2 = 2L;

        Room room = createMockRoom(roomId);
        User user1 = createMockUser(userId1, "user1");
        User user2 = createMockUser(userId2, "user2");
        createMockRoomMember(room, user1, RoomRole.HOST, InviteStatus.ACCEPTED);
        createMockRoomMember(room, user2, RoomRole.MEMBER, InviteStatus.ACCEPTED);

        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 1);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(12, 0);

        PromiseTimeRecommendRequest request = new PromiseTimeRecommendRequest(
                startDate, endDate, startTime, endTime, 60, PromiseTimeRecommendationSortType.EARLIEST
        );

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(scheduleRepository.findBusySchedulesByUserIdsAndPeriod(
                any(), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of());

        // when
        List<PromiseTimeRecommendationResponse> responses =
                promiseRecommendationService.recommendPromiseTimes(userId1, roomId, request);

        // then
        assertThat(responses).isNotEmpty();
        PromiseTimeRecommendationResponse firstResponse = responses.get(0);
        assertThat(firstResponse.allAvailable()).isTrue();
        assertThat(firstResponse.startTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 0));
        assertThat(firstResponse.endTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 12, 0));
        assertThat(firstResponse.durationMinutes()).isEqualTo(180);
        assertThat(firstResponse.availableMemberCount()).isEqualTo(2);
        assertThat(firstResponse.totalMemberCount()).isEqualTo(2);
        assertThat(firstResponse.reason()).isEqualTo("멤버 전원 가능한 시간");
    }

    @Test
    @DisplayName("2. minDuration보다 짧은 빈 구간은 제외되는지")
    void test2_excludeShorterThanMinDuration() {
        // given
        Long roomId = 1L;
        Long userId1 = 1L;
        Long userId2 = 2L;

        Room room = createMockRoom(roomId);
        User user1 = createMockUser(userId1, "user1");
        User user2 = createMockUser(userId2, "user2");
        createMockRoomMember(room, user1, RoomRole.HOST, InviteStatus.ACCEPTED);
        createMockRoomMember(room, user2, RoomRole.MEMBER, InviteStatus.ACCEPTED);

        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 1);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(12, 0);

        PromiseTimeRecommendRequest request = new PromiseTimeRecommendRequest(
                startDate, endDate, startTime, endTime, 120, PromiseTimeRecommendationSortType.EARLIEST
        );

        // Busy schedule for user1 from 10:00 to 11:00
        // Free slots: 09:00~10:00 (60m) and 11:00~12:00 (60m)
        // Since minDuration = 120m, neither free slot meets the minDuration, so there should be no allAvailable = true candidates.
        List<BusyScheduleTimeRange> busySchedules = List.of(
                new BusyScheduleTimeRange(userId1, LocalDateTime.of(2026, 7, 1, 10, 0), LocalDateTime.of(2026, 7, 1, 11, 0), RepeatType.NONE)
        );

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(scheduleRepository.findBusySchedulesByUserIdsAndPeriod(
                any(), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(busySchedules);

        // when
        List<PromiseTimeRecommendationResponse> responses =
                promiseRecommendationService.recommendPromiseTimes(userId1, roomId, request);

        // then
        boolean hasAllAvailable = responses.stream().anyMatch(PromiseTimeRecommendationResponse::allAvailable);
        assertThat(hasAllAvailable).isFalse();
    }

    @Test
    @DisplayName("3. 오늘 날짜에서 현재 시간 이전은 추천되지 않는지")
    void test3_noRecommendationBeforeCurrentTimeOnToday() {
        // given
        Long roomId = 1L;
        Long userId1 = 1L;

        Room room = createMockRoom(roomId);
        User user1 = createMockUser(userId1, "user1");
        createMockRoomMember(room, user1, RoomRole.HOST, InviteStatus.ACCEPTED);

        LocalDate today = LocalDate.of(2026, 7, 1);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(13, 0);

        PromiseTimeRecommendRequest request = new PromiseTimeRecommendRequest(
                today, today, startTime, endTime, 60, PromiseTimeRecommendationSortType.EARLIEST
        );

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(scheduleRepository.findBusySchedulesByUserIdsAndPeriod(
                any(), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of());

        // Mock LocalDateTime.now() to be 2026-07-01 10:15:00
        // Because of roundToNextSlot(now), 10:15 should be rounded up to 10:30.
        // Therefore, recommendations for today should start from 10:30, not before.
        LocalDateTime mockNow = LocalDateTime.of(2026, 7, 1, 10, 15);
        try (MockedStatic<LocalDateTime> mockedLocalDateTime = Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(mockNow);

            // when
            List<PromiseTimeRecommendationResponse> responses =
                    promiseRecommendationService.recommendPromiseTimes(userId1, roomId, request);

            // then
            assertThat(responses).isNotEmpty();
            for (PromiseTimeRecommendationResponse resp : responses) {
                assertThat(resp.startTime()).isAfterOrEqualTo(LocalDateTime.of(2026, 7, 1, 10, 30));
            }
        }
    }

    @Test
    @DisplayName("4. 반복 일정 WEEKLY가 추천 날짜에 반영되는지")
    void test4_weeklyRecurringScheduleReflected() {
        // given
        Long roomId = 1L;
        Long userId1 = 1L;
        Long userId2 = 2L;

        Room room = createMockRoom(roomId);
        User user1 = createMockUser(userId1, "user1");
        User user2 = createMockUser(userId2, "user2");
        createMockRoomMember(room, user1, RoomRole.HOST, InviteStatus.ACCEPTED);
        createMockRoomMember(room, user2, RoomRole.MEMBER, InviteStatus.ACCEPTED);

        // 2026-07-08 is Wednesday
        LocalDate targetWednesday = LocalDate.of(2026, 7, 8);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(13, 0);

        PromiseTimeRecommendRequest request = new PromiseTimeRecommendRequest(
                targetWednesday, targetWednesday, startTime, endTime, 60, PromiseTimeRecommendationSortType.EARLIEST
        );

        // A weekly schedule on Wednesday (2026-06-03 was Wednesday) from 10:00 to 12:00
        List<BusyScheduleTimeRange> busySchedules = List.of(
                new BusyScheduleTimeRange(userId1, LocalDateTime.of(2026, 6, 3, 10, 0), LocalDateTime.of(2026, 6, 3, 12, 0), RepeatType.WEEKLY)
        );

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(scheduleRepository.findBusySchedulesByUserIdsAndPeriod(
                any(), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(busySchedules);

        // when
        List<PromiseTimeRecommendationResponse> responses =
                promiseRecommendationService.recommendPromiseTimes(userId1, roomId, request);

        // then
        // Since there is a Wednesday weekly schedule from 10:00 to 12:00,
        // the remaining free slots of size >= 60 mins should be 09:00-10:00 and 12:00-13:00.
        List<PromiseTimeRecommendationResponse> allAvailableResponses = responses.stream()
                .filter(PromiseTimeRecommendationResponse::allAvailable)
                .toList();

        assertThat(allAvailableResponses).hasSize(2);
        assertThat(allAvailableResponses.get(0).startTime()).isEqualTo(LocalDateTime.of(2026, 7, 8, 9, 0));
        assertThat(allAvailableResponses.get(0).endTime()).isEqualTo(LocalDateTime.of(2026, 7, 8, 10, 0));
        assertThat(allAvailableResponses.get(1).startTime()).isEqualTo(LocalDateTime.of(2026, 7, 8, 12, 0));
        assertThat(allAvailableResponses.get(1).endTime()).isEqualTo(LocalDateTime.of(2026, 7, 8, 13, 0));
    }

    @Test
    @DisplayName("5. 전원 가능 후보와 충돌 최소 후보가 같이 candidatePool에 들어가는지")
    void test5_bothAllAvailableAndLeastConflictInCandidatePool() {
        // given
        Long roomId = 1L;
        Long userId1 = 1L;
        Long userId2 = 2L;

        Room room = createMockRoom(roomId);
        User user1 = createMockUser(userId1, "user1");
        User user2 = createMockUser(userId2, "user2");
        createMockRoomMember(room, user1, RoomRole.HOST, InviteStatus.ACCEPTED);
        createMockRoomMember(room, user2, RoomRole.MEMBER, InviteStatus.ACCEPTED);

        LocalDate date = LocalDate.of(2026, 7, 1);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(12, 0);

        PromiseTimeRecommendRequest request = new PromiseTimeRecommendRequest(
                date, date, startTime, endTime, 60, PromiseTimeRecommendationSortType.EARLIEST
        );

        // Member 1 is busy from 10:00 to 12:00.
        // 09:00 ~ 10:00: both free (allAvailable = true)
        // 10:00 ~ 12:00: only user2 free (allAvailable = false, leastConflict)
        List<BusyScheduleTimeRange> busySchedules = List.of(
                new BusyScheduleTimeRange(userId1, LocalDateTime.of(2026, 7, 1, 10, 0), LocalDateTime.of(2026, 7, 1, 12, 0), RepeatType.NONE)
        );

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(scheduleRepository.findBusySchedulesByUserIdsAndPeriod(
                any(), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(busySchedules);

        // when
        List<PromiseTimeRecommendationResponse> responses =
                promiseRecommendationService.recommendPromiseTimes(userId1, roomId, request);

        // then
        boolean hasAllAvailable = responses.stream().anyMatch(PromiseTimeRecommendationResponse::allAvailable);
        boolean hasLeastConflict = responses.stream().anyMatch(r -> !r.allAvailable());

        assertThat(hasAllAvailable).isTrue();
        assertThat(hasLeastConflict).isTrue();
    }

    @Test
    @DisplayName("6. 충돌 최소 후보가 최대 가능 구간으로 확장되는지")
    void test6_leastConflictCandidateExpandedToMaxInterval() {
        // given
        Long roomId = 1L;
        Long userId1 = 1L;
        Long userId2 = 2L;

        Room room = createMockRoom(roomId);
        User user1 = createMockUser(userId1, "user1");
        User user2 = createMockUser(userId2, "user2");
        createMockRoomMember(room, user1, RoomRole.HOST, InviteStatus.ACCEPTED);
        createMockRoomMember(room, user2, RoomRole.MEMBER, InviteStatus.ACCEPTED);

        LocalDate date = LocalDate.of(2026, 7, 1);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(13, 0);

        PromiseTimeRecommendRequest request = new PromiseTimeRecommendRequest(
                date, date, startTime, endTime, 60, PromiseTimeRecommendationSortType.EARLIEST
        );

        // Member 1 is busy from 12:00 to 13:00.
        // Member 2 is busy from 09:00 to 10:00.
        // For the slot starting at 09:00 (to 10:00), only Member 1 is free.
        // Since Member 1 is free until 12:00, this slot should expand to 09:00~12:00.
        List<BusyScheduleTimeRange> busySchedules = List.of(
                new BusyScheduleTimeRange(userId1, LocalDateTime.of(2026, 7, 1, 12, 0), LocalDateTime.of(2026, 7, 1, 13, 0), RepeatType.NONE),
                new BusyScheduleTimeRange(userId2, LocalDateTime.of(2026, 7, 1, 9, 0), LocalDateTime.of(2026, 7, 1, 10, 0), RepeatType.NONE)
        );

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(scheduleRepository.findBusySchedulesByUserIdsAndPeriod(
                any(), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(busySchedules);

        // when
        List<PromiseTimeRecommendationResponse> responses =
                promiseRecommendationService.recommendPromiseTimes(userId1, roomId, request);

        // then
        Optional<PromiseTimeRecommendationResponse> startAt9 = responses.stream()
                .filter(r -> r.startTime().equals(LocalDateTime.of(2026, 7, 1, 9, 0)))
                .findFirst();

        assertThat(startAt9).isPresent();
        assertThat(startAt9.get().endTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 12, 0));
        assertThat(startAt9.get().durationMinutes()).isEqualTo(180);
        assertThat(startAt9.get().availableMemberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("7. 포함되는 작은 충돌 후보가 제거되는지")
    void test7_removeDominatedCandidates() {
        // given
        Long roomId = 1L;
        Long userId1 = 1L;
        Long userId2 = 2L;

        Room room = createMockRoom(roomId);
        User user1 = createMockUser(userId1, "user1");
        User user2 = createMockUser(userId2, "user2");
        createMockRoomMember(room, user1, RoomRole.HOST, InviteStatus.ACCEPTED);
        createMockRoomMember(room, user2, RoomRole.MEMBER, InviteStatus.ACCEPTED);

        LocalDate date = LocalDate.of(2026, 7, 1);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(12, 0);

        PromiseTimeRecommendRequest request = new PromiseTimeRecommendRequest(
                date, date, startTime, endTime, 60, PromiseTimeRecommendationSortType.EARLIEST
        );

        // Member 2 is busy the whole time (09:00 to 12:00)
        // Candidate 09:00~12:00 contains 09:30~12:00, 10:00~12:00 etc. with same available members ({Member 1}).
        // The smaller ones should be dominated and removed.
        List<BusyScheduleTimeRange> busySchedules = List.of(
                new BusyScheduleTimeRange(userId2, LocalDateTime.of(2026, 7, 1, 9, 0), LocalDateTime.of(2026, 7, 1, 12, 0), RepeatType.NONE)
        );

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(scheduleRepository.findBusySchedulesByUserIdsAndPeriod(
                any(), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(busySchedules);

        // when
        List<PromiseTimeRecommendationResponse> responses =
                promiseRecommendationService.recommendPromiseTimes(userId1, roomId, request);

        // then
        boolean has0900 = responses.stream().anyMatch(r -> r.startTime().equals(LocalDateTime.of(2026, 7, 1, 9, 0)) && r.endTime().equals(LocalDateTime.of(2026, 7, 1, 12, 0)));
        boolean has0930 = responses.stream().anyMatch(r -> r.startTime().equals(LocalDateTime.of(2026, 7, 1, 9, 30)));
        boolean has1000 = responses.stream().anyMatch(r -> r.startTime().equals(LocalDateTime.of(2026, 7, 1, 10, 0)));

        assertThat(has0900).isTrue();
        assertThat(has0930).isFalse();
        assertThat(has1000).isFalse();
    }

    @Test
    @DisplayName("8. EARLIEST / LATEST / LONGEST 정렬이 의도대로 동작하는지")
    void test8_sortingTypes() {
        // given
        Long roomId = 1L;
        Long userId1 = 1L;
        Long userId2 = 2L;

        Room room = createMockRoom(roomId);
        User user1 = createMockUser(userId1, "user1");
        User user2 = createMockUser(userId2, "user2");
        createMockRoomMember(room, user1, RoomRole.HOST, InviteStatus.ACCEPTED);
        createMockRoomMember(room, user2, RoomRole.MEMBER, InviteStatus.ACCEPTED);

        LocalDate date = LocalDate.of(2026, 7, 1);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(13, 0); // 4 hours

        // Busy schedule at 10:00 ~ 10:30
        // Expected free slots:
        // 1. 09:00 ~ 10:00 (60 mins)
        // 2. 10:30 ~ 13:00 (150 mins)
        List<BusyScheduleTimeRange> busySchedules = List.of(
                new BusyScheduleTimeRange(userId1, LocalDateTime.of(2026, 7, 1, 10, 0), LocalDateTime.of(2026, 7, 1, 10, 30), RepeatType.NONE),
                new BusyScheduleTimeRange(userId2, LocalDateTime.of(2026, 7, 1, 10, 0), LocalDateTime.of(2026, 7, 1, 10, 30), RepeatType.NONE)
        );

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(scheduleRepository.findBusySchedulesByUserIdsAndPeriod(
                any(), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(busySchedules);

        // 1. EARLIEST Sort (9:00 first, 10:30 second)
        PromiseTimeRecommendRequest earliestReq = new PromiseTimeRecommendRequest(
                date, date, startTime, endTime, 60, PromiseTimeRecommendationSortType.EARLIEST
        );
        List<PromiseTimeRecommendationResponse> earliestResponses =
                promiseRecommendationService.recommendPromiseTimes(userId1, roomId, earliestReq);

        assertThat(earliestResponses.get(0).startTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 0));
        assertThat(earliestResponses.get(1).startTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 30));

        // 2. LATEST Sort (10:30 first, 9:00 second)
        PromiseTimeRecommendRequest latestReq = new PromiseTimeRecommendRequest(
                date, date, startTime, endTime, 60, PromiseTimeRecommendationSortType.LATEST
        );
        List<PromiseTimeRecommendationResponse> latestResponses =
                promiseRecommendationService.recommendPromiseTimes(userId1, roomId, latestReq);

        assertThat(latestResponses.get(0).startTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 30));
        assertThat(latestResponses.get(1).startTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 0));

        // 3. LONGEST Sort (10:30 [150 mins] first, 9:00 [60 mins] second)
        PromiseTimeRecommendRequest longestReq = new PromiseTimeRecommendRequest(
                date, date, startTime, endTime, 60, PromiseTimeRecommendationSortType.LONGEST
        );
        List<PromiseTimeRecommendationResponse> longestResponses =
                promiseRecommendationService.recommendPromiseTimes(userId1, roomId, longestReq);

        assertThat(longestResponses.get(0).startTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 30));
        assertThat(longestResponses.get(0).durationMinutes()).isEqualTo(150);
        assertThat(longestResponses.get(1).startTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 0));
        assertThat(longestResponses.get(1).durationMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("9. availableMembers / unavailableMembers가 정확히 나뉘는지")
    void test9_availableAndUnavailableMembersPartitioned() {
        // given
        Long roomId = 1L;
        Long userId1 = 1L;
        Long userId2 = 2L;
        Long userId3 = 3L;

        Room room = createMockRoom(roomId);
        User user1 = createMockUser(userId1, "user1");
        User user2 = createMockUser(userId2, "user2");
        User user3 = createMockUser(userId3, "user3");
        createMockRoomMember(room, user1, RoomRole.HOST, InviteStatus.ACCEPTED);
        createMockRoomMember(room, user2, RoomRole.MEMBER, InviteStatus.ACCEPTED);
        createMockRoomMember(room, user3, RoomRole.MEMBER, InviteStatus.ACCEPTED);

        LocalDate date = LocalDate.of(2026, 7, 1);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(12, 0);

        PromiseTimeRecommendRequest request = new PromiseTimeRecommendRequest(
                date, date, startTime, endTime, 60, PromiseTimeRecommendationSortType.EARLIEST
        );

        // Member 3 is busy the entire time. Members 1 & 2 are free.
        List<BusyScheduleTimeRange> busySchedules = List.of(
                new BusyScheduleTimeRange(userId3, LocalDateTime.of(2026, 7, 1, 9, 0), LocalDateTime.of(2026, 7, 1, 12, 0), RepeatType.NONE)
        );

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(scheduleRepository.findBusySchedulesByUserIdsAndPeriod(
                any(), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(busySchedules);

        // when
        List<PromiseTimeRecommendationResponse> responses =
                promiseRecommendationService.recommendPromiseTimes(userId1, roomId, request);

        // then
        assertThat(responses).isNotEmpty();
        PromiseTimeRecommendationResponse firstResponse = responses.get(0);
        assertThat(firstResponse.allAvailable()).isFalse();

        assertThat(firstResponse.availableMembers())
                .extracting(m -> m.userId())
                .containsExactlyInAnyOrder(userId1, userId2);

        assertThat(firstResponse.unavailableMembers())
                .extracting(m -> m.userId())
                .containsExactlyInAnyOrder(userId3);
    }
}
