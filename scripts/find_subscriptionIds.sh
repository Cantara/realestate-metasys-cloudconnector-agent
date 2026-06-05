#!/bin/bash

# Check if the correct number of arguments is provided
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 filename"
    exit 1
fi

# Assign parameters to variables
start_pattern="subscriptionId:"
end_pattern=","
filename=$1

# Check if the file exists
if [ ! -f "$filename" ]; then
    echo "File not found: $filename"
    exit 1
fi

# Extract unique names based on the specified start and end patterns
grep -oP "${start_pattern} \K[^${end_pattern}]+" "$filename" | sort | uniq
