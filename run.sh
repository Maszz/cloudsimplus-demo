#!/bin/bash

# Step 0: generate new pom.xml
# Replace placeholders with values
template="pom.xml.template"
output="pom.xml"
sed -e "s|\${project}|$1|g" \
    "$template" > "$output"
echo "Generated $output from $template."

# Step 1: Clean, copy dependencies, and build the project
echo "Running Maven build..."
mvn clean dependency:copy-dependencies package

# Step 2: Check if the build was successful
if [ $? -ne 0 ]; then
  echo "Maven build failed. Exiting."
  exit 1
fi

# Step 3: Run the Java application
echo "Running the application..."
java -cp "target/$1-1.0-SNAPSHOT.jar:target/dependency/*" "com.example.cloudsim.$1.Main"

# Step 4: Check if the application ran successfully
if [ $? -ne 0 ]; then
  echo "Application failed to run. Check for errors above."
  exit 1
else
  echo "Application ran successfully."
fi