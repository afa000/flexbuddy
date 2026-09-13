FROM ubuntu:22.04 AS native-build

# Match Tess4J 5.20.0 / Lept4J 1.24.0. Jammy's older liblept package does
# not provide the leptonica library name and API expected by these wrappers.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates curl build-essential cmake pkg-config \
        libjpeg-dev libpng-dev libtiff-dev libwebp-dev libopenjp2-7-dev zlib1g-dev libgif-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /native
RUN curl -fL --retry 3 https://github.com/DanBloomberg/leptonica/releases/download/1.87.0/leptonica-1.87.0.tar.gz -o leptonica.tar.gz \
    && echo 'c73363397f96eb1295602bf44d708a994ad42046c791bf03ea0505d829bdb6a7  leptonica.tar.gz' | sha256sum -c - \
    && tar -xzf leptonica.tar.gz \
    && cmake -S leptonica-1.87.0 -B lept-build -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DBUILD_PROG=OFF \
    && cmake --build lept-build --parallel 2 \
    && cmake --install lept-build

RUN curl -fL --retry 3 https://github.com/tesseract-ocr/tesseract/archive/refs/tags/5.5.3.tar.gz -o tesseract.tar.gz \
    && echo '9218e62793116d42a9f6d14cd9348518b27f382096eea3d0f2d1a24616bb5884  tesseract.tar.gz' | sha256sum -c - \
    && tar -xzf tesseract.tar.gz \
    && cmake -S tesseract-5.5.3 -B tess-build -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DBUILD_TRAINING_TOOLS=OFF -DDISABLE_CURL=ON -DDISABLE_ARCHIVE=ON \
    && cmake --build tess-build --parallel 2 \
    && cmake --install tess-build

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package
COPY docker/OcrNativeSmokeCheck.java /smoke/OcrNativeSmokeCheck.java
RUN mkdir /smoke/app \
    && cd /smoke/app \
    && jar xf /workspace/target/flexbuddy-*.jar \
    && javac -cp 'BOOT-INF/classes:BOOT-INF/lib/*' -d /smoke /smoke/OcrNativeSmokeCheck.java

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libjpeg-turbo8 libpng16-16 libtiff5 libwebp7 libwebpmux3 libopenjp2-7 zlib1g libgif7 libgomp1 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=native-build /usr/local/lib/ /usr/local/lib/
COPY --from=native-build /usr/local/bin/tesseract /usr/local/bin/tesseract
RUN ldconfig && tesseract --version
RUN useradd --system --uid 1001 --create-home flexbuddy
WORKDIR /app
COPY --from=build /workspace/target/flexbuddy-*.jar app.jar
COPY docker-entrypoint.sh docker-entrypoint.sh
RUN chmod 0555 /app/docker-entrypoint.sh
USER flexbuddy
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]

FROM runtime AS native-check
COPY --from=build /smoke /smoke
RUN java -Djava.awt.headless=true -cp '/smoke:/smoke/app/BOOT-INF/classes:/smoke/app/BOOT-INF/lib/*' OcrNativeSmokeCheck \
    && touch /tmp/ocr-native-check-passed

FROM runtime
# Force the Java/native smoke check to pass without retaining its extra JAR copies.
COPY --from=native-check /tmp/ocr-native-check-passed /app/ocr-native-check-passed
