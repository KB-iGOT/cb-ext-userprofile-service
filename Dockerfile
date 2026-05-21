FROM sivaprakash123/openjdk:17-slim-debian11

RUN useradd -ms /bin/bash appuser

# Install necessary dependencies
RUN apt-get update \
    && apt-get install -y \
        curl \
        libxrender1 \
        libjpeg62-turbo \
        fontconfig \
        libxtst6 \
        xfonts-75dpi \
        xfonts-base \
        xz-utils \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
    
COPY cb-ext-userprofile-service-1.0-SNAPSHOT.jar /opt/

RUN chown -R appuser:appuser /opt
USER appuser
WORKDIR /opt

#HEALTHCHECK --interval=30s --timeout=30s CMD curl --fail http://localhost:7001/actuator/health || exit 1
CMD ["/bin/bash", "-c", "java -XX:+PrintFlagsFinal $JAVA_OPTIONS -XX:+UnlockExperimentalVMOptions -jar /opt/cb-ext-userprofile-service-1.0-SNAPSHOT.jar"]
