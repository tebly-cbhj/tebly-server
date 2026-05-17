# 1단계: 빌드 스테이지
# 굳이 무거운 gradle 이미지를 쓰지 않고, 가벼운 eclipse-temurin 자바 이미지에서 직접 빌드합니다.
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /apps

# gradlew와 설정 파일들 먼저 복사 (의존성 캐싱)
COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN chmod +x gradlew
RUN ./gradlew build -x test --no-daemon || true

# 전체 소스 복사 후 빌드 수행
COPY . .
RUN ./gradlew clean bootJar -x test --no-daemon

# 2단계: 실행 스테이지
FROM eclipse-temurin:21-jre-jammy
WORKDIR /apps

# 빌드 스테이지에서 생성된 jar 파일 복사
COPY --from=builder /apps/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]