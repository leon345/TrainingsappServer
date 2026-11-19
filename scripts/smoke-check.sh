#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || -z "${1:-}" ]]; then
    echo "Verwendung: $0 <BASIS_URL>" >&2
    echo "Beispiel:   $0 https://training.example.de" >&2
    exit 2
fi

BASIS_URL="${1%/}"
FEHLER=0

ok() {
    printf '[OK]   %s\n' "$1"
}

warn() {
    printf '[WARN] %s\n' "$1"
}

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    FEHLER=$((FEHLER + 1))
}

pruefe_header() {
    local header_datei="$1"
    local name="$2"
    local erwartet="$3"
    local gefunden
    gefunden="$(grep -i "^${name}:" "$header_datei" | tail -n 1 | cut -d':' -f2- | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    if [[ "$gefunden" == "$erwartet" ]]; then
        ok "Header ${name}: ${erwartet}"
    else
        fail "Header ${name} erwartet '${erwartet}', erhalten '${gefunden:-<fehlt>}'"
    fi
}

pruefe_401_json() {
    local pfad="$1"
    local header_datei body_datei status content_type
    header_datei="$(mktemp)"
    body_datei="$(mktemp)"
    trap 'rm -f "$header_datei" "$body_datei"' RETURN

    status="$(curl -sS -o "$body_datei" -D "$header_datei" -w '%{http_code}' "${BASIS_URL}${pfad}")"
    content_type="$(grep -i '^content-type:' "$header_datei" | tail -n 1 | tr -d '\r')"

    if [[ "$status" != "401" ]]; then
        fail "GET ${pfad}: erwartet HTTP 401, erhalten ${status}"
        return
    fi
    if ! grep -qi 'application/json' <<<"$content_type"; then
        fail "GET ${pfad}: erwartet Content-Type application/json, erhalten '${content_type:-<fehlt>}'"
        return
    fi
    if ! grep -q '"NICHT_ANGEMELDET"' "$body_datei"; then
        fail "GET ${pfad}: erwartet FehlerV1-Code NICHT_ANGEMELDET im Body"
        return
    fi
    ok "GET ${pfad} -> 401 FehlerV1-JSON (NICHT_ANGEMELDET)"
}

pruefe_404_gesperrt() {
    local pfad="$1"
    local header_datei body_datei status
    header_datei="$(mktemp)"
    body_datei="$(mktemp)"
    trap 'rm -f "$header_datei" "$body_datei"' RETURN

    status="$(curl -sS -o "$body_datei" -D "$header_datei" -w '%{http_code}' "${BASIS_URL}${pfad}")"
    if [[ "$status" != "404" ]]; then
        fail "GET ${pfad}: erwartet HTTP 404, erhalten ${status}"
        return
    fi
    if grep -qi '^set-cookie:' "$header_datei"; then
        fail "GET ${pfad}: darf keinen Set-Cookie-Header setzen"
        return
    fi
    ok "GET ${pfad} -> 404 (gesperrt, kein Set-Cookie)"
}

echo "=== Trainingsapp Smoke-Check: ${BASIS_URL} ==="

# 1. Security-Header auf /v1/info prüfen
INFO_HEADER="$(mktemp)"
trap 'rm -f "$INFO_HEADER"' EXIT
curl -sS -o /dev/null -D "$INFO_HEADER" "${BASIS_URL}/v1/info"

pruefe_header "$INFO_HEADER" "X-Content-Type-Options" "nosniff"
pruefe_header "$INFO_HEADER" "X-Frame-Options" "DENY"
pruefe_header "$INFO_HEADER" "Referrer-Policy" "no-referrer"
pruefe_header "$INFO_HEADER" "Content-Security-Policy" "default-src 'self'"

# Optionaler HSTS-Check bei HTTPS
if [[ "$BASIS_URL" == https://* ]]; then
    if grep -qi '^strict-transport-security:' "$INFO_HEADER"; then
        ok "HTTPS HSTS-Header (Strict-Transport-Security) vorhanden"
    else
        warn "HTTPS HSTS-Header (Strict-Transport-Security) nicht gesetzt (am Reverse Proxy empfohlen)"
    fi
fi

# 2. Geschützte /v1/*-Endpunkte & unbekannter Pfad -> 401 JSON (nie 302)
pruefe_401_json "/v1/info"
pruefe_401_json "/v1/workouts"
pruefe_401_json "/v1/unbekannt"

# 3. Interne /q/*-Pfade auf öffentlichem Port -> 404
pruefe_404_gesperrt "/q/openapi"
pruefe_404_gesperrt "/q/swagger-ui"
pruefe_404_gesperrt "/q/health"
pruefe_404_gesperrt "/q/webauthn/logout"

echo "=== Ergebnis ==="
if [[ "$FEHLER" -gt 0 ]]; then
    echo "Smoke-Check FEHLGESCHLAGEN (${FEHLER} Fehler)." >&2
    exit 1
fi
echo "Smoke-Check ERFOLGREICH (0 Fehler)."
