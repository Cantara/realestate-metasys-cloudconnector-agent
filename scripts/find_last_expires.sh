#!/bin/bash
../tools/find_last.sh "Expires:" logs/metasys-cloudconnector.log | ./enhance_date.sh
