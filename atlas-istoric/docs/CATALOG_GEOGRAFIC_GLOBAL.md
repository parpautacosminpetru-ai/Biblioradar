# Catalogul geografic mondial — specificație profesională

**Stadiul v1.7:** căutare în toate locurile deja indexate local, inclusiv forme alternative
și diacritice, cu selecție multiplă și creare rapidă de identități de tip PLACE.
**Nu include încă gazetteere mondiale preîncărcate ori frontiere vectoriale istorice.**

## De ce „toate regiunile posibile” nu poate însemna o listă definitivă

Un teritoriu poate fi geografic, administrativ, politic, cultural, etnografic,
ecleziastic, natural, imaginar sau disputat. Numele, întinderea, statutul și
apartenența politică se schimbă în timp. A preîncărca o singură hartă actuală și
a o interpreta ca hartă a trecutului ar produce erori documentare.

Obiectivul real: **un catalog mondial extensibil, reconciliabil și documentat**,
nu afirmarea unui inventar exhaustiv și neschimbător.

## Pachete offline recomandate

1. **GeoNames** — toponime globale contemporane, nume alternative și unități
   administrative; https://www.geonames.org/ și https://download.geonames.org/export/dump/.
   GeoNames folosește CC BY 4.0: include atribuirea și versiunea pachetului importat.
   Un import naiv al tuturor milioanelor de intrări ar suprasolicita actuala listă
   `Flow<List<HistoricalEntity>>`; folosește FTS5 + paginare și pachete regionale.
2. **Pleiades** — locuri din antichitate, nume, localizări și conexiuni cu identificatori
   permanenți; https://pleiades.stoa.org/downloads. Datele Pleiades sunt CC BY 3.0.
   Importă versiuni arhivate, atribuie sursele și păstrează gradele de incertitudine.
3. **World Historical Gazetteer** — armonizarea toponimelor istorice și spații temporale;
   https://docs.whgazetteer.org/content/technical/apis.html. Necesită autentificare
   pentru multe endpoint-uri, iar datele agregate AU LICENȚE DIFERITE; verifică
   condițiile fiecărei surse înainte de descărcare/redistribuire.
4. **Date proprii** — nume istorice, episcopii, comitate, provincii, regiuni culturale,
   frontiere interpretate, cu sursă documentară și interval declarat.

Evitați descărcarea în masă din serviciul public Nominatim sau a tile-urilor
`tile.openstreetmap.org` pentru hărți offline: politicile OSM interzic folosirea
lor astfel. Pentru hărți offline trebuie folosite extrase/date care permit aceasta
și un renderer/local server corespunzător.

## Schema țintă înainte de încărcarea pachetelor mari

- `PlaceAuthority(source, externalId, version, licence, attribution)`
- `PlaceName(placeId, label, language, validFrom, validUntil, source)`
- `PlaceGeometry(placeId, geometry, validFrom, validUntil, confidence, evidenceId)`
- `RegionMembership(placeId, regionId, relationType, interval, supportingAssertionId)`
- `PlaceIndexFTS` pentru căutare rapidă după nume/variante și `Paging3`.
- `CatalogImport`: pachet, checksum, versiune, trasabilitate, rollback, fără ștergere
  sau fuzionare automată a identităților omonime.

## Zoom cronologic vs. zoom cartografic

În v1.7 avem **zoom temporal permanent** în cinci trepte.
Zoomul cartografic ar fi independent: gest pinch, pan, recentrare, scară în kilometri,
straturi istorice datate și comparație între două epoci. Poligoanele/limitele
istorice trebuie să provină din surse, nu să fie deduse din frontiere moderne.

## Pași de implementare

1. Catalog de autorități geografice, metadate de licență și index FTS5.
2. Import incrementat pentru pachete regionale GeoNames, apoi Pleiades și WHG.
3. Reconciliere asistată a numelor, cu confirmare umană a homonimelor.
4. Pan/zoom spațial real, geometrie și straturi datate cu transparență și sursă.
5. Teste offline pe telefon cu sute de mii de locuri și milioane de toponime,
   plus export/import complet și păstrarea ID-urilor.
