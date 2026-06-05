#!/bin/bash
../tools/find_last.sh ".heartbeat" logs/metasys-cloudconnector.log | ./enhance_date.sh
