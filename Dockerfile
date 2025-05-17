FROM openjdk:21-bookworm

LABEL authors="Jose Forero Angel"

WORKDIR /app
RUN mkdir /app/uploads
COPY ./target/death-note-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]