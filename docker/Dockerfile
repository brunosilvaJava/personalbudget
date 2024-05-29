FROM openjdk:21-jdk
LABEL maintainer="Bruno Silva"
## USER personalbudget_user
WORKDIR /app
COPY build/libs/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8080