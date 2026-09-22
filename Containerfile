FROM docker.io/library/golang:1.27.1-bookworm AS builder
WORKDIR /build
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN CGO_ENABLED=0 go build -trimpath -o watchshark .

FROM docker.io/library/debian:13-slim
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg ca-certificates msmtp curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /build/watchshark ./
COPY public ./public
RUN groupadd -r -g 1000 app && useradd -r -m -u 1000 -g app app && mkdir -p /data/videos /data/thumbs && chown -R app:app /app /data
EXPOSE 3000
ENV PORT=3000 DATA_DIR=/data PUBLIC_DIR=/app/public
VOLUME ["/data"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD ["curl", "-f", "http://localhost:3000/health"]
CMD ["./watchshark"]
