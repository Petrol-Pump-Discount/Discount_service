# syntax=docker/dockerfile:1
# Build the jar on the host first:
#   cd backend && ./mvnw -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /data/uploads
COPY target/discount-service-0.0.1-SNAPSHOT.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
