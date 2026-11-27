# Movy für Cloudstream

Cloudstream-Erweiterung für den deutschen Katalog von [Movy](https://www.movy.sx/de) mit Startseite, Suche, Filmdetails und Staffeln samt Episoden. Die Erweiterung öffnet den Movy-Player in Android WebView und übergibt die gefundene HLS- oder DASH-Quelle an Cloudstream. Die verfügbaren Tonspuren hängen von der Quelle ab.

## Lokal in Cloudstream testen

Kopiere `Movy.cs3` in den Ordner `Cloudstream3/plugins/` im gemeinsamen Android-Speicher. Erlaube Cloudstream den Zugriff auf diesen Speicher, falls Android danach fragt. Starte die App neu und wähle **Movy** als Quelle.

## Als GitHub-Repository installieren

1. Lade dieses Git-Repository in ein GitHub-Repository mit dem Standardbranch `main` oder `master`.
2. Der Workflow **Cloudstream-Erweiterung bauen** legt den Branch `builds` an und veröffentlicht dort `Movy.cs3`, `plugins.json` und `repo.json`. Der Workflow benötigt Schreibzugriff auf diesen Branch.
3. Füge in Cloudstream unter **Einstellungen → Erweiterungen → Repository hinzufügen** die folgende URL mit deinem GitHub-Inhaber und Repository-Namen hinzu. Installiere anschließend **Movy**.

   ```text
   https://raw.githubusercontent.com/OWNER/REPO/builds/repo.json
   ```

Die Dateien im Branch `builds` müssen für Cloudstream per HTTP abrufbar sein. Bei einem öffentlichen GitHub-Repository ist die Raw-URL direkt verwendbar.

Falls Cloudstream die GitHub-Raw-URL nicht laden kann, verwende den jsDelivr-Link. Bei diesem Link werden die Pluginliste und das Plugin ebenfalls über jsDelivr geladen.

```text
https://cdn.jsdelivr.net/gh/OWNER/REPO@builds/repo-cdn.json
```

## Selbst bauen

Voraussetzungen sind JDK 17 oder 21, Android SDK 35 und Build Tools 34.0.0. Setze `ANDROID_HOME` auf den Pfad zum Android SDK und führe Folgendes aus.

```sh
./gradlew make makePluginsJson makeRepositoryJson
```

Unter Windows lautet der Aufruf `gradlew.bat make makePluginsJson makeRepositoryJson`.

| Datei | Inhalt |
| --- | --- |
| `Movy/build/Movy.cs3` | Installierbare Android-Erweiterung |
| `build/plugins.json` | Liste der Erweiterungen |
| `build/repo.json` | Einstiegsdatei für den Repository-Import |

Für eigenes HTTP-Hosting setze `MOVY_REPO_BASE_URL` beim Build auf die Basis-URL des Zielverzeichnisses und lade alle drei Dateien in dieses Verzeichnis. Importiere dann die URL zu `repo.json` in Cloudstream.

[Cloudstream-Entwicklerdokumentation](https://recloudstream.github.io/csdocs/devs/gettingstarted/)
