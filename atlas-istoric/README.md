# ATLAS ISTORIC — Android offline

**Versiunea codului: 1.2.0 (candidat cu indici incrementali).** Acest pachet conține proiectul complet și workflow-ul de compilare pentru un APK Android. **Nu conține APK compilat și NU este încă certificat ca testat pe un telefon real.** Pentru obținerea installerului este necesar build-ul Android (GitHub Actions sau Android Studio).

## Nou în v1.2 — indici incrementali și benzi paralele

- În Atlas → **Benzi paralele pe categorii**, fiecare indice/rădăcină și fiecare subcategorie are bandă orizontală proprie pe aceeași riglă temporală.
- **Indice nou** creează o rădăcină editabilă, iar butonul **+** de pe orice rând adaugă o subcategorie imbricată, nelimitată ca adâncime logică.
- Poți redenumi sau muta o categorie prin formularul de editare. Identificatorul și referințele rămân aceleași; istoricul modificărilor este păstrat.
- În celule se văd numărul afirmațiilor distincte și intensitatea vizuală a documentării. Apăsarea unei celule deschide pasajele și sursele cu paginile lor.
- Poți selecta regiuni ca filtre fără schimbarea categoriei: nestingul istoric al regiunilor este evaluat pentru fiecare interval de timp în parte.
- Există benzi speciale **Fără categorie** și **Fără dată**, pentru a nu pierde cercetarea incomplet clasificată.
- Lista de benzi este incrementală și poate fi extinsă în loturi pentru a evita supraîncărcarea interfeței.
- Aceeași afirmație poate apărea în mai multe categorii și părinții lor, dar fără dublare fizică în baza de date.

**Semnarea:** workflow-ul generează APK de test. Versiunile debug construite pe runneri diferiți pot avea semnături diferite; înainte de reinstalare exportă baza de date și păstrează o copie. Folosirea îndelungată necesită configurarea unei chei release stabile.

## Ce este Atlas Istoric

Un atlas personal pentru cercetare documentară, cu o singură bază locală și trei perspective suprapuse: **TIMP × SPAȚIU × TEMĂ**. O afirmație se introduce o singură dată, dar poate participa la oricâte intersecții, fără copierea pasajului. Un actor are ID stabil; afirmațiile distincte ale surselor despre același actor sunt păstrate distinct.

### Funcții incluse în cod

| Componentă | Comportament |
|---|---|
| Riglă temporală continuă | Epocă, secol, an, lună, zi; zoom/derulare și salt la an; funcționează chiar dacă nu există surse pentru perioada afișată. Ani negativi î.Hr., fără anul zero. |
| Spațiu istoric | Identități de tip LOC, nesting prin `PART_OF` datat, selecție simultană de regiuni și subregiunile acestora. Sunt interzise ciclurile în ierarhie. |
| Tematică | Teme extensibile cu subteme, selecție multiplă și logică **OR / AND**. |
| Matrice de suprapunere | Timp × spațiu sau timp × temă, cu numărul afirmațiilor documentate pe intersecție; apăsarea unei celule activează filtrele. |
| Relații/actori | Identitate stabilă, nume alternative, avertizare conservatoare privind dublurile, relații documentate/provizorii și indexul aparițiilor în afirmațiile filtrate. |
| Surse și proveniență | Autor, lucrare, ediție, identificator, pagină sau secțiune, stare lectură și ultima pagină parcursă; citat/parafrază/extragere separate de comentariu. |
| Cameră OCR live offline | CameraX + ML Kit OCR Latin integrat în aplicație: citește textul local, sugerează doar potriviri prudente de nume, teme și ani. **Salvarea în bază necesită verificarea și confirmarea utilizatorului.** |
| Introducere rapidă | Creezi o sursă, o identitate sau o temă fără să pierzi pasajul OCR aflat în editare; noua înregistrare se asociază automat cu fișa de lucru. |
| Atelier de sinteze | Texte originale în spațiu separat de afirmațiile brute, linkuri la pasajele documentare, export Markdown cu aparat de referințe. |
| Revizii | Istoricul modificărilor afirmatiilor, surselor, entităților, temelor, relațiilor și sintezelor este păstrat. |
| Copie de siguranță | Export/import JSON cu referințe și ID-uri, migrare din formatul v1 la v2 și din baza Room v1 la v2, fără ștergerea automată a datelor la upgrade. |

## Cum obții APK-ul pe GitHub

1. Creează un **repository nou privat** pe GitHub, numit `Atlas-Istoric`, cu ramura principală `main`.
2. Încarcă în rădăcina lui conținutul acestui ZIP (nu ZIP-ul ca singur fișier), păstrând structura directoarelor.
3. Deschide fila **Actions → Atlas Istoric — APK instalabil → Run workflow**. Workflow-ul pornește și automat la un commit pe ramura `main`.
4. După o execuție **verde**, descarcă artifactul `atlas-istoric-v1-installer` de la finalul paginii execuției.
5. În ZIP-ul artifactului găsești `app-debug.apk` și `SHA256SUMS.txt`. Instalează APK-ul pe Android 8 sau mai nou, permițând instalarea fișierelor din sursa respectivă.

