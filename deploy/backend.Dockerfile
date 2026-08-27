# syntax=docker/dockerfile:1.7
FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY server ./server
COPY mock ./mock

RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode -pl :ccb-boot -am -DskipTests package \
    && cp server/src/platform/boot/target/ccb-boot-*.jar /workspace/app.jar

FROM --platform=$TARGETPLATFORM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home rddmp

WORKDIR /app
COPY --from=builder --chown=rddmp:rddmp /workspace/app.jar /app/app.jar

USER rddmp
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
