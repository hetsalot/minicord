FROM gradle:8.9-jdk17 AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/minicord-server.jar app.jar
EXPOSE 9001
ENTRYPOINT ["java", "-jar", "app.jar"]
