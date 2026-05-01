# =========================================================
# Multi-stage build:
#   1) gradle 이미지로 bootJar 만들기 (gradlew 없이도 OK)
#   2) JRE 17 슬림 이미지에 jar 복사
# =========================================================

# ---- Stage 1: build ----
FROM gradle:8.5-jdk17 AS build
WORKDIR /workspace

# 의존성 캐시 활용을 위해 빌드 정의 먼저 복사 → 캐시된 의존성 다운로드
COPY build.gradle settings.gradle ./
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 복사 후 jar 빌드 (테스트 제외)
COPY src ./src
RUN gradle --no-daemon clean bootJar -x test

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# 업로드 디렉터리 (실행 시 볼륨으로 마운트하면 영속화)
RUN mkdir -p /app/var/uploads
ENV FILE_UPLOAD_DIR=/app/var/uploads

# Asia/Seoul 시간대 (로그 가독성)
ENV TZ=Asia/Seoul

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

# JVM 옵션: 컨테이너 메모리 인식, file.encoding 명시
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Dfile.encoding=UTF-8", \
  "-jar", "/app/app.jar"]
