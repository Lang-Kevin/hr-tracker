## Englisch als Hauptsprache, alle Texte übersetzbar

**Problem:**
Alle UI-Texte waren als deutsche String-Literale im Code verteilt. Eine weitere Sprache hätte Änderungen in jedem Screen bedeutet.

**Lösung:**
- Sämtliche sichtbaren Texte (inkl. Content-Descriptions, Tutorial, Benachrichtigung, Share-Titel) liegen als String-Ressourcen vor: Englisch in `res/values/strings_*.xml` (Default), Deutsch wortgleich zu vorher in `res/values-de/`. Eine neue Sprache = ein neuer `values-xx`-Ordner.
- Zählabhängige Texte als `<plurals>`.
- Domain bleibt frei von Android-Ressourcen: `widgetNotificationText` bekommt die aufgelösten Vorlagen, Enum-Anzeigenamen werden im UI-Layer auf `@StringRes` gemappt.
- Dezimaltrennzeichen in den Trend-Karten folgen der Gerätesprache (statt fest `Locale.GERMANY`).
- Geräte ohne Namen werden unter ihrer MAC-Adresse gespeichert statt als „Unbekanntes Gerät".
- Bekannt: Der Report-JSON-Text in `DetailViewModel.generateReport()` hat weiterhin deutsche Schlüssel; Session-/Meilenstein-Labels sind Nutzerdaten und werden nicht übersetzt.

**Betroffene Dateien:**
- `res/values/strings_*.xml`, `res/values-de/strings_*.xml` (neu)
- alle Screens unter `ui/`, `service/HrRecordingService.kt`, `domain/WidgetVariant.kt` (+ `WidgetVariantTest`)
- `ui/scan/ScanViewModel.kt`
