package de.trainingsapp.domain.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Fachliche Passkey-Entität ohne personenbezogene Telemetrie (keine IP, kein User-Agent, keine Attestierung).
 */
public final class Passkey {

    private final PasskeyId id;
    private final BenutzerId benutzerId;
    private final String credentialId;
    private final byte[] publicKey;
    private final long publicKeyAlgorithm;
    private final long signCount;
    private final UUID aaguid;
    private final PasskeyBezeichnung bezeichnung;
    private final LocalDate erstelltAm;
    private final LocalDate zuletztVerwendetAm;

    public Passkey(
            PasskeyId id,
            BenutzerId benutzerId,
            String credentialId,
            byte[] publicKey,
            long publicKeyAlgorithm,
            long signCount,
            UUID aaguid,
            PasskeyBezeichnung bezeichnung,
            LocalDate erstelltAm,
            LocalDate zuletztVerwendetAm
    ) {
        this.id = Objects.requireNonNull(id, "id darf nicht null sein");
        this.benutzerId = Objects.requireNonNull(benutzerId, "benutzerId darf nicht null sein");
        if (credentialId == null || credentialId.isBlank()) {
            throw new IllegalArgumentException("credentialId darf nicht leer sein");
        }
        this.credentialId = credentialId;
        if (publicKey == null || publicKey.length == 0) {
            throw new IllegalArgumentException("publicKey darf nicht leer sein");
        }
        this.publicKey = Arrays.copyOf(publicKey, publicKey.length);
        this.publicKeyAlgorithm = publicKeyAlgorithm;
        if (signCount < 0) {
            throw new IllegalArgumentException("signCount darf nicht negativ sein");
        }
        this.signCount = signCount;
        this.aaguid = Objects.requireNonNull(aaguid, "aaguid darf nicht null sein");
        this.bezeichnung = Objects.requireNonNull(bezeichnung, "bezeichnung darf nicht null sein");
        this.erstelltAm = Objects.requireNonNull(erstelltAm, "erstelltAm darf nicht null sein");
        this.zuletztVerwendetAm = zuletztVerwendetAm;
    }

    public PasskeyId id() {
        return id;
    }

    public BenutzerId benutzerId() {
        return benutzerId;
    }

    public String credentialId() {
        return credentialId;
    }

    public byte[] publicKey() {
        return Arrays.copyOf(publicKey, publicKey.length);
    }

    public long publicKeyAlgorithm() {
        return publicKeyAlgorithm;
    }

    public long signCount() {
        return signCount;
    }

    public UUID aaguid() {
        return aaguid;
    }

    public PasskeyBezeichnung bezeichnung() {
        return bezeichnung;
    }

    public LocalDate erstelltAm() {
        return erstelltAm;
    }

    public LocalDate zuletztVerwendetAm() {
        return zuletztVerwendetAm;
    }

    public Passkey umbenennen(PasskeyBezeichnung neueBezeichnung) {
        return new Passkey(
                id,
                benutzerId,
                credentialId,
                publicKey,
                publicKeyAlgorithm,
                signCount,
                aaguid,
                neueBezeichnung,
                erstelltAm,
                zuletztVerwendetAm
        );
    }

    /**
     * Prüft, ob Signaturzähler oder Verwendungsdatum (UTC-Tageswechsel) aktualisiert werden müssen.
     */
    public boolean brauchtVerwendungsAktualisierung(long neuerSignCount, LocalDate heuteUtc) {
        Objects.requireNonNull(heuteUtc, "heuteUtc darf nicht null sein");
        return neuerSignCount != this.signCount || !heuteUtc.equals(this.zuletztVerwendetAm);
    }

    public Passkey mitVerwendung(long neuerSignCount, LocalDate heuteUtc) {
        Objects.requireNonNull(heuteUtc, "heuteUtc darf nicht null sein");
        if (neuerSignCount < 0) {
            throw new IllegalArgumentException("neuerSignCount darf nicht negativ sein");
        }
        LocalDate neuesDatum = heuteUtc.equals(this.zuletztVerwendetAm) ? this.zuletztVerwendetAm : heuteUtc;
        return new Passkey(
                id,
                benutzerId,
                credentialId,
                publicKey,
                publicKeyAlgorithm,
                neuerSignCount,
                aaguid,
                bezeichnung,
                erstelltAm,
                neuesDatum
        );
    }
}
