# 빌드 컨텍스트는 저장소 루트(compose.yaml 의 context: ..). 테스트는 여기서 돌리지 않는다(Testcontainers 가 Docker 를 요구).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
# jar 가 둘 이상이면 cp 가 실패한다(어느 걸 띄울지 모르는 채로 넘어가지 않게).
RUN ./gradlew --no-daemon bootJar && cp build/libs/*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system app
USER app
COPY --from=build /src/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
