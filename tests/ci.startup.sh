#!/bin/bash
source ./set-env.sh

# The integration-tests action passes the docker_compose_file of the workflow, which the docker-compose calls of
# @jahia/cypress do not read: they read COMPOSE_FILE (docker-compose.jexperience.yml on the jExperience runner).
export COMPOSE_FILE="${DOCKER_COMPOSE_FILE:-docker-compose.yml}"

version=$(node -p "require('./package.json').devDependencies['@jahia/cypress']")
echo Using @jahia/cypress@$version...
npx --yes --package @jahia/cypress@$version ci.startup
