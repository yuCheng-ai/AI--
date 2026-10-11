FROM eclipse-temurin:25-jre

ENV TZ=UTC
WORKDIR /app
RUN mkdir -p /app/data/media && chown -R 10001:10001 /app
COPY --chmod=0444 backend/drama-boot/target/drama-server.jar /app/drama-server.jar
USER 10001:10001
EXPOSE 18080
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/drama-server.jar"]
