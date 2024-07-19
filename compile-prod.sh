#!/bin/bash


# Change directory to resources/backend
# This is where the backend code is located
cd resources/backend

# Install the dependencies listed in package.json
# This is necessary for the backend code to run
npm install

# Compile the backend code using shadow-cljs
# The "release" command compiles the code for production
shadow-cljs compile backend-ready