FROM gradle:jdk25-noble
LABEL authors="cristi"

WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY openapi ./openapi
COPY src ./src
# Repo-root dataset artifact packaged onto the classpath by processResources
# (curated catalogue image selection, issue #9 follow-up). The training manifest
# is not needed at runtime, so only this file enters the build context.
COPY dataset/catalogue-images.json ./dataset/catalogue-images.json

# curl is required by the Docker compose healthcheck
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN chmod +x ./gradlew
RUN ./gradlew bootJar
RUN cp ./build/libs/*.jar .
RUN mv ./*.jar ./app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]