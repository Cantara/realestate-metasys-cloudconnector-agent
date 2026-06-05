#!/bin/bash

# Check if the correct number of arguments is provided
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 pattern filename"
    exit 1
fi

# Assign parameters to variables
start_pattern=$1
filename=$2

# Check if the file exists
if [ ! -f "$filename" ]; then
    echo "File not found: $filename"
    exit 1
fi

# Extract unique names based on the specified start and end patterns
grep -m 1 "${start_pattern}" "$filename"
