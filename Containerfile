FROM docker.io/library/gradle:8.10-jdk21 AS builder
WORKDIR /build
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src ./src
RUN gradle installDist --no-daemon -x test

FROM docker.io/library/debian:13-slim
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg ca-certificates msmtp curl openjdk-21-jre-headless \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /build/build/install/watchshark ./
COPY public ./public
RUN groupadd -r -g 1000 app && useradd -r -m -u 1000 -g app app && mkdir -p /data/videos /data/thumbs && chown -R app:app /app /data
EXPOSE 3000
ENV PORT=3000 DATA_DIR=/data PUBLIC_DIR=/app/public
VOLUME ["/data"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD ["curl", "-f", "http://localhost:3000/health"]
CMD ["./bin/watchshark"]
