#!/bin/sh
docker run -d --name=valuereporter-statsd-agent -p 22500:22500 baardl/valuereporter-statsd-agent
