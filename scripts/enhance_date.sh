#!/bin/bash
awk '{print "\033[1;34m" substr($0, 1, 20) "\033[0m" substr($0, 21)}'

