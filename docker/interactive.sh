#!/bin/sh

BASEDIR=$(pwd)

#IMAGE_TAG=$(git rev-parse HEAD)
IMAGE_TAG=latest
IMAGE_NAME=rasa-basic
IMAGE_TITLE="RASA with shell"

docker run -it --rm -p 5005:5005 -v $(pwd):/app "$IMAGE_NAME:$IMAGE_TAG" /bin/bash