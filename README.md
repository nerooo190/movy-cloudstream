# Movy für Cloudstream

Cloudstream-Erweiterung für den deutschen Katalog von [Movy](https://www.movy.sx/de) mit Startseite, Suche, Filmdetails und Staffeln samt Episoden. Die Erweiterung öffnet den Movy-Player in Android WebView und übergibt die gefundene HLS- oder DASH-Quelle an Cloudstream. Die verfügbaren Tonspuren hängen von der Quelle ab.

## Cloudstream und Stremio über einen Server nutzen

Der Server in `server/` bietet ein Stremio-Addon für die vorhandenen Film- und Serienkataloge. Cloudstream verwendet ab Movy v3 denselben Resolver, wenn du seine Adresse in den Plugin-Einstellungen einträgst.

### Gehostete Einrichtung

1. Öffne [Movy auf Render bereitstellen](https://render.com/deploy?repo=https%3A%2F%2Fgithub.com%2Fnerooo190%2Fmovy-cloudstream) und bestätige den Build in deinem Render-Konto. Das Blueprint verwendet den Free-Tarif.
2. Öffne im Render-Dashboard den bereitgestellten Web Service und kopiere die HTTPS-Adresse aus **Overview**. Kopiere auch den Wert von `MOVY_ACCESS_TOKEN` aus **Environment**. Ersetze im Token `+` durch `-` und `/` durch `_`. Entferne `=` am Ende. Setze dann die URL für beide Apps zusammen.

   ```text
   https://DEIN-DIENST.onrender.com/DEIN-TOKEN/manifest.json
   ```

3. **Stremio.** Füge die Manifest-URL unter **Add-ons** ein und installiere **Movy**. Öffne anschließend einen Film oder eine Folge in der Stremio-Bibliothek.
4. **Cloudstream.** Installiere **Movy v3**. Tippe in der Erweiterungsliste auf **Movy → Einstellungen**, füge dieselbe Manifest-URL ein und speichere.

Die Manifest-URL enthält deinen Zugriffstoken. Der Free-Tarif pausiert den Server bei Inaktivität. Wenn ein Aufruf deshalb abbricht, öffne die Serveradresse im Browser, warte bis der Server erreichbar ist und starte den Titel erneut.

### Selbst betreiben

Bau und starte das Image aus diesem Repository.

```sh
docker build -t movy-resolver ./server
docker run --init --rm -p 127.0.0.1:7000:7000 movy-resolver
```

Für Tests am selben Rechner lautet die Manifest-URL `http://127.0.0.1:7000/manifest.json`. Für Geräte außerhalb des Rechners nutze eine erreichbare HTTPS-Adresse und setze `MOVY_ACCESS_TOKEN`.

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
