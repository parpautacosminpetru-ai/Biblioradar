# Lupa de Relevanță — Android v0.2

Instrument OCR offline pentru cercetare în documente fizice. Aplicația nu interpretează semantic textul și nu decide ce este relevant. Utilizatorul definește o hartă ierarhică de termeni, iar aplicația caută simultan termenii vizibili prin cameră și îi marchează conform nodului lor.

## Ce face versiunea 0.2

- cameră live cu CameraX;
- OCR local cu modelul ML Kit inclus în APK;
- caută simultan toți termenii din hartă;
- ierarhie de noduri: #, ##, ### etc.;
- culoare și simbol diferite pentru fiecare nod;
- traseu vizibil: `Tema → Subtemă → Nod`;
- moduri Exact / Începe cu / Conține;
- opțional, doar primele N caractere ale termenului;
- ignorare opțională a diferențelor de diacritice;
- highlight stabilizat temporal, pentru scanare din mers;
- înghețarea cadrului fără salvare automată;
- lanternă;
- semnal sonor/vibrație discretă, oprite implicit;
- semnalul audio/vibrația folosesc doar densitatea potrivirilor/nodurilor, nu o analiză semantică;
- dicționar CSV local păstrat din versiunea anterioară;
- fără permisiune INTERNET în manifest.

## Formatul hărții

Poți scrie sau lipi direct în aplicație:

```text
@title: Cauzele bătrânirii
# Tema centrală | color=#C84A4A | symbol=★
senescență | îmbătrânire | aging
## Mecanisme celulare | color=#D9893D | symbol=◆
telomer | telomeri | telomeric
stres oxidativ
### Mitocondrii | color=#3F7394 | symbol=■
mitocondrie | mitocondrial
```

Reguli:
- `#` = nod mare, `##` = subnod, până la 6 nivele;
- `color=#RRGGBB` este opțional;
- `symbol=★` este opțional și poate fi orice simbol/emoji Unicode acceptat de telefon;
- pe o linie de termeni, `|` separă termeni independenți;
- spațiile dintr-un termen sunt păstrate, deci `sfat domnesc` este căutat ca expresie;
- liniile `// comentariu` sunt ignorate;
- harta se salvează local și se poate importa/exporta ca TXT.

## Build pe GitHub — telefon

1. Creează un repository nou, gol.
2. Dezarhivează acest ZIP pe telefon.
3. În GitHub: `Code` → `Add file` → `Upload files`.
4. Încarcă **conținutul folderului dezarhivat**, astfel încât în rădăcina repository-ului să vezi direct:
   - `.github`
   - `app`
   - `build.gradle.kts`
   - `settings.gradle.kts`
5. Commit changes.
6. Intră la `Actions` → `Build APK` → `Run workflow`.
7. Când build-ul este verde, în Summary descarcă artifact-ul `Lupa-Relevanta-APK`.
8. Extrage artifact-ul și instalează `app-debug.apk`.

IMPORTANT: nu încărca ZIP-ul ca un singur fișier în repository. GitHub Actions trebuie să vadă direct folderul `app` și fișierul `build.gradle.kts`.

## Dicționar local

Format CSV recomandat:

```csv
term,definition,synonyms,antonyms
istorie,Definiția termenului,...,...
```

Poți importa mai multe fișiere CSV; intrările sunt păstrate în baza locală SQLite.

## Confidențialitate / offline

Aplicația nu declară permisiunea INTERNET. OCR-ul folosește biblioteca bundled `com.google.mlkit:text-recognition`, iar hărțile și dicționarele sunt locale. Cadrul înghețat este doar afișat în memorie și nu este salvat automat.

## Teste locale incluse

`core-test/MapParserTest.kt` verifică parserul hărții și motorul de potrivire independent de Android.
