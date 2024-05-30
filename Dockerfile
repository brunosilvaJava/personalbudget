FROM openjdk:21-jdk
LABEL maintainer="Bruno Silva"
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser
WORKDIR /app
COPY --chown=appuser:appuser --chmod=400  build/libs/*.jar /app/app.jar
RUN chmod 500 /app
ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8080
