FROM eclipse-temurin:21-jdk

WORKDIR /app

# Gradle 빌드 결과물 복사 (bootJar에서 생성된 고정 파일명)
COPY build/libs/application.jar application.jar

EXPOSE 8083

# 기본값 제거 - 반드시 환경변수로 주입받아야 함
ENV SERVER_PORT=8083

# AI 서비스는 메모리 사용량이 높으므로 JVM 옵션 조정
ENTRYPOINT ["sh", "-c", "\
    if [ -z \"$SPRING_PROFILES_ACTIVE\" ]; then \
        echo 'ERROR: SPRING_PROFILES_ACTIVE is not set!' && exit 1; \
    fi && \
    java -Djdk.httpclient.keepalive.timeout=20 \
         -Dserver.port=8083 \
         -XX:+UseG1GC \
         -XX:MaxGCPauseMillis=200 \
         -jar application.jar"]
