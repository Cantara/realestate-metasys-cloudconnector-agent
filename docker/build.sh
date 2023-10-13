#!/bin/sh

BASEDIR=$(pwd)

#IMAGE_TAG=$(git rev-parse HEAD)
IMAGE_TAG=latest
IMAGE_NAME=rasa-basic
IMAGE_TITLE="RASA with shell"

echo "Building Docker image $IMAGE_TITLE ($IMAGE_ID) from $BASEDIR."

docker build -t "$IMAGE_NAME" "$BASEDIR" || exit 1
#docker build -t "$IMAGE_NAME" "$BASEDIR" --no-cache|| exit 1

docker tag "$IMAGE_NAME" "$IMAGE_NAME:$IMAGE_TAG" || exit 1
