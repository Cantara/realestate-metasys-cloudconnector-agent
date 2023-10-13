#!/bin/sh
#IMAGE_TAG=$(git rev-parse HEAD)
IMAGE_TAG=latest
#IMAGE_NAME=cantara/valuereporter-statsd-agent
IMAGE_NAME=valuereporter-statsd-agent
curr_dir=${PWD}
docker run -d  --name=$IMAGE_NAME -p 22500:22500 "$IMAGE_NAME:$IMAGE_TAG"