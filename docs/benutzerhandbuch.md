# Benutzerhandbuch

Der eRechnung-Konverter wandelt Ihre Abas-Rechnungen (PDF und/oder CSV) in eine
geprüfte, gültige **XRechnung** um. Drei Schritte: **Hochladen → Prüfen →
Herunterladen**. Es ist kein technisches Wissen nötig.

## Anmelden

Öffnen Sie die Anwendung im Browser und melden Sie sich mit Ihrer E-Mail und
Ihrem Passwort an. Ihre Zugangsdaten erhalten Sie von Ihrem Administrator.

## 1. Hochladen

1. Klicken Sie links auf **Hochladen**.
2. Ziehen Sie eine oder mehrere Dateien in die Fläche oder klicken Sie zum
   Auswählen. Erlaubt sind **PDF** und **CSV**.
3. Gehören PDF und CSV zusammen, benennen Sie sie gleich (z. B. `400009.pdf` und
   `400009.csv`) – sie werden automatisch zu einer Rechnung zusammengefasst. Liegt
   eine CSV vor, ist sie maßgeblich (strukturierte Abas-Daten); das PDF – bei
   Scans per Texterkennung (OCR) – füllt nur die Lücken der CSV (z. B. Menge,
   Einzelpreis, Beschreibung, Datum). Vorhandene CSV-Werte behalten immer Vorrang
   und werden nie durch OCR überschrieben; nur so gefüllte OCR-Felder bleiben zur
   Kontrolle gelb.
4. Klicken Sie auf **Hochladen & prüfen**.

Sie können auch mehrere Rechnungen auf einmal hochladen; für jede entsteht ein
eigener Eintrag.

## 2. Prüfen (Ampel)

Nach dem Auslesen öffnet sich der Prüfbildschirm: links das Original-PDF, rechts
die erkannten Daten. Jedes Feld hat eine Ampel:

- 🟢 **Grün** – sicher erkannt oder aus den Stammdaten übernommen.
- 🟡 **Gelb** – unsicher (z. B. per Texterkennung/OCR erkannt oder Abweichung
  zwischen PDF und CSV). Bitte prüfen und mit **✓** oder durch Klick bestätigen.
- 🔴 **Rot** – Pflichtfeld fehlt. Bitte ergänzen.

Fehlerhinweise erscheinen in Alltagssprache direkt am Feld (z. B. „Leitweg-ID
fehlt – bitte ergänzen"). Sie können jeden Wert direkt im Formular korrigieren.

Nützlich: Mit **Empfänger in Stammdaten speichern** übernehmen Sie den Käufer
einmalig in die Stammdaten – künftige Rechnungen desselben Kunden werden dann
automatisch ergänzt.

## 3. Prüfen & herunterladen

Die Aktionen stehen in der oben angehefteten Leiste; die Tasten sind je nach
Stand aktiv. Sind alle Pflichtfelder grün, klicken Sie auf **Prüfen**: Die
XRechnung wird erzeugt und amtlich geprüft. Nur wenn die Prüfung besteht, wird
sie freigegeben – so kann keine ungültige Rechnung exportiert werden.

Danach können Sie herunterladen:

- **⭳ XRechnung** – die XML-Datei (UBL) für die Einreichung.
- **⭳ ZUGFeRD** – ein Hybrid-PDF: Ihr Original-PDF mit der eingebetteten
  XRechnung. Nur verfügbar, wenn zur Rechnung ein Original-PDF vorliegt (bei
  reinen CSV-Rechnungen bleibt die Taste inaktiv).
- **Bericht** – der Validierungsbericht (menschenlesbar, mit XML-Download).

Möchten Sie eine bereits erzeugte Rechnung noch ändern, klicken Sie auf
**Daten ändern**; die erzeugte XML wird verworfen und nach der Korrektur neu
erstellt.

## Übersicht

Unter **Übersicht** sehen Sie alle Rechnungen mit Status und Ampel. Der Zähler in
der Navigationsleiste zeigt, wie viele Rechnungen noch zu prüfen sind. Rechnungen
lassen sich hier auch löschen.

## Häufige Meldungen

- **„… fehlt – bitte ergänzen"** – ein Pflichtfeld ist leer. Wert eintragen.
- **„… ist unsicher – bitte bestätigen"** – Wert prüfen und mit ✓ bestätigen.
- **„Netto + Steuer ergeben nicht den Bruttobetrag"** – Beträge stimmen rechnerisch
  nicht zusammen. Werte prüfen (der erwartete Wert steht im Hinweis).
- **„Diese Rechnung ist noch nicht validiert…"** – zuerst „Prüfen".
