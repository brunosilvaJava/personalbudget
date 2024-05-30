FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="Bruno Silva"
RUN apk add --no-cache shadow && groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser
WORKDIR /app
COPY --chown=appuser:appuser --chmod=400  build/libs/*.jar /app/app.jar
RUN chmod 500 /app
ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8080
