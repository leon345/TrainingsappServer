# Lokaler Prototyp: Server im Dev-Modus, Client im Vite-Dev-Server

## Voraussetzungen

- Java 21
- Maven (kein Wrapper im Repo)
- Docker (oder eine andere Container-Laufzeit): Quarkus Dev Services startet damit die
  Dev-Datenbank (PostgreSQL, Image `postgres:18`). Ohne Container-Laufzeit startet der
  Dev-Modus nicht (Alternative mit lokaler PostgreSQL: `docs/persistenz.md`).

## Server starten

```bash
mvn quarkus:dev
```

Der Server laeuft dann unter `http://localhost:8080`, die API unter `/v1`.

## Client

Der Client wird unter `http://localhost:5173` (Vite-Dev-Server) erwartet und leitet `/v1`
per Proxy an `http://localhost:8080` weiter. Fuer den Browser ist die Origin damit
`http://localhost:5173`. Deshalb erlaubt das Dev-Profil beide Origins:

```properties
%dev.quarkus.webauthn.origins=http://localhost:8080,http://localhost:5173
```

Die rpId bleibt `localhost`. Andere Origins lehnt der Server mit 403 `ORIGIN_UNGUELTIG` ab.
Test- und Prod-Profil sind davon nicht betroffen.

## Dev-Datenbank ist fluechtig

Dev Services startet die Datenbank in einem eigenen Container mit anonymem Volume. Beim
Beenden des Dev-Modus werden Container und Volume entfernt. Nach einem Neustart ist die
Datenbank leer, Flyway legt das Schema neu an: Konten, Passkeys und ausstehende
Registrierungen sind weg. Passkeys, die im Browser bzw. Authenticator gespeichert wurden,
bleiben dort zwar bestehen, gehoeren aber zu keinem Konto mehr und muessen neu registriert
werden.

## Passkeys nur unter `localhost`

WebAuthn verlangt, dass die rpId zur Domain der Seite passt; unter einer IP-Adresse
(z. B. `http://127.0.0.1:5173` oder eine LAN-Adresse) funktionieren Passkeys nicht.
Den Client deshalb immer ueber `http://localhost:5173` aufrufen.
