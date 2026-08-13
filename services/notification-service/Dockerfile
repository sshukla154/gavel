# syntax=docker/dockerfile:1
# Multi-stage build for notification-service (build context: repo root).

FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /build
COPY pom.xml .
COPY common/pom.xml common/
COPY services/auction-service/pom.xml services/auction-service/
COPY services/bid-service/pom.xml services/bid-service/
COPY services/notification-service/pom.xml services/notification-service/
COPY common/src common/src
RUN mvn -B -N install && \
    mvn -B -pl common install -DskipTests && \
    mvn -B -pl services/notification-service \
        dependency:resolve \
        dependency:resolve-plugins \
        -DincludeScope=test \
        -DskipTests

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY --from=deps /root/.m2 /root/.m2
COPY . .
RUN mvn -B -pl services/notification-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS extractor
WORKDIR /extract
COPY --from=build /build/services/notification-service/target/notification-service-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app -g 1001 \
    && useradd -r -u 1001 -g app -d /app appuser
WORKDIR /app
COPY --from=extractor /extract/app/dependencies/ ./
COPY --from=extractor /extract/app/spring-boot-loader/ ./
COPY --from=extractor /extract/app/snapshot-dependencies/ ./
COPY --from=extractor /extract/app/application/ ./
USER appuser
EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8083/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
