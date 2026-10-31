FROM eclipse-temurin:17.0.18_8-jre-jammy
WORKDIR /app
COPY target/cool-admin-8.0.0.jar /app/backend.jar
EXPOSE 8001
ENTRYPOINT ["java", "-jar", "/app/backend.jar", "--spring.profiles.active=local"]
