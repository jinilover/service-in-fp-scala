#!/bin/bash
set -e

# run application
echo "Running Application: $@"
exec "$@"

