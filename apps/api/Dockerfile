FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg python3 pipx && \
    pipx install yt-dlp && \
    ln -s /root/.local/bin/yt-dlp /usr/local/bin/yt-dlp && \
    apt-get clean && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.net.preferIPv6Addresses=false", "-jar", "app.jar"]
