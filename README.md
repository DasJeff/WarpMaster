# WarpMaster

Ein flexibles Warp-Plugin mit optionalem Webinterface, API und Datenbankunterstützung.

**Copyright (c) 2025 DasJeff** - Siehe [LICENSE](LICENSE) für Nutzungsbedingungen.

## Inhaltsverzeichnis

*   [Features](#features)
*   [Installation](#installation)
*   [Datenbank](#datenbank)
*   [Konfiguration](#konfiguration)
*   [Befehle](#befehle)
*   [Berechtigungen](#berechtigungen)
*   [Webinterface & API (Optional)](#webinterface--api-optional)
    *   [Webinterface](#webinterface)
    *   [API](#api)
        *   [Authentifizierung & Sicherheit](#authentifizierung--sicherheit)
        *   [Endpunkte](#endpunkte)
*   [Lizenz](#lizenz)
*   [Support & Probleme](#support--probleme)

## Features

*   **Einfaches Warp-Management:**
    *   Setze persönliche Warps an deiner aktuellen Position (`/setwarp <Name>`).
    *   Teleportiere dich zu deinen gesetzten Warps (`/warp <Name>`).
    *   Liste alle deine verfügbaren Warps auf (`/warps`).
*   **Admin-Funktionen:**
    *   Lösche Warps von Spielern (`/warpmaster delete <Spieler> <WarpName>`). **Hinweis:** Normale Spieler können ihre eigenen Warps *nicht* löschen!
    *   Setze individuelle Warp-Limits für Spieler (`/warpmaster limit <Spieler> <Limit>`).
    *   Übertrage Warps von einem Spieler zu einem anderen (`/warpmaster transfer <VonSpieler> <WarpName> <ZuSpieler>`).
    *   Teleportiere dich zu Warps anderer Spieler (`/warp <WarpName> <SpielerName>`).
    *   Lade die Plugin-Konfiguration neu (`/warpmaster reload`).
*   **Konfigurierbarkeit:**
    *   **Warp-Limit:** Lege ein Standard-Warp-Limit pro Spieler fest.
    *   **Cooldown:** Definiere eine Abklingzeit zwischen Warp-Teleportationen.
    *   **Datenbank:** Wähle zwischen MySQL und SQLite zur Speicherung der Warp-Daten.
    *   **Nachrichten:** Passe alle Plugin-Nachrichten an (mit Farbcode-Unterstützung).
*   **Webinterface & API (Optional):**
    *   Aktiviere ein integriertes Webinterface und eine HTTP-API für die Verwaltung.
    *   Sichere den Zugriff mit einem API-Schlüssel und IP-Whitelist.
    *   Rate-Limiting zum Schutz vor Missbrauch.
*   **Asynchrone Datenbankoperationen:** Verhindert Server-Lags bei Datenbankzugriffen.
*   **Tab-Completion:** Sinnvolle Autovervollständigung für Befehle und Argumente.

## Installation

1.  **Voraussetzungen:**
    *   Java 21 oder höher.
    *   Minecraft Server Software, die die Bukkit API unterstützt (z. B. Spigot, Paper) in Version 1.21 oder höher (siehe `plugin.yml` für `api-version`).
2.  **Download:** Lade die neueste `WarpMaster-X.Y.Z.jar`-Datei von der [GitHub Releases Seite](https://github.com/DasJeff/WarpMaster/releases) herunter.
3.  **Platzieren:** Kopiere die heruntergeladene `.jar`-Datei in den `plugins`-Ordner deines Minecraft-Servers.
4.  **Server starten:** Starte deinen Minecraft-Server. Das Plugin erstellt beim ersten Start die Konfigurationsdatei (`config.yml`).
5.  **Konfigurieren:** Stoppe den Server und bearbeite die `plugins/WarpMaster/config.yml` nach deinen Wünschen (siehe Abschnitt [Konfiguration](#konfiguration)). Achte besonders auf die Datenbankeinstellungen und den API-Schlüssel!
6.  **Server neu starten:** Starte den Server erneut, um die Konfiguration zu laden.

## Datenbank

WarpMaster benötigt eine Datenbank, um Warp- und Spielerdaten zu speichern. Du kannst zwischen MySQL und SQLite wählen.

*   **MySQL (Standard nach erster Installation):**
    *   Benötigt einen laufenden MySQL-Server.
    *   **Empfohlen für größere Server oder wenn Daten zentral verwaltet werden sollen.**
    *   Du musst die Zugangsdaten (Host, Port, Datenbankname, Benutzer, Passwort) in der `config.yml` korrekt eintragen.
    *   Das Plugin versucht, die benötigten Tabellen (`warps`, `player_data`) automatisch zu erstellen. Der angegebene Datenbankbenutzer benötigt dafür ausreichende Rechte (mindestens `CREATE TABLE`, `INSERT`, `SELECT`, `UPDATE`, `DELETE`, `CREATE INDEX`).

*   **SQLite:**
    *   Einfachste Option, keine externe Datenbank erforderlich.
    *   Die Datenbank wird automatisch in der Datei `plugins/WarpMaster/database.db` erstellt.
    *   **Empfohlen für kleinere Server oder Testumgebungen.**
    *   **Wichtig:** Bei SQLite wird die Pool-Größe intern immer auf 1 gesetzt, um Konflikte zu vermeiden, unabhängig vom Wert in der `config.yml`.

**Wichtiger Hinweis:** Wenn du mit der Standard-MySQL-Konfiguration startest (`host: localhost`, `username: root`, `password: password`), wird das Plugin **nicht aktiviert** und gibt eine Fehlermeldung aus. Du musst die Konfiguration anpassen oder auf SQLite wechseln.

## Konfiguration

Die Hauptkonfiguration erfolgt über die `plugins/WarpMaster/config.yml`. Nach Änderungen ist ein Serverneustart oder der Befehl `/warpmaster reload` (mit entsprechender Berechtigung) erforderlich.

```yaml
# WarpMaster Konfiguration

# Datenbank Konfiguration
database:
  type: mysql # mysql oder sqlite
  host: localhost # Hostname der Datenbank  
  port: 3306 # Port der Datenbank
  database: warpmaster # Name der Datenbank
  username: root # Username der Datenbank
  password: password # Unbedingt ändern!
  pool-size: 10 # Anzahl der Verbindungen zur Datenbank
  connection-timeout: 30000 # Timeout für Verbindungen
  idle-timeout: 600000 # Timeout für inaktive Verbindungen
  max-lifetime: 1800000 # Maximale Lebensdauer der Verbindungen

# Warp Konfiguration
warps:
  default-limit: 5 # Standard-Warp-Limit pro Spieler
  cooldown: 3 # Abklingzeit in Sekunden zwischen Warp-Teleportationen

# API Konfiguration
api:
  enabled: true # API aktivieren/deaktivieren
  port: 8080 # Port der API
  host: 0.0.0.0 # Auf allen Schnittstellen lauschen
  security:
    api-key: "bitte-aendern-zu-einem-sicheren-schluessel" # API-Schlüssel zur Authentifizierung
    ip-whitelist: [] # Liste der IPs, die auf die API zugreifen dürfen (leer = alle IPs erlaubt)
      # Beispiel:
      # - "192.168.1.100"
      # - "10.0.0.0/8"
    rate-limit:
      enabled: true # Rate-Limiting aktivieren/deaktivieren
      requests-per-minute: 60 # Maximale Anfragen pro Minute pro IP

# Nachrichten
messages:
  prefix: "&8[&bWarpMaster&8] &7"
  warp-set: "&aWarp &e%name% &awurde gesetzt!"
  warp-deleted: "&cWarp &e%name% &cwurde gelöscht!"
  warp-limit-reached: "&cDu hast dein Warp-Limit von &e%limit% &cerreicht!"
  warp-not-found: "&cWarp &e%name% &cnicht gefunden!"
  warp-teleported: "&aTeleportiert zu &e%name%&a!"
  warp-already-exists: "&cDu hast bereits einen Warp mit dem Namen &e%name%&c!"
  no-permission: "&cDu hast keine Berechtigung dafür!"
  player-not-found: "&cSpieler &e%player% &cnicht gefunden!"
  cooldown-active: "&cBitte warte &e%time% &cSekunden, bevor du dich erneut warpst!"
```

## Befehle

| Befehl                                    | Beschreibung                                                | Verwendung                             | Berechtigung                 |
| :---------------------------------------- | :---------------------------------------------------------- | :------------------------------------- | :--------------------------- |
| `/setwarp <Name>`                         | Setzt einen Warp an deiner aktuellen Position.              | `/setwarp MeinZuhause`                 | `warpmaster.warp.set`        |
| `/warp <Name>`                            | Teleportiert dich zu deinem Warp.                           | `/warp MeinZuhause`                    | `warpmaster.warp.use`        |
| `/warp <WarpName> <SpielerName>`          | Teleportiert dich zum Warp eines anderen Spielers.          | `/warp Home Notch`                     | `warpmaster.admin`           |
| `/warps`                                  | Öffnet ein GUI / Listet alle deine gesetzten Warps auf.       | `/warps`                               | `warpmaster.warp.list`       |
| `/warps <SpielerName>`                    | Öffnet das Warp-GUI für einen anderen Spieler.            | `/warps Notch`                         | `warpmaster.admin`           |
| `/warpmaster reload`                      | Lädt die `config.yml` neu und leert interne Caches.        | `/warpmaster reload`                   | `warpmaster.admin`           |
| `/warpmaster delete <Spieler> <WarpName>` | Löscht einen Warp eines Spielers. (Admin-Befehl)            | `/warpmaster delete Notch Home`        | `warpmaster.admin.delete`    |
| `/warpmaster limit <Spieler> <Anzahl>`    | Setzt das Warp-Limit für einen Spieler. (Admin-Befehl)      | `/warpmaster limit Steve 10`           | `warpmaster.admin.limit`     |
| `/warpmaster transfer <Von> <Warp> <Zu>` | Überträgt einen Warp. (Admin-Befehl)                      | `/warpmaster transfer Alex Mine Bob` | `warpmaster.admin.transfer`  |

**Wichtiger Hinweis:** Es gibt **keinen** Befehl für normale Spieler, um ihre eigenen Warps zu löschen. Dies muss aktuell ein Admin über `/warpmaster delete <SpielerName> <WarpName>` tun.

## Berechtigungen

| Berechtigung                | Beschreibung                                                                 | Standard | Definiert in `plugin.yml`? |
| :-------------------------- | :--------------------------------------------------------------------------- | :------- | :------------------------- |
| `warpmaster.warp.set`       | Erlaubt das Setzen von Warps mit `/setwarp`.                                 | `true`   | Ja                         |
| `warpmaster.warp.use`       | Erlaubt die Teleportation zu eigenen Warps mit `/warp <Name>`.               | `true`   | Ja                         |
| `warpmaster.warp.list`      | Erlaubt das Auflisten/Öffnen des eigenen Warp-GUI mit `/warps`.              | `true`   | Ja                         |
| `warpmaster.admin`          | Hauptberechtigung für Admin-Befehle und Aktionen auf andere Spieler.        | `op`     | Ja                         |
| `warpmaster.admin.delete`   | Erlaubt das Löschen von Warps (`/warpmaster delete`).                        | `op`     | Ja (als Kind von admin)    |
| `warpmaster.admin.limit`    | Erlaubt das Setzen von Warp-Limits (`/warpmaster limit`).                    | `op`     | Ja (als Kind von admin)    |
| `warpmaster.admin.transfer` | Erlaubt das Übertragen von Warps (`/warpmaster transfer`).                   | `op`     | Ja (als Kind von admin)    |

**Hinweis:** Die Berechtigung `warpmaster.admin` wird zusätzlich benötigt für:
*   `/warp <WarpName> <SpielerName>` (Teleport zum Warp eines anderen Spielers)
*   `/warps <SpielerName>` (Anzeigen der Warps eines anderen Spielers)

## Webinterface & API (Optional)

Wenn in der `config.yml` aktiviert (`api.enabled: true`), startet WarpMaster einen kleinen Webserver, der ein **Webinterface** und eine **HTTP-API** bereitstellt. Zugriff erfolgt über `http://<ServerIP>:<api.port>` (z.B. `http://localhost:8080`).

### Webinterface

*   Bietet eine grafische Oberfläche (`index.html`) zur Verwaltung von Spielern und deren Warps.
*   Zeigt eine Liste von Spielern, die Warps besitzen.
*   Ermöglicht das Durchsuchen von Spielern.
*   Zeigt Details zu einem ausgewählten Spieler an (UUID, Warp-Anzahl/-Limit).
*   Listet die Warps des Spielers mit Details (Welt, Koordinaten, Erstellungsdatum).
*   Ermöglicht das Ändern des Warp-Limits für den Spieler.
*   Ermöglicht das Löschen einzelner Warps des Spielers.
*   **Login:** Der Zugriff erfolgt über `login.html`, wo der in `api.security.api-key` festgelegte Schlüssel eingegeben werden muss. Dieser Schlüssel wird dann für die API-Anfragen des Webinterfaces verwendet.

### API

Ermöglicht externen Skripten/Anwendungen die Interaktion mit WarpMaster-Daten.

#### Authentifizierung & Sicherheit

*   **API-Schlüssel:** Jede API-Anfrage (an Pfade unter `/api/`) benötigt einen `X-API-Key` HTTP-Header mit dem korrekten, in `config.yml` festgelegten `api.security.api-key`.
*   **IP-Whitelist:** Wenn `api.security.ip-whitelist` in der `config.yml` nicht leer ist, sind nur Anfragen von den dort gelisteten IPs (oder IP-Bereichen im CIDR-Format, z.B. `192.168.1.0/24`) erlaubt.
*   **Rate Limiting:** Wenn `api.security.rate-limit.enabled: true`, wird die Anzahl der Anfragen pro IP auf den Wert in `requests-per-minute` begrenzt (Standard: 60).

#### Endpunkte

Alle API-Endpunkte befinden sich unter dem Pfad `/api`. Sie erwarten und liefern Daten im JSON-Format.

*   **`GET /api/players`**
    *   **Beschreibung:** Ruft eine Liste aller Spieler ab, die mindestens einen Warp besitzen.
    *   **Response:** `200 OK`
      ```json
      [
        { "uuid": "player-uuid-1", "name": "SpielerName1" },
        { "uuid": "player-uuid-2", "name": "SpielerName2" },
        ...
      ]
      ```

*   **`GET /api/player/{uuid}`**
    *   **Beschreibung:** Ruft Detailinformationen zu einem spezifischen Spieler ab.
    *   **Path Parameter:** `{uuid}` - Die UUID des Spielers.
    *   **Response:** `200 OK`
      ```json
      {
        "uuid": "player-uuid",
        "name": "SpielerName",
        "warpLimit": 10,
        "warpCount": 3
      }
      ```
    *   **Fehler:** `400 Bad Request` (Ungültige UUID), `500 Internal Server Error`.

*   **`GET /api/warps/{uuid}`**
    *   **Beschreibung:** Ruft alle Warps eines spezifischen Spielers ab.
    *   **Path Parameter:** `{uuid}` - Die UUID des Spielers.
    *   **Response:** `200 OK`
      ```json
      [
        {
          "id": 1,
          "ownerUuid": "player-uuid",
          "name": "WarpName1",
          "worldName": "world",
          "x": 100.5,
          "y": 64.0,
          "z": -200.7,
          "yaw": 90.0,
          "pitch": 0.0,
          "createdAt": 1678886400000
        },
        ...
      ]
      ```
    *   **Fehler:** `400 Bad Request` (Ungültige UUID), `500 Internal Server Error`.

*   **`POST /api/warp`**
    *   **Beschreibung:** Erstellt einen neuen Warp für einen Spieler. Prüft das Warp-Limit des Spielers.
    *   **Request Body (JSON):**
      ```json
      {
        "uuid": "player-uuid",
        "name": "NeuerWarpName",
        "worldName": "world",
        "x": 10.0,
        "y": 70.0,
        "z": 15.0,
        "yaw": 180.0,
        "pitch": 5.0
      }
      ```
    *   **Response:** `201 Created` - Enthält das erstellte Warp-Objekt (inkl. `id` und `createdAt`)
      ```json
      // (Struktur wie in GET /api/warps/{uuid})
      ```
    *   **Fehler:** `400 Bad Request` (Fehlende Felder, ungültige UUID/WarpName, Warp-Limit erreicht, Warp existiert bereits), `500 Internal Server Error`.

*   **`DELETE /api/warp/{uuid}/{name}`**
    *   **Beschreibung:** Löscht einen spezifischen Warp eines Spielers.
    *   **Path Parameter:** `{uuid}` - UUID des Spielers, `{name}` - Name des Warps.
    *   **Response:** `204 No Content` (Erfolgreich gelöscht).
    *   **Fehler:** `400 Bad Request` (Ungültige UUID/WarpName), `404 Not Found` (Warp nicht gefunden), `500 Internal Server Error`.

*   **`GET /api/player/{uuid}/limit`**
    *   **Beschreibung:** Ruft das aktuelle Warp-Limit eines Spielers ab.
    *   **Path Parameter:** `{uuid}` - UUID des Spielers.
    *   **Response:** `200 OK`
      ```json
      { "uuid": "player-uuid", "limit": 10 }
      ```
    *   **Fehler:** `400 Bad Request` (Ungültige UUID), `500 Internal Server Error`.

*   **`PUT /api/player/{uuid}/limit`**
    *   **Beschreibung:** Setzt das Warp-Limit für einen Spieler.
    *   **Path Parameter:** `{uuid}` - UUID des Spielers.
    *   **Request Body (JSON):**
      ```json
      { "limit": 15 }
      ```
    *   **Response:** `200 OK` - Enthält die aktualisierten Daten.
      ```json
      { "uuid": "player-uuid", "limit": 15 }
      ```
    *   **Fehler:** `400 Bad Request` (Ungültige UUID, ungültiges Limit/Body), `500 Internal Server Error`.

## Lizenz

**ACHTUNG:** Diese Software wird unter einer **benutzerdefinierten proprietären Lizenz** bereitgestellt. Die Nutzung ist **ausschließlich auf private, nicht-öffentliche Testzwecke beschränkt.**

Folgendes ist **verboten**:
*   Nutzung auf öffentlichen Servern.
*   Kommerzielle Nutzung.
*   Modifikation, Dekompilierung oder Weitergabe des Codes oder der kompilierten Software.

Bitte lies die vollständigen Lizenzbedingungen in der [LICENSE](LICENSE)-Datei.

## Support & Probleme

Wenn du Fehler findest, Vorschläge hast oder Fragen zur eingeschränkten Nutzung hast, erstelle bitte ein [Issue](https://github.com/DasJeff/WarpMaster/issues) auf GitHub. 