# Atlas Istoric — lansare profesională controlată

**Stadiu:** candidat de lansare v1.5.0-rc1. Nu eticheta „final profesional” fără
validarea tuturor criteriilor de acceptare și un APK release semnat cu cheie permanentă.

## 1. Cine deține cheia de actualizare

Cheia de semnare Android aparține proprietarului aplicației, nu unui serviciu terț.
Pentru prima lansare permanentă, generează local o cheie și păstreaz-o în cel puțin
două locații protejate; **nu o comite în GitHub, nu o trimite într-un chat și nu o
atașa vreunui issue**.

Cu Java JDK instalat pe calculator:

```sh
keytool -genkeypair -v -keystore atlas-release.jks -alias atlas -keyalg RSA -keysize 3072 -validity 10000
```

Creează o parolă de depozit și o parolă de cheie. Nu pierde aceste parole sau fișierul
`atlas-release.jks`: fără cheia originală nu vei putea semna actualizări compatibile
ale aplicației cu același `applicationId`.

Convertește local keystore-ul în base64 pentru GitHub Secrets:
- Linux: `base64 -w0 atlas-release.jks`
- macOS: `base64 -i atlas-release.jks | tr -d '\n'`
- Windows PowerShell: `[Convert]::ToBase64String([System.IO.File]::ReadAllBytes("atlas-release.jks"))`

## 2. Configurează GitHub Secrets, fără a le publica

În repository: **Settings → Secrets and variables → Actions → New repository secret**.
Creează **exact** următoarele patru secrete:

| Secret | Conținut |
|---|---|
| `ATLAS_RELEASE_KEYSTORE_B64` | Keystore-ul local, codificat base64 |
| `ATLAS_RELEASE_STORE_PASSWORD` | Parola depozitului de chei |
| `ATLAS_RELEASE_KEY_ALIAS` | Aliasul, de exemplu `atlas` |
| `ATLAS_RELEASE_KEY_PASSWORD` | Parola cheii asociate aliasului |

Fluxul nu include niciodată secrete în cod sau în APK. Fișierul JKS temporar de pe
runner este șters după compilare. Folosește cont GitHub cu autentificare în doi pași.

## 3. Generează manual versiunea semnată

Workflow-ul de semnare se află pe ramura izolată
`atlas-istoric-professional-release`, fără să modifice ramura principală Biblioradar.
GitHub poate afișa butonul *Run workflow* numai pentru workflow-uri prezente și pe
ramura implicită. Pentru a evita modificarea proiectului Biblioradar, poți declanșa
**în siguranță semnarea dintr-un commit controlat**, astfel:

1. După configurarea celor patru GitHub Secrets, adaugă în
   **Settings → Secrets and variables → Actions → Variables** variabila de repository
   `ATLAS_ENABLE_RELEASE_SIGNING` cu valoarea `true`.
2. Deschide pe ramura profesională documentul
   `atlas-istoric/docs/RELEASE_REQUEST.md` și schimbă numărul cererii.
3. Salvează modificarea cu un mesaj de commit care conține exact
   `[sign-release]`. Aceste trei condiții activează **doar** workflow-ul semnat.
4. În **Actions → Atlas Istoric - APK semnat permanent**, verifică rularea.
   Un build reușit publică `app-release.apk`, SHA-256 și certificatul public.
5. După descărcare, setează variabila `ATLAS_ENABLE_RELEASE_SIGNING` înapoi la
   `false`. Păstrează cheia, parola și certificatul pentru viitoarele versiuni.

Este blocată orice încercare de semnare dacă secretele lipsesc. Nu urca cheia de semnare
sau parolele în repository, într-un issue sau în chat.

## 4. Important pentru versiunile debug anterioare

APK-urile v1.3 și v1.4 au fost semnate de Android build/debug. Versiunea release
cu noua cheie **nu se va instala direct peste ele** pe același `applicationId`.
Înainte de orice dezinstalare:

1. Exportă arhiva completă din aplicația veche (ZIP dacă există imagini).
2. Copiază arhiva în afara aplicației și verifică-i integritatea.
3. TESTEAZĂ restaurarea într-o instalație/dispozitiv separat; nu presupune că exportul este suficient.
4. Instalează release-ul cu cheia permanentă și restaurează datele verificate.
5. Ulterior, folosește **aceeași cheie** și un `versionCode` mai mare la orice actualizare.

Nu șterge niciodată singura copie a cercetării.

## 5. Criteriile de acceptare „profesional”

- [ ] Installer release semnat stabil, certificat verificat, fără cheie în Git;
- [ ] Upgrade semnat cu aceeași cheie, păstrarea bazei demonstrată pe 2 dispozitive Android;
- [ ] Captură și OCR pe minimum 50 de pagini variate, cu eșecuri și erori documentate;
- [ ] Originalele și transcrierile au amprente verificate, iar neconcordanțele sunt vizibile;
- [ ] Adnotările sunt reancorate sau marcate explicit drept ambigue;
- [ ] Export ZIP complet și restaurare comparată bit-cu-bit / referință-cu-referință;
- [ ] Comportament verificat offline și la întreruperea alimentării în timpul operațiilor;
- [ ] Navigare testată cu 100.000 de afirmații și mai multe sute de mii de legături;
- [ ] Audit de securitate al arhivelor, al fișierelor importate și al accesului la date;
- [ ] Contradicțiile între surse sunt păstrate distinct, fără armonizări automate;
- [ ] Funcțiile de analiză, graf conceptual, ediție critică și materiale didactice sunt
      implementate și testate înaintea declarării ediției academice complete.

**Limite actuale:** este disponibilă o arhivă `.atlas` criptată cu parolă,
dar și un ZIP tradițional necriptat (pentru compatibilitate); decriptarea folosește
temporar fișiere în cache-ul privat al Android. OCR-ul nu garantează recunoaștere
perfectă și nu extrage exhaustiv toate afirmațiile; editorul de hărți conceptuale și
atelierul didactic universitar complet rămân de construit. Registrul de acoperire
atestă numai examinarea segmentelor de text, NU exhaustivitatea înțelegerii istorice.

## 6. Strategie de arhitectură pe termen lung

Arhivă local-first, modele cu identificatori stabili, legături documentare verificabile,
istoric de revizii, import/export cu scheme versionate, compatibilitate viitoare cu IIIF,
TEI, CIDOC CRM și formate bibliografice standard. În viitor, sincronizarea între
dispozitive trebuie să fie opt-in și cu rezolvare explicită a conflictelor de date.
