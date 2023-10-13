#!/bin/sh
NAME=valuereporter-statsd-agent
docker logs -f $(docker ps -aqf "name=$NAME")