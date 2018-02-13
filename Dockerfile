# Install latest release of Java version 9
FROM openjdk:9-jdk-slim

# Create directory for app
RUN mkdir /app

# Set as current directory for RUN, ADD, COPY commands
WORKDIR /app

# Add Gradle from upstream
ADD gradle /app/gradle
ADD gradlew /app
ADD gradlew.bat /app
ADD build.gradle /app

# Install dependencies
RUN { \
    echo 'task fetchDependencies { description "Pre-downloads *most* dependencies"'; \
    echo 'doLast { configurations.getAsMap().each { name, config ->'; \
    echo 'print "Fetching dependencies for $name..."'; \
    echo 'try { config.files; println "done" }'; \
    echo 'catch (e) { println ""; project.logger.info e.message; }'; \
    echo '} } }'; \
} >>/app/build.gradle
RUN ./gradlew --no-daemon clean fetchDependencies

# Add entire student fork (overwrites previously added files)
ARG SUBMISSION_SUBFOLDER
ADD $SUBMISSION_SUBFOLDER /app

# Overwrite files in student fork with upstream files
ADD assessment /app/assessment
ADD test.sh /app
ADD build.gradle /app
