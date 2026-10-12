# Cerere de compilare semnată — Atlas Istoric

**Stare:** neiniciată. Modificarea acestui document nu pornește semnarea decât
după configurarea celor patru secrete de semnare și a variabilei explicite
`ATLAS_ENABLE_RELEASE_SIGNING=true` în GitHub Repository Settings.

## Inițierea (numai de proprietarul contului)

1. Salvează în siguranță cheia ta Android release și cele patru GitHub Actions Secrets.
2. Creează în **Settings → Secrets and variables → Actions → Variables** variabila
   `ATLAS_ENABLE_RELEASE_SIGNING`, cu valoarea exactă `true`.
3. Deschide **acest fișier** pe ramura `atlas-istoric-professional-release` și
   alege Edit (creionul). Schimbă numărul de mai jos, apoi salvează cu un
   **mesaj de commit ce conține `[sign-release]`**.
4. În Actions, găsești workflow-ul `Atlas Istoric - APK semnat permanent`.
   Descărcarea APK-ului release este disponibilă numai dacă verificarea
   semnăturii și toate testele au reușit.
5. După lansare, readu variabila la `false`; păstrează cheia pentru actualizările viitoare.

Număr cerere: **0**.

Nu încărca fișierul keystore sau parolele în repository și nu le trimite în chat.
Până nu sunt verificate testele pe dispozitive reale, build-ul rămâne un candidat,
nu o ediție academică definitivă.
