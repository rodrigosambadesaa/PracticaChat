FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY src/ /app/src/
RUN mkdir -p /app/bin && javac -d /app/bin /app/src/chat/server/*.java

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/bin /app/bin
EXPOSE 9000 9080
CMD ["java", "-cp", "/app/bin", "chat.server.ServerMain", "9000", "9080"]
