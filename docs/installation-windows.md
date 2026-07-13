# Installation unter Windows (On-Premise)

Der Windows-Installer (`senapsa-erechnung-setup-<Version>.exe`) richtet den
kompletten Stack auf einem Windows-Rechner ein: Konfiguration (`.env`) mit
generierten Secrets, Watch-Ordner fuer die Ordner-Automatik, Docker-Images
(offline im Setup enthalten, kein Registry-Zugriff noetig) und
Startmenue-Verknuepfungen. Die Quellen des Installers liegen ausserhalb dieses
Repos (Distribution: `senapsa-bin/installer`).

## Voraussetzungen

- Windows 10/11 64-bit
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) mit
  WSL2-Backend, installiert und **gestartet**
- Der angemeldete Benutzer kann Docker verwenden (Administrator oder Mitglied
  der Gruppe `docker-users`)
- ca. 10 GB freier Speicherplatz (Images + Daten)

## Installation

1. Alle Setup-Dateien in einen Ordner legen (`.exe` und ggf. `-1.bin` usw. -
   das Setup ist wegen der enthaltenen Docker-Images auf mehrere Dateien
   aufgeteilt; es muessen immer alle vorhanden sein).
2. `senapsa-erechnung-setup-<Version>.exe` ausfuehren. Der Assistent fragt:
   - **Zielordner** der Installation
   - **Administrator-Konto**: E-Mail und Passwort fuer die erste Anmeldung an
     der Weboberflaeche (`ADMIN_EMAIL` / `ADMIN_PASSWORD`)
   - **Ordner-Automatik**: aktivieren ja/nein, Eingangs- und Ausgangsordner
     (Vorgabe `C:\abas\in` / `C:\abas\out`). Fehlende Ordner werden angelegt.
     Deaktiviert = interne Docker-Volumes, kein Host-Ordner noetig.
   - **Port** der Weboberflaeche (Vorgabe 8080)
3. Das Setup erzeugt die `.env` (Datenbank-, Redis- und JWT-Secrets werden
   zufaellig generiert), laedt die Docker-Images (`docker load`, dauert einige
   Minuten) und startet die Anwendung auf Wunsch direkt.
4. Anmeldung: `http://localhost:<Port>` mit dem Admin-Konto aus Schritt 2.

## Verknuepfungen (Startmenue)

- **senapsa eRechnung starten** - prueft Docker (startet Docker Desktop bei
  Bedarf), legt fehlende Watch-Ordner neu an, `docker compose up -d`, oeffnet
  den Browser.
- **senapsa eRechnung stoppen** - `docker compose down`; alle Daten bleiben
  erhalten.
- **Ordner-Einstellungen aendern** - grafischer Dialog fuer Ordner-Automatik
  und Watch-Ordner. Schreibt die Werte in die `.env` (Windows-Pfade werden
  automatisch in die Schreibweise mit Vorwaertsslashes ueberfuehrt, z.B.
  `C:/abas/in`), legt die Ordner an und startet laufende Container mit den
  neuen Bind-Mounts neu. Ersetzt das manuelle Bearbeiten der `.env` aus dem
  Admin-Handbuch (Abschnitt "Ordner-Automatik").

## Update

Einfach das Setup einer neueren Version ueber die bestehende Installation
ausfuehren:

- Die Konfigurationsseiten des Assistenten entfallen; die vorhandene `.env`
  (Secrets, Ordner, Port) bleibt vollstaendig erhalten. Nur `IMAGE_TAG` wird
  auf die neue Version gesetzt; neu hinzugekommene Konfigurationsschluessel
  werden mit ihren Defaults ergaenzt.
- Die neuen Images werden geladen; beim naechsten Start erstellt
  `docker compose up -d` die betroffenen Container neu.
- Alte Image-Versionen belegen weiter Speicherplatz - bei Bedarf aufraeumen:
  `docker image prune -a` (loescht alle ungenutzten Images).

## Deinstallation

"Apps und Features" -> senapsa eRechnung-Konverter. Der Deinstaller fragt:

- **Daten behalten** (Standard): Container werden gestoppt und entfernt,
  die Docker-Volumes (Datenbank, verarbeitete Rechnungen) und die `.env`
  bleiben fuer eine spaetere Neuinstallation erhalten.
- **Alles loeschen**: zusaetzlich werden Volumes, Images und die `.env`
  unwiderruflich entfernt. Die Watch-Ordner (z.B. `C:\abas\in`) und deren
  Inhalt werden nie geloescht.

## Backup

Wie im [Betriebshandbuch](betrieb.md) beschrieben (pg_dump ueber
`docker compose exec postgres ...`); die Befehle in einer PowerShell im
Installationsordner ausfuehren.

## Fehlerbehebung

- **"Docker Desktop wurde nicht gefunden oder laeuft nicht"** beim Setup:
  Docker Desktop starten, warten bis der Wal ruhig ist, "Wiederholen".
- **Einrichtung fehlgeschlagen**: Details stehen in `install.log` im
  Installationsordner.
- **Port belegt**: `WEBAPP_PORT` in der `.env` im Installationsordner aendern
  (Adminrechte noetig), danach "senapsa eRechnung starten".
- **Logs**: PowerShell im Installationsordner:
  `docker compose logs -f webapp`
