FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 1001 --create-home flexbuddy

WORKDIR /app

COPY --from=build /workspace/target/flexbuddy-*.jar app.jar
COPY docker-entrypoint.sh docker-entrypoint.sh

RUN chmod 0555 /app/docker-entrypoint.sh

USER flexbuddy

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
