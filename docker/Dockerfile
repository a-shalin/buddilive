FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY buddilive.jar buddilive.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "buddilive.jar", "--spring.profiles.active=server"]
