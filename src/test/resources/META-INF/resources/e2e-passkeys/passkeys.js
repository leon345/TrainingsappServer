// Nur in den Testquellen (S-04, B4). Nutzt ausschliesslich die Browser-APIs, die auch die PWA nutzen wird:
// PublicKeyCredential.parseCreationOptionsFromJSON / parseRequestOptionsFromJSON und credential.toJSON().
'use strict';

async function post(pfad, body) {
    const antwort = await fetch(pfad, {
        method: 'POST',
        headers: body === undefined ? {} : {'Content-Type': 'application/json'},
        body: body === undefined ? undefined : JSON.stringify(body),
        credentials: 'same-origin'
    });
    const text = await antwort.text();
    return {status: antwort.status, text};
}

async function erzeugen(optionenJson) {
    const optionen = PublicKeyCredential.parseCreationOptionsFromJSON(optionenJson);
    try {
        const credential = await navigator.credentials.create({publicKey: optionen});
        return {credential: credential.toJSON()};
    } catch (e) {
        return {fehler: e.name, meldung: e.message};
    }
}

window.e2e = {
    async registrieren(benutzername) {
        const o = await post('/v1/registrieren/optionen', {benutzername});
        if (o.status !== 200) return {schritt: 'optionen', status: o.status, text: o.text};
        const optionen = JSON.parse(o.text);
        const ergebnis = await erzeugen(optionen);
        if (ergebnis.fehler) return {schritt: 'create', optionen, ...ergebnis};
        const r = await post('/v1/registrieren', {benutzername, credential: ergebnis.credential});
        return {schritt: 'abschluss', status: r.status, text: r.text, optionen, credential: ergebnis.credential};
    },

    async hinzufuegen() {
        const o = await post('/v1/passkeys/optionen');
        if (o.status !== 200) return {schritt: 'optionen', status: o.status, text: o.text};
        const optionen = JSON.parse(o.text);
        const ergebnis = await erzeugen(optionen);
        if (ergebnis.fehler) return {schritt: 'create', optionen, ...ergebnis};
        const r = await post('/v1/passkeys', ergebnis.credential);
        return {schritt: 'abschluss', status: r.status, text: r.text, optionen, credential: ergebnis.credential};
    },

    async anmelden() {
        const o = await post('/v1/anmelden/optionen');
        if (o.status !== 200) return {schritt: 'optionen', status: o.status, text: o.text};
        const optionen = JSON.parse(o.text);
        let credential;
        try {
            // kein Benutzername, keine allowCredentials: discoverable credential
            credential = (await navigator.credentials.get(
                {publicKey: PublicKeyCredential.parseRequestOptionsFromJSON(optionen)})).toJSON();
        } catch (e) {
            return {schritt: 'get', optionen, fehler: e.name, meldung: e.message};
        }
        const r = await post('/v1/anmelden', credential);
        return {schritt: 'abschluss', status: r.status, text: r.text, optionen, credential};
    },

    async liste() {
        const r = await fetch('/v1/passkeys', {credentials: 'same-origin'});
        return {status: r.status, text: await r.text()};
    },

    async umbenennen(id, bezeichnung) {
        const r = await fetch('/v1/passkeys/' + encodeURIComponent(id), {
            method: 'PATCH', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({bezeichnung}), credentials: 'same-origin'
        });
        return {status: r.status, text: await r.text()};
    },

    abmelden() {
        return post('/v1/abmelden');
    },

    cookiesFuerSkript() {
        return document.cookie;
    }
};
