#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then echo 'Create .env from .env.example first.' >&2; exit 1; fi
set -a
. ./.env
set +a
if [ -z "${JAVA_HOME:-}" ] && [ -d /Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ]; then
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
fi
cd backend
if [ "${1:-}" = test-fixtures ]; then
  exec ./mvnw -q spring-boot:test-run -Dspring-boot.run.main-class=com.kaizen.invoice.E2EApplication
else
  exec ./mvnw -q spring-boot:run
fi
