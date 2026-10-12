# Arhitectura datelor — Atlas Istoric 1.0

## Principiul integrității sursei

O persoană, instituție, loc sau eveniment = o **identitate** internă stabilă (nu se unifică automat omonimele). O afirmație = o **înregistrare separată** cu text, tip (citat/parafrază/extragere), sursă bibliografică, pagină sau secțiune, temporalitate, note de cercetător. Fiecare afirmație are mai multe legături la identități și teme. Interpretările și sintezele proprii sunt în altă tabelă și pot cita afirmațiile fără să le rescrie.

## Model relațional

```text
HistoricalSource 1 ── N HistoricalAssertion
HistoricalAssertion N ── N HistoricalEntity   (AssertionEntityLink, rol: LOC/ACTOR/SUBJECT)
HistoricalAssertion N ── N HistoricalTopic    (AssertionTopicLink)
HistoricalTopic 1 ── N HistoricalTopic        (parentId; fără cicluri)
HistoricalEntity  N ── N HistoricalEntity    (EntityRelation, inclusiv PART_OF temporal)
ResearchDraft N ── N HistoricalAssertion      (DraftClaimLink)
RevisionEntry ── istoricul versiuni anterioare
```

## Indexare multidimensională

- **TIMP**: `yearStart`, `yearEnd`, `precision`, `periodLabel`, `month`, `day`. `TemporalNavigation` generează o riglă de zoom și pan fără a inventa înregistrări. Intervalele se intersectează doar dacă afirmația are date relevante.
- **SPAȚIU**: relații `PART_OF(childPlace→parentPlace)` cu interval, sursă opțională, și traversare a descendenților când un teritoriu e filtrat. Ciclurile sunt respinse. Identitatea unui loc nu se multiplică la schimbarea suveranității.
- **TEMĂ**: `parentId` și legături multe-la-multe. Mai multe ramuri pot fi selectate prin OR sau AND.
- **AFIRMAȚII / ACTORI**: filtrare care compune restricțiile, apoi afișează actori din înregistrările corespondente. Frecvența mențiunilor nu înseamnă domnie/longevitate istorică.
- **MATRICE**: numai contoare ale pasajelor documentate; calculul se execută pe un dispatcher de fundal pentru a nu bloca randarea.
- **INTERPRETARE**: `ResearchDraft` separat, cu legături explicite către afirmațiile folosite.

## Proveniență și prudență epistemică

- Citatele și parafrazele aparțin unei surse, nu unei presupuse versiuni unice a adevărului. Afirmațiile divergente coexistă.
- Denumirile alternative se folosesc numai ca indicii conservatoare pentru detectarea posibilelor dubluri; utilizatorul confirmă identitatea.
- OCR ML Kit Latin este local; textul eronat se corectează manual. Un an detectat în OCR este doar un număr candidat, nu un fapt.
- `EntityRelation` fără `supportingAssertionId` este marcat provizorie. Relațiile `INFLUENCES` nu demonstrează singure cauzalitatea.
- Precizia calendaristică a unei surse nu este convertită automat în alt calendar.

## Evoluție a datelor

Schema SQLite Room v2 are `MIGRATION_1_2` pentru a adăuga stadiul lecturii și atelierul fără a șterge tabelele existente. Exportul JSON este schema v2; arhivele schema v1 se pot importa cu câmpuri default. Importul este **înlocuire explicit confirmată**, validată înainte de ștergerea datelor curente și încapsulată într-o tranzacție cu rollback la eșec.

## Scalabilitate

Stocarea locală SQLite poate fi extinsă, însă prezentarea colecțiilor întregi prin `Flow<List<...>>` nu garantează performanță pentru milioane de pasaje. O evoluție pentru volum extrem trebuie să includă Room Paging3, FTS5, tablouri de agregare pe epoci/regiuni și teste pe telefoane cu memorie mică. Datele actuale rămân exportabile independent de interfață.

## Strat didactic v1.3

- `StudyCard(assertionId)` referă afirmația documentară originară; întrebarea și nota cercetătorului sunt independente de sursă.
- `StudyReview(cardId, grade, reviewedAt, nextDueAt)` înregistrează fiecare recapitulare într-un jurnal separat.
- Întrebările pot evalua pasajul, anul/intervalul sau locul explicit, fără a construi alte fapte istorice.
- Politica de repetare este locală și deterministă; nu estimează în mod științific cunoașterea reală a utilizatorului.
- Filtrele didactice reutilizează nestingul temporal, geografic și tematic; precizia incompletă este păstrată.
- Migrarea incrementală `MIGRATION_2_3` păstrează toate tabelele anterioare; exportul JSON v3 include progresul didactic.
- Exercițiile de tip hartă, aliniere cronologică și confruntare critică a două surse sunt planificate pentru versiuni ulterioare.
