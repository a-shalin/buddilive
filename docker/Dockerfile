FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY buddilive.jar buddilive.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "buddilive.jar", "--spring.profiles.active=server"]
