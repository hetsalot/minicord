FROM gradle:8.9-jdk17 AS build
WORKDIR /app
COPY . .
RUN ./gradlew jar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/minicord-loadbalancer.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
