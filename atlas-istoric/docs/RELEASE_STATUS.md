# Stadiu livrare — 12 octombrie 2026

Versiunea codului: `1.0.0`, candidată la lansare.

## Verificat în mediul de lucru

- 35 verificări de logică temporală, nesting și sugestii OCR: **trecute**.
- Migrarea SQL din schema 1 în 2 (test SQLite, conservarea sursei și integritatea FK): **trecută**.
- Verificarea sintactică Kotlin fără bibliotecile Android: nicio eroare de parsare raportată; referințele Android/Compose nu pot fi rezolvate fără Android SDK/dependențe.
- Configurația de build `.github/workflows/android.yml` inclusă: folosește Java 17, SDK 35, Gradle 8.9.

## Nevalidat, deci nepromis

- Nu a fost compilat niciun APK în acest mediu (SDK Android și dependențele Google/Maven nu sunt disponibile aici).
- Nu există test instrumentat pe telefon sau verificare end-to-end OCR/Compose.
- Workflow-ul de GitHub Actions nu a rulat, fiindcă nu există încă un repository `Atlas-Istoric` la care proiectul să fie încărcat.
- Nu există cheie stabilă de semnare pentru actualizări de release.
- Nu există clasificare semantică istorică automată garantată, nici stocare fotografii/PDF-uri.

**Această arhivă este codul sursă completabil, nu un pachet APK instalabil.** Pentru un APK care poate fi instalat trebuie urmat build-ul de pe GitHub și verificate testele Android din `docs/TESTARE_ANDROID.md`.
