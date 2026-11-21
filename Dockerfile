FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN --mount=type=cache,id=s/7c61045a-bacd-4265-a22e-75000517ebd4-/root/.m2,target=/root/.m2 mvn -B --no-transfer-progress package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-venv tesseract-ocr tesseract-ocr-vie tesseract-ocr-eng libreoffice-writer fonts-dejavu-core && rm -rf /var/lib/apt/lists/*
COPY scripts/knowledge /app/scripts/knowledge
ENV KNOWLEDGE_PYTHON=/opt/knowledge/bin/python TIKTOKEN_CACHE_DIR=/opt/knowledge/tiktoken-cache
RUN python3 -m venv /opt/knowledge && /opt/knowledge/bin/pip install --no-cache-dir -r /app/scripts/knowledge/requirements.txt && /opt/knowledge/bin/python -c "import tiktoken; tiktoken.get_encoding('cl100k_base')" && chmod -R a+rX /opt/knowledge
RUN useradd --system --uid 10001 appuser && mkdir -p /app/data/map-artifacts /app/data/speech && chown -R appuser:appuser /app/data
COPY --from=build /workspace/target/backend-*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
