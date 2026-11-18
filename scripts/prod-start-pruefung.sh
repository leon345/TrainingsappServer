#!/usr/bin/env bash
# Prueft den Start des gebauten Fast-JARs im Prod-Profil (S-04, B4):
#   (a) ohne TRAININGSAPP_*-Variablen, (b) leerer Sitzungsschluessel, (c) zu kurzer Schluessel,
#   (d) TRAININGSAPP_COOKIE_SECURE=false  -> Exit-Code ungleich 0 mit Meldung
#   (e) vollstaendig konfiguriert         -> Start; /v1/info 401 JSON, /q/openapi 404 ohne Body
# Voraussetzungen: zuvor "mvn package", Docker, curl. Alle Werte sind Wegwerf-Werte, die das Skript
# selbst erzeugt; es startet einen eigenen PostgreSQL-Container und beendet ihn per ID wieder.
# Aufruf aus dem Repo-Wurzelverzeichnis: scripts/prod-start-pruefung.sh
set -u
JAR=target/quarkus-app/quarkus-run.jar
PORT=${PORT:-18080}
PG_PORT=${PG_PORT:-55432}
[ -f "$JAR" ] || { echo "Fehlt: $JAR (zuerst mvn package)"; exit 2; }

PW=$(head -c 18 /dev/urandom | base64 | tr -dc a-zA-Z0-9)
SCHLUESSEL=$(head -c 36 /dev/urandom | base64 | tr -dc a-zA-Z0-9)
CID=$(docker run -d --rm -e POSTGRES_USER=wegwerf -e POSTGRES_PASSWORD="$PW" -e POSTGRES_DB=wegwerf \
  -p 127.0.0.1:$PG_PORT:5432 postgres:18) || exit 2
trap 'docker stop "$CID" >/dev/null' EXIT
until docker exec "$CID" pg_isready -U wegwerf -q; do sleep 1; done
sleep 2

DB=(QUARKUS_HTTP_PORT=$PORT QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://127.0.0.1:$PG_PORT/wegwerf
    QUARKUS_DATASOURCE_USERNAME=wegwerf QUARKUS_DATASOURCE_PASSWORD=$PW)
REST=(TRAININGSAPP_WEBAUTHN_ORIGINS=https://app.example.test TRAININGSAPP_WEBAUTHN_RP_ID=app.example.test
      TRAININGSAPP_TRUSTED_PROXIES=127.0.0.1)
FEHLER=0

start_muss_scheitern() {
  local fall=$1; shift
  local log; log=$(mktemp)
  env -i PATH="$PATH" HOME="$HOME" "${DB[@]}" "$@" timeout 60 java -jar "$JAR" >"$log" 2>&1
  local code=$?
  local grund; grund=$(grep -m1 -E "IllegalStateException|Failed to load config value" "$log" | sed 's/.*Exception: //')
  if [ "$code" -ne 0 ] && [ "$code" -ne 124 ] && [ -n "$grund" ]; then
    echo "OK   ($fall) Exit $code: $grund"
  else
    echo "FEHL ($fall) Exit $code, erwartet: Abbruch mit Meldung"; FEHLER=1
  fi
  rm -f "$log"
}

start_muss_scheitern a
start_muss_scheitern b "${REST[@]}" TRAININGSAPP_COOKIE_SECURE=true TRAININGSAPP_SITZUNG_SCHLUESSEL=
start_muss_scheitern c "${REST[@]}" TRAININGSAPP_COOKIE_SECURE=true TRAININGSAPP_SITZUNG_SCHLUESSEL=zu-kurz-123
start_muss_scheitern d "${REST[@]}" TRAININGSAPP_COOKIE_SECURE=false TRAININGSAPP_SITZUNG_SCHLUESSEL="$SCHLUESSEL"

LOG_E=$(mktemp)
env -i PATH="$PATH" HOME="$HOME" "${DB[@]}" "${REST[@]}" TRAININGSAPP_COOKIE_SECURE=true \
  TRAININGSAPP_SITZUNG_SCHLUESSEL="$SCHLUESSEL" java -jar "$JAR" >"$LOG_E" 2>&1 &
PID=$!
for _ in $(seq 1 60); do curl -s -o /dev/null "http://127.0.0.1:$PORT/v1/info" && break; sleep 1; done
INFO=$(curl -s -w ' %{http_code}' "http://127.0.0.1:$PORT/v1/info")
OPENAPI=$(curl -s -w '%{http_code}' "http://127.0.0.1:$PORT/q/openapi")
kill "$PID"; wait "$PID" 2>/dev/null
if [ "$INFO" = '{"code":"NICHT_ANGEMELDET","meldung":"Anmeldung erforderlich"} 401' ] && [ "$OPENAPI" = "404" ] \
   && ! grep -q "temporary key" "$LOG_E"; then
  echo "OK   (e) gestartet; /v1/info -> $INFO; /q/openapi -> $OPENAPI ohne Body; kein Zufallsschluessel"
else
  echo "FEHL (e) /v1/info -> $INFO; /q/openapi -> $OPENAPI"; FEHLER=1
fi
rm -f "$LOG_E"
exit $FEHLER
