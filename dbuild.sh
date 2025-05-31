#!/bin/sh

# Build the plugin
./gradlew build

# Copy the jar to the server's plugins folder
cp ./build/libs/Official-Addons.jar ./NovaServer/plugins/

cd ./NovaServer

# Run the server
java -Xms4G -Xmx4G -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -DNovaDev -jar paper.jar --nogui
