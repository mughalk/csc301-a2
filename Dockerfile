# syntax=docker/dockerfile:1

# Stage 1: compile
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY lib/ lib/
COPY src/ src/
RUN mkdir -p compiled && \
    find src -name "*.java" | xargs javac -cp "lib/*" -d compiled

# Stage 2: run
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*
COPY --from=build /app/compiled compiled/
COPY lib/ lib/
COPY config.docker.json config.json
