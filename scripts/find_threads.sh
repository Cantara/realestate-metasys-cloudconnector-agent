#!/bin/bash
filename=$1
grep -oP "\[33m.*?\]" "$filename" |sort |uniq | grep -v IotHub
