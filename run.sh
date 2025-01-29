#!/bin/bash

# Check for project name and config file arguments
if [ -z "$1" ] || [ -z "$2" ]; then
  echo "Usage: $0 <project-name> <config-file>"
  echo "Valid project names: classical, blockchain"
  exit 1
fi

project="$1"
config_fliename="$2"
config_file="src/main/resources/${config_fliename}.json"

# Step 0: Generate new pom.xml
template="pom.xml.template"
output="pom.xml"
sed -e "s|\${project}|$project|g" "$template" > "$output"
echo "Generated $output from $template."

# Step 1: Clean, copy dependencies, and build the project
echo "Running Maven build..."
mvn clean dependency:copy-dependencies package

if [ $? -ne 0 ]; then
  echo "Maven build failed. Exiting."
  exit 1
fi

# Step 2: Run the Java application and save output to a file named based on config file
csv_output="output_${project}_${config_fliename}.txt"
echo "Running the application with config file $config_file and saving output to $csv_output..."
java -cp "target/energy.$project-1.0-SNAPSHOT.jar:target/dependency/*" "energy.$project" "$config_file" > "$csv_output"

if [ $? -ne 0 ]; then
  echo "Application failed to run. Check for errors above."
  exit 1
else
  echo "Application ran successfully and output saved to $csv_output."
fi
