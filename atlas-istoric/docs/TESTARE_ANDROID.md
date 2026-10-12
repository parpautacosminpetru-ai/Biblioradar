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


## Teste de acceptare obligatorii — Laboratorul Sursei v1.4

- [ ] Deschide Laborator, creează sursa, introdu fila exactă și importă o fotografie JPG/PNG.
- [ ] Fotografiază o pagină prin Camera Android; imaginea originală trebuie să se deschidă din aplicație după relansare.
- [ ] Folosește OCR offline, salvează transcrierea corectată și compar-o cu textul OCR brut neschimbat.
- [ ] Adnotează pasajul selectat; schimbă numai textul dinaintea fragmentului, apoi verifică reancorarea deterministă.
- [ ] Introdu un citat repetat și editează textul astfel încât ancora să fie ambiguă: aplicația trebuie să ceară verificare, nu să inventeze altă ancoră.
- [ ] Creează o afirmație din adnotare; pagina și sursa trebuie completate; după salvare deschide fișa și revino la imaginea originală.
- [ ] Încearcă reimportul aceleiași imagini în aceeași sursă. Nu trebuie creată o a doua pagină cu același SHA-256.
- [ ] Exportă ZIP complet. Verifică prezența `atlas.json` și a imaginilor `pages/<uuid>.<ext>`, apoi importă pe un dispozitiv de test.
- [ ] Deteriorează un octet al unei imagini într-o arhivă ZIP de test. Importul trebuie respins **fără să schimbe baza existentă**.
- [ ] Încearcă export/import JSON simplu după stocarea unei imagini: aplicația trebuie să îl blocheze explicit.
- [ ] Revino la versiunea 1.3 doar într-o copie de test, niciodată pe arhiva reală, pentru a evita incompatibilitatea schemei bazei.
- [ ] Testează imagini mari, cărți cu tipar istoric, pagini cu două coloane și note de subsol; consemnează erorile OCR.
- [ ] Testează un APK **semnat cu cheie release stabilă**, instalat peste versiunea anterioară, păstrând toate sursele și fotografiile.

Aceste teste manuale nu sunt echivalente cu testele Gradle/SQLite și nu trebuie declarate trecute fără dispozitive Android reale.


## Scenarii suplimentare v1.5-rc1 — acoperire și criptare

- [ ] Confirmă că concatenarea tuturor segmentelor OCR reproduce **identic**
      transcrierea verificată, inclusiv spații, diacritice și semne de punctuație.
- [ ] Parcurge manual toate segmentele unei pagini, marcând explicit fiecare segment.
      Procentul de acoperire trebuie să reflecte doar verificările confirmate.
- [ ] Modifică textul după adnotare: pozițiile ambigue nu trebuie marcate ca verificate.
- [ ] Exportă arhiva `.atlas` cu o parolă puternică de minimum 12 caractere și
      compară restaurarea într-o instalare nouă, pe un telefon de probă.
- [ ] Încearcă restaurarea unei arhive criptate cu parolă greșită și a uneia
      deteriorate; nicio înregistrare actuală nu trebuie modificată.
- [ ] Verifică faptul că nu sunt salvate parole în fișierele de configurare ale
      aplicației sau în jurnalul GitHub Actions.
- [ ] Repetă un upgrade cu un APK release semnat prin cheia permanentă a proprietarului.
- [ ] Testează consumul de memorie și timpul de procesare pentru arhive de cel puțin
      1 GB, pe un dispozitiv Android cu RAM limitat, înainte de folosirea permanentă.
