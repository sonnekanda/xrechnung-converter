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
`.example.env`). Änderungen wirken nach `docker compose up -d`. Wichtige Schlüssel:

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

Im Admin-Menü **Ordner-Automatik** ohne Neustart konfigurierbar:

- **Automatik aktiv** – Ein-/Ausschalter.
- **Ordner** (Eingang/Ausgang) – der Host-Ordner wird bei der Installation
  festgelegt und in der Oberfläche read-only angezeigt (z. B. `C:\abas\in`).
  Optional lässt sich ein **Unterordner** ergänzen (im Container/Host darunter).
  Änderung des Host-Ordners = `.env` anpassen + Neustart (übernimmt später der
  Installer/Launcher). On-premise wird der Host-Ordner über
  `WATCH_HOST_INPUT_DIR`/`WATCH_HOST_OUTPUT_DIR` in der `.env` eingebunden
  (Windows-Pfade mit Vorwärtsslashes, z. B. `C:/abas/in`); es gibt **nur ein**
  Compose-File, der Mount-Typ ergibt sich aus dem Wert. Leer lassen (Cloud/SFTP)
  = interne Docker-Volumes. Vor dem Start `sh scripts/preflight-check.sh`
  ausführen, damit fehlende Host-Ordner sofort auffallen statt still angelegt zu
  werden. Start dann wie gewohnt: `docker compose up -d`.
- **Prüfintervall** – wie oft der Ordner geprüft wird.
- **Settle-Zeit** – Wartezeit, bis eine neue Datei als „stabil" gilt. So kommen
  zusammengehörige PDF/CSV-Dateien an, bevor verarbeitet wird (kein Zerlegen des
  Paares in zwei Rechnungen).
- **Automatisch erzeugen** – Zielformat des Auto-Exports: **XRechnung (XML)**,
  **ZUGFeRD (PDF)** oder **Beide**. ZUGFeRD benötigt ein Original-PDF; liegt zu
  einer Rechnung nur eine CSV vor, wird ersatzweise die XRechnung-XML geschrieben.
- **Gelbe Felder automatisch bestätigen** – für echten Auto-Export. **OCR-Werte
  bleiben ausgenommen** und prüfpflichtig; Scan-Rechnungen werden daher nicht
  automatisch exportiert.

Die `.env`-Werte `WATCH_ENABLED`/`WATCH_INTERVAL` dienen nur als Startvorgabe;
maßgeblich sind danach die Einstellungen im Admin-Bereich. Neue Dateien laufen
exakt denselben Weg wie ein Upload.

- Nur **vollständig gültige** Rechnungen werden automatisch im gewählten Format
  (XRechnung-XML + Bericht und/oder ZUGFeRD-PDF) in den Ausgangsordner
  (`WATCH_OUTPUT_DIR`) exportiert.
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
