# Install latest version of Java
FROM java:latest

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
RUN ./gradlew --no-daemon fetchDependencies
RUN ./gradlew --no-daemon clean build

# Add entire student fork (overwrites previously added files)
ARG SUBMISSION_SUBFOLDER
ADD $SUBMISSION_SUBFOLDER /app

# Overwrite files in student fork with upstream files
ADD assessment /app/assessment
ADD test.sh /app
ADD build.gradle /app
