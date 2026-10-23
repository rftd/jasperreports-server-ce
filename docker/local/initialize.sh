#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
export PGHOST="${OPENJRS_DB_HOST:-postgres}" PGUSER=postgres
export PGPASSWORD="${OPENJRS_DB_PASSWORD:?Set OPENJRS_DB_PASSWORD}"
export ks=/var/openjrs/keys ksp=/var/openjrs/keys
mkdir -p "$ks" /var/openjrs/config
exec 9>"$ks/.initialize.lock"
flock -n 9 || { echo 'Another initializer is running.' >&2; exit 1; }
cd /src/jasperserver/buildomatic
python3 /opt/openjrs-docker/configure.py
./js-ant refresh-config
exists=$(psql -XAt -d postgres -v ON_ERROR_STOP=1 -c "SELECT 1 FROM pg_database WHERE datname='jasperserver'")
if [[ "$exists" == 1 ]]; then
  [[ -s "$ks/.jrsks" && -s "$ksp/.jrsksp" ]] || {
    echo 'Existing repository is missing its encryption keys. Restore both volumes together.' >&2; exit 1;
  }
  marker=$(psql -XAt -d jasperserver -v ON_ERROR_STOP=1 -c "SELECT to_regclass('openjrs_local.bootstrap')")
  [[ -n "$marker" ]] || {
    echo 'Repository exists without a completed local initialization. No data was changed. See docker/local/README.md.' >&2; exit 1;
  }
  expected=$(psql -XAt -d jasperserver -v ON_ERROR_STOP=1 -c 'SELECT key_sha256 FROM openjrs_local.bootstrap WHERE id=1')
  actual=$(sha256sum "$ks/.jrsks" | cut -d' ' -f1)
  [[ "$expected" == "$actual" ]] || { echo 'Encryption keys do not match this database.' >&2; exit 1; }
  echo 'Existing repository and encryption keys verified; preserving all data.'
else
  if [[ -e "$ks/.jrsks" || -e "$ksp/.jrsksp" ]]; then
    [[ -s "$ks/.jrsks" && -s "$ksp/.jrsksp" ]] || { echo 'Incomplete encryption key volume.' >&2; exit 1; }
  else
    ./js-ant create-keystore
  fi
  # Deliberately do not use build-init-js-db-ce: that target drops the database.
  ./js-ant add-jdbc-driver create-js-db build-js-ddl-ce init-js-db-ce
  ./js-ant run-production-data-ce
  ./js-ant create-sugarcrm-db load-sugarcrm-db create-foodmart-db load-foodmart-db
  ./js-ant import-sample-data-ce
  fingerprint=$(sha256sum "$ks/.jrsks" | cut -d' ' -f1)
  psql -X -d jasperserver -v ON_ERROR_STOP=1 -v fingerprint="$fingerprint" <<'SQL'
CREATE SCHEMA openjrs_local;
CREATE TABLE openjrs_local.bootstrap (id integer PRIMARY KEY CHECK (id=1), key_sha256 text NOT NULL);
INSERT INTO openjrs_local.bootstrap VALUES (1, :'fingerprint');
SQL
fi
python3 /opt/openjrs-docker/configure.py export
chown -R 10001:10001 /var/openjrs/keys /var/openjrs/config
echo 'OpenJRS local initialization completed.'
