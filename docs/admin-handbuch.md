# Admin-Handbuch

Als Administrator verwalten Sie Benutzer, Stammdaten, die eigene Firma und die
Konfiguration. Die Admin-Bereiche sind nur für die Rolle **Admin** sichtbar.

## Benutzer

Menü **Benutzer**: Benutzer anlegen (E-Mail, Passwort, Rolle) und löschen.

- Rollen: **Benutzer** (Hochladen/Prüfen/Herunterladen) und **Admin**
  (zusätzlich Stammdaten, Eigene Firma, Benutzer, Einstellungen).
- E-Mail-Adressen sind eindeutig. Das eigene Konto und der letzte verbleibende
  Administrator können nicht gelöscht werden.

## Eigene Firma (Verkäufer)

Menü **Eigene Firma**: Name, Adresse, USt-IdNr, Steuernummer, Ansprechpartner,
E-Mail, Telefon, IBAN/BIC und Zahlungsbedingungen. Diese Daten werden automatisch
als Verkäufer und Zahlungsweg in jede Rechnung eingefüllt – so müssen Kontakt und
Bankverbindung nicht bei jeder Rechnung erneut erfasst werden.

Vorbelegung ist optional über die `.env` möglich (`COMPANY_*`); danach jederzeit
hier pflegbar.

## Empfänger-Stammdaten

Menü **Stammdaten**: je Empfänger Name, USt-IdNr, Kundennummer, Leitweg-ID,
Adresse, **E-Mail (elektronische Adresse, BT-49)** und Zahlungsbedingungen.

- Zuordnung beim Auslesen über die **USt-IdNr** des Käufers, sonst die
  **Kundennummer**. Passt ein Eintrag, werden fehlende Pflichtfelder automatisch
  ergänzt (Leitweg-ID, Adresse, E-Mail, …).
- Doppelte Einträge werden verhindert (Eindeutigkeit über USt-IdNr/Kundennummer).
- Bequem: Beim Prüfen kann der Käufer mit **Empfänger in Stammdaten speichern**
  direkt übernommen werden.

> Für die amtliche KoSIT-Prüfung ist die **elektronische Adresse des Käufers**
> (BT-49) Pflicht. Hinterlegen Sie hier eine E-Mail, damit die XRechnung des
> Empfängers gültig erzeugt werden kann.

## Konfiguration (.env)

Der gesamte Stack wird über eine einzige `.env` gesteuert (Vorlage:
`.env.example`). Änderungen wirken nach `docker compose up -d`. Wichtige Schlüssel:

| Schlüssel | Bedeutung |
|---|---|
| `WEBAPP_PORT` | nach außen exponierter Port |
| `MAX_UPLOAD_MB` | maximale Uploadgröße je Datei |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | initialer Admin (erster Start) |
| `JWT_SECRET` | Signatur der Anmelde-Token (lang & zufällig) |
| `ROUNDING_TOLERANCE` | Toleranz der Summenprüfung |
| `KOSIT_ENABLED` | zweite amtliche Validierung (KoSIT) an/aus |
| `WATCH_*` | Ordner-Automatik (siehe unten) |
| `BRAND_*` | White-Label (Name, Titel, Farbe) |
| `COMPANY_*` | optionale Vorbelegung der eigenen Firma |

Secrets gehören ausschließlich in die `.env` der Instanz, niemals ins Image/Repo.

## Ordner-Automatik (optional)

Aktivierung über `WATCH_ENABLED=true`. Der Eingangsordner
(`WATCH_INPUT_DIR`, Standard `/data/watch/in`) wird im Intervall `WATCH_INTERVAL`
(Sekunden) überwacht. Neue Dateien laufen exakt denselben Weg wie ein Upload.

- Nur **vollständig gültige** Rechnungen werden automatisch als XRechnung + Bericht
  in den Ausgangsordner (`WATCH_OUTPUT_DIR`) exportiert.
- Unsichere/unvollständige Rechnungen erscheinen im **Prüfbildschirm** zur
  manuellen Bearbeitung – kein Auto-Export.
- Verarbeitete Eingangsdateien werden nach `.done` (exportiert) bzw. `.error`
  (Prüfung nötig/fehlgeschlagen) verschoben. Identische Dateien werden per
  Inhalts-Hash nicht doppelt verarbeitet.

On-premise schreibt Abas direkt in den gemounteten Eingangsordner; in der Cloud
stellt die Instanz einen Übergabeordner bereit.

## Betrieb

Start/Stop, Backup/Restore, Health-Checks und Logs sind in
[`betrieb.md`](betrieb.md) beschrieben. Der Umsetzungsstand steht in
[`status.md`](status.md), das Datenmapping in [`mapping-abas.md`](mapping-abas.md).