**Atenție la actualizări:** acest workflow produce un APK *debug*. Semnătura debug este generată de runnerul GitHub și se poate schimba între compilări. Un APK viitor semnat cu altă cheie nu poate fi instalat peste cel anterior fără dezinstalare; dezinstalarea șterge baza locală. Pentru folosirea permanentă, recomand configurarea unei **chei stabile de semnare release** înainte de prima instalare serioasă. Nu publica și nu urca cheia privată în cod. Până atunci, exportă periodic arhive JSON, inclusiv înaintea oricărei actualizări.

### Compilare locală în Android Studio

Deschide directorul în Android Studio cu JDK 17 și Android SDK Platform 35. Sync Gradle, apoi **Build → Build APK(s)**. Sau cu Gradle 8.9:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

APK-ul apare în `app/build/outputs/apk/debug/app-debug.apk` **doar după un build reușit**. Artefactul nu este generat de simpla dezarhivare.

## Fluxul de lucru al istoricului

1. **Surse** → introdu o bibliografie corectă, marchează stadiul lecturii.
2. **OCR** → îndreaptă camera spre pagină, îngheață pasajul și corectează eventualele erori.
3. **Afirmație** → selectează sursa, pagina, tipul citatului, perioada (doar cât se știe explicit), actorii și locurile existente sau creează altele noi.
4. **Atlas** → explorează rigla continuă, regiunile, temele și matricea; poți combina OR/AND pentru teme.
5. **Relații** → leagă identități și atașează afirmația care justifică legătura; relațiile fără susținere sunt provizorii.
6. **Atelier** → redactează și exportă sintezele tale; afirmațiile brute nu sunt rescrise.
7. **Arhivare** → exportă regulat un JSON într-un loc sigur, separat de telefon.

## Rigoare și limite

- Textul OCR nu este tratat automat drept fapt. Clasificarea automată offline nu poate garanta recunoașterea corectă a locurilor și actorilor istorici omonimi. Sugestiile se confirmă manual.
- Datele exacte sunt indexate ca atare; datele incerte nu sunt convertite artificial în zile/ani exacți. Luna/ziua se păstrează conform sursei; aplicația **nu convertește automat calendarele iulian, gregorian sau locale**.
- `PART_OF` exprimă nesting istoric datat, nu o hartă vectorială cu frontiere reale. Aplicarea unei relații cu interval cronologic necunoscut într-un filtru temporal nu este dovada continuității acelei relații.
- Matricea afișează **numărul de afirmații înregistrate**, nu numărul efectiv de evenimente care s-au petrecut.
- Operațiunile mari (ex. milioane de afirmații) nu au fost testate. Listele principale sunt colectate în memorie; aplicația folosește lazy rendering și mută calculul matricei în fundal, dar o arhivă foarte mare va necesita interogări paginabile și indexare full-text.
- Exportul JSON conține textul și metadatele, inclusiv sintezele, **nu** fotografiile paginilor sau PDF-uri. JSON-ul exportat nu este criptat; protejează-l.
- Proiectul nu solicită permisiune de internet; textul OCR se procesează local și baza rămâne în SQLite pe dispozitiv.
- Nu există încă teste instrumentate pe dispozitive fizice pentru toate versiuni Android și nu există încă un release APK semnat stabil.

## Teste disponibile

Verificarea logicii (fără Android SDK):

```bash
kotlinc checks/ModelsStub.kt checks/LogicCheck.kt \
  app/src/main/java/ro/atlasistoric/util/TimeMath.kt \
  app/src/main/java/ro/atlasistoric/util/Nesting.kt \
  app/src/main/java/ro/atlasistoric/util/TemporalNavigation.kt \
  app/src/main/java/ro/atlasistoric/ocr/OcrSuggestions.kt \
  -include-runtime -d /tmp/atlas-check.jar
java -jar /tmp/atlas-check.jar
python3 checks/sqlite_migration_check.py
```

JUnit pentru proiectul Android:

```bash
gradle :app:testDebugUnitTest
```

`docs/ARHITECTURA.md` descrie principiile de cunoaștere și `docs/TESTARE_ANDROID.md` descrie testarea manuală necesară înainte de utilizarea pe termen lung.

## Proprietatea codului

Nu este adăugată o licență open-source. Poți păstra codul într-un repository privat și decide ulterior dacă dorești să publici aplicația. Deține în mod separat și protejează conținutul cercetării, care nu se urcă automat în repository.
