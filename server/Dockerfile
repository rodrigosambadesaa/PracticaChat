FROM eclipse-temurin:21-jdk-alpine AS builder
# CVE-2026-114938: mitigate the Base64 JIT intrinsic until patched JDK images ship.
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"
WORKDIR /app
COPY src/ /app/src/
RUN mkdir -p /app/bin && javac -d /app/bin /app/src/chat/server/*.java

FROM eclipse-temurin:21-jre-alpine
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"
WORKDIR /app
COPY --from=builder /app/bin /app/bin
EXPOSE 9000 9080
CMD ["java", "-cp", "/app/bin", "chat.server.ServerMain", "9000", "9080"]
