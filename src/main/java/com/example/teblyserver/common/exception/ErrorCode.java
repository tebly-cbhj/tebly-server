package com.example.teblyserver.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 4xx - Client Error
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400_1", "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401_1", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403_1", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404_1", "요청한 리소스를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_1", "사용자를 찾을 수 없습니다."),
    DUPLICATE_FRIENDSHIP(HttpStatus.CONFLICT, "FRIEND_409_1", "이미 친구입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "USER_409_1", "이미 사용 중인 닉네임입니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_404_1", "해당 일정을 찾을 수 없습니다."),
    SCHEDULE_FORBIDDEN(HttpStatus.FORBIDDEN, "SCHEDULE_403_1", "해당 일정을 수정/삭제할 권한이 없습니다."),
    INVALID_SCHEDULE_TIME(HttpStatus.BAD_REQUEST, "SCHEDULE_400_1", "일정 시간이 올바르지 않습니다."),

    // OCR - 4xx
    OCR_EMPTY_FILE(HttpStatus.BAD_REQUEST, "OCR_400_1", "파일이 비어있습니다."),
    OCR_UNSUPPORTED_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "OCR_415_1", "지원하지 않는 파일 형식입니다. jpg, png, jpeg만 허용됩니다."),

    // room - 4xx
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "ROOM_404_1", "존재하지 않는 방입니다."),
    ROOM_FORBIDDEN(HttpStatus.FORBIDDEN, "ROOM_403_1", "해당 방에 접근할 권한이 없습니다."),
    HOST_CANNOT_LEAVE_ROOM(HttpStatus.BAD_REQUEST, "ROOM_400_1", "방장은 방을 나갈 수 없습니다."),

    // chat - 4xx
    CHAT_ROOM_MEMBER_FORBIDDEN(HttpStatus.FORBIDDEN, "CHAT_403_1", "해당 채팅방에 속한 멤버가 아닙니다."),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_404_1", "존재하지 않는 메시지입니다."),

    // notification - 4xx
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_404_1", "존재하지 않는 알림입니다."),
    PROMISE_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "PROMISE_400_1", "이미 확정되었거나 취소된 약속입니다."),
    INVALID_PROMISE_TIME(HttpStatus.BAD_REQUEST, "PROMISE_400_2", "약속 시간이 올바르지 않습니다."),
    INVALID_PROMISE_DURATION(HttpStatus.BAD_REQUEST, "PROMISE_400_3", "약속 시간이 최소 시간보다 짧습니다."),
    INVALID_PROMISE_CATEGORY(HttpStatus.BAD_REQUEST, "PROMISE_400_4", "약속 카테고리는 기본 카테고리 중에서 선택해야 합니다."),
    INVALID_PROMISE_MEMBER(HttpStatus.BAD_REQUEST, "PROMISE_400_5", "약속 참여 멤버가 올바르지 않습니다."),
    PROMISE_MEMBER_NOT_PENDING(HttpStatus.BAD_REQUEST, "PROMISE_400_5", "미응답 상태인 멤버만 콕찌르기 할 수 있습니다."),
    PROMISE_POKE_ALREADY_SENT(HttpStatus.BAD_REQUEST, "PROMISE_400_6", "오늘 이미 해당 멤버에게 콕찌르기를 보냈습니다."),
    CANNOT_POKE_SELF(HttpStatus.BAD_REQUEST, "PROMISE_400_7", "자기 자신에게는 콕찌르기를 보낼 수 없습니다."),
    PROMISE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROMISE_404_1", "존재하지 않는 약속입니다."),
    PROMISE_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "PROMISE_404_2", "해당 약속에 포함되지 않은 멤버입니다."),
    PROMISE_FORBIDDEN(HttpStatus.FORBIDDEN, "PROMISE_403_1", "해당 약속의 권한이 없습니다."),
    PROMISE_INVITEE_FORBIDDEN(HttpStatus.FORBIDDEN, "PROMISE_403_2", "초대 대상은 방에 참여 중인 멤버만 선택할 수 있습니다."),

    // OCR - 5xx
    OCR_API_CALL_FAILED(HttpStatus.BAD_GATEWAY, "OCR_502_1", "OCR API 호출에 실패했습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_404_1", "존재하지 않는 카테고리입니다."),
    CATEGORY_FORBIDDEN(HttpStatus.FORBIDDEN, "CATEGORY_403_1", "해당 카테고리에 대한 접근 권한이 없습니다."),
    DUPLICATE_CATEGORY_NAME(HttpStatus.CONFLICT, "CATEGORY_409_1", "이미 존재하는 카테고리 이름입니다."),
    CANNOT_DELETE_DEFAULT_CATEGORY(HttpStatus.BAD_REQUEST, "CATEGORY_400_1", "기본으로 제공되는 카테고리는 삭제할 수 없습니다."),

    // 5xx - Server Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500_1", "서버 내부 오류가 발생했습니다."),
    DEFAULT_CATEGORY_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "CATEGORY_500_1", "사용자의 기본(기타) 카테고리가 존재하지 않습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}