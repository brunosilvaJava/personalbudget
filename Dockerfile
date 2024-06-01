FROM gradle:jdk21 as gradle
WORKDIR /gradle
COPY build.gradle settings.gradle ./
COPY gradle/ ./gradle
RUN gradle dependencies --no-daemon

FROM gradle as builder
COPY src/ ./src
RUN gradle build --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="Bruno Silva"
RUN apk add --no-cache shadow && groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser
WORKDIR /app
COPY --chown=appuser:appuser --chmod=400 --from=builder /gradle/build/libs/*.jar /app/app.jar
RUN chmod 500 /app
ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8080
