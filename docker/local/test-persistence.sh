#!/usr/bin/env bash
# Integration check against this Compose project's disposable database only.
set -Eeuo pipefail
query() { docker compose exec -T postgres psql -XAt -U postgres -d jasperserver -v ON_ERROR_STOP=1 -c "$1"; }
before=$(query 'SELECT key_sha256 FROM openjrs_local.bootstrap WHERE id=1')
query 'CREATE TABLE openjrs_local.persistence_probe (value text); INSERT INTO openjrs_local.persistence_probe VALUES ($$preserved$$);'
docker compose run --rm initialize
[[ "$(query 'SELECT value FROM openjrs_local.persistence_probe')" == preserved ]]
[[ "$(query 'SELECT key_sha256 FROM openjrs_local.bootstrap WHERE id=1')" == "$before" ]]

# A lost key volume must fail, rather than generate replacement keys for data
# that was encrypted with a different keystore.
empty_keys=$(docker volume create)
trap 'docker volume rm "$empty_keys" >/dev/null' EXIT
if docker compose run --rm -v "$empty_keys:/var/openjrs/keys" initialize; then
  echo 'Initializer incorrectly accepted an existing repository without its keys.' >&2
  exit 1
fi
[[ "$(query 'SELECT value FROM openjrs_local.persistence_probe')" == preserved ]]
[[ "$(query 'SELECT key_sha256 FROM openjrs_local.bootstrap WHERE id=1')" == "$before" ]]
query 'DROP TABLE openjrs_local.persistence_probe'
docker compose restart openjrs
echo 'Persistence, repeated initialization and missing-key protection PASSED.'
