#!/bin/bash

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 filename copy_to_file"
    exit 1
fi

filename=$1
copy_to_file=$2
grep """$(tools/current_hour_echo.sh)""" $filename > $copy_to_file
