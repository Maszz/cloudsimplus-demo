#!/bin/bash

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
java -cp "target/demo-1.0-SNAPSHOT.jar:target/dependency/*" demo.Main

# Step 4: Check if the application ran successfully
if [ $? -ne 0 ]; then
  echo "Application failed to run. Check for errors above."
  exit 1
else
  echo "Application ran successfully."
fi