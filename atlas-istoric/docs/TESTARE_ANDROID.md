# Protocol de testare înainte de folosire permanentă

## Ce a fost validat fără Android SDK

- Teste Kotlin independente pentru BCE/CE, secole, intersecții, nesting, OCR și rigla continuă.
- Test SQL din `checks/sqlite_migration_check.py` pentru DDL-ul migrării și cheile străine.
- Verificarea sintactică a fișierelor Kotlin cu compilerul, fără dependențele Android (nu înseamnă compilare Android reușită).

## Ce rămâne obligatoriu de verificat prin GitHub Actions

1. `:app:testDebugUnitTest` trebuie să fie verde.
2. `:app:assembleDebug` trebuie să producă un APK nenul.
3. Instalează APK-ul pe un telefon Android 8+ și verifică pornirea, navigarea și accesul camerei.

## Scenarii practice de acceptare (manuale)

- [ ] Creează o sursă cu autor/ediție, statut de lectură și ultima pagină.
- [ ] Scanează o pagină offline cu camera; corectează textul. Încearcă cu diacritice și o pagină slab iluminată.
- [ ] Creează rapid o sursă nouă din fișa OCR fără pierderea pasajului, apoi introduce pagina.
- [ ] Salvează o afirmație doar după verificarea tipului citat/parafrază, anului și sursei.
- [ ] Creează un actor, asociază-l cu două afirmații din două surse; verifică identitatea unică și două citări distincte.
- [ ] Creează o ierarhie loc → regiune → stat cu `PART_OF` datat; verifică faptul că se schimbă la schimbarea perioadei.
- [ ] Încearcă un ciclu `PART_OF` și verifică mesajul de respingere, fără să pierzi datele anterioare.
- [ ] Treci din anul -1 în anul 1; verifică faptul că anul zero nu apare.
- [ ] Mută rigla către perioade pentru care nu ai afirmații și revino; nimic nu trebuie șters.
- [ ] Selectează două teme prin OR și prin AND; verifică numărul și conținutul afirmațiilor afișate.
- [ ] Deschide o celulă din matrice și verifică timpul/spațiul/tema selectate.
- [ ] Modifică o afirmație și verifică istoricul reviziilor în fișa ei.
- [ ] Creează o sinteză, asociază trei afirmații, exportă Markdown cu paginile și lucrările corecte.
- [ ] Exportă JSON, importă-l pe un telefon de test sau bază separată, verifică identitățile, paginile, relațiile, sintezele și reviziile.
- [ ] Fă două editări după restaurare și verifică dacă istoricul noilor modificări se adaugă corect.
- [ ] Testează arhivă mare pentru memorie/timp de răspuns; limitele trebuie consemnate înainte de utilizare la scară foarte mare.
- [ ] Configurează cheia stabilă pentru actualizări, testează update semnat cu aceeași cheie fără pierderea datelor.

**Până la trecerea scenariilor de mai sus, codul este un candidat de release, nu o aplicație certificată drept complet testată.**
