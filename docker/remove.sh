#!/bin/sh
IMAGE_NAME=rasa-basic
echo stopping $IMAGE_NAME
docker stop $IMAGE_NAME
echo removing $IMAGE_NAME
docker rm $IMAGE_NAME
echo list active docker containers
docker ps
