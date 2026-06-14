FROM eclipse-temurin:25-jre

WORKDIR /app

COPY target/socratic-tutor-1.0-SNAPSHOT.jar app.jar

ENV TZ=America/New_York

EXPOSE 6543

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
