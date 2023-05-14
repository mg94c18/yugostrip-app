FEEDBACK:
* Omiljene epizode (jedan)
* Full Screen (nekoliko)
* ako neko ode na drugi strip pa se vrati na prvi, da ne krene od početka
* Izgleda da sam nekom obećao Full Screen u review feedback :(

TODO:
* Brisanje sinhronizacije jer više nije potrebna
* Auto zoom
* Pogledati MaxMagnus - da li su to kratke priče pa mogu da se sakupe od poslednjih strana Alan Forda, ili zasebna kolekcija?
* Vjesnikova izdanja (CBRs)
* Hrvatski prevod ako nađem
* "(dodano 8 str. između 48.-49. po unutrašnjoj numeraciji, koje se ne nalaze u izvornom SS izdanju)" (Ponovo Baby Kate, 9171ytk6dyd1v5x)
* ? Horizontalno čitanje za krupniju sliku, tako što se AF iseče na pola.  Ne radi za MM.

* ? Kada detektuje da nema interneta, treba da se pojavi opcija gde mogu da se vide prethodno sačuvane epizode
* ? Nađi -> "grešku" da prikazuje poslednju grešku tako da korisnici mogu da pošalju screenshot
* ? Lint za performClick() za accessibility
* SearchProvider tretira 'dates' asset kao case-sensitive a bilo bi lepo da nije

MM mess:
* 127bis "L'ultimo mistero" fali: https://striputopija.blogspot.com/2018/01/127.html
* 131bis "Space Shuttle" fali: https://striputopija.blogspot.com/2018/01/131.html
* 139bis "Il ritorno di Jaspar" fali, i nemam link.

* Ako ne nađe sačuvanu stranu ili broj, da ne crash-uje i da ne prikaže prazno, već da učita default ili možda migracija
* Migracija zahteva i preimenovanje svih fajlova.  Možda bolje promeniti ID-ove samo na UI nivou
* Full Screen obećao
* Ako nema zoom, podesiti da bude ceo vidljivi deo, ili barem cela slika?

obrisati slike koje daju 404

MM reference:
* MM 63 strana 158 nije dobro skenirana
* MM 45/page127 pominje MM LMS 46/47
* MM 79 pominje MM LMS 40/41
16 MM 20. Tothova knjiga
18 MM 47/48 Tajna velike ?
Orig MM SP3 Čovek iz Atlan?
* Dodati "Rječnik misterija" (5. broj specijalnih izdanja)
* https://www.sergiobonelli.it/sezioni/13/martin-mystere ima brojeve epizoda drugačije; pospremiti ovo
* https://www.goodreads.com/series/59802 ima pak drugačije

Testiranje za updates (2^4 ima 16 slučajeva)
A: čitao staru epizodu (pre 100, jednu on onih 23_24_b) vs. čitao novu
B: otvaranje aplikacije pre sync vs. posle sync
C: sledeći sync tokom čitanja vs. posle čitanja
D: stao na nekoj strani koje više nema

UPDATES:
cat updates.gz | gunzip - | tr -d '\r' | sed -E 's/([^\ ]+)\ +([^\ ]+)\ +([^\ ]+)$/wget -O assets\/\1 "\3"/' > script
mkdir for-drive
za 440 epizoda:
    EC=440
    for A in titles numbers dates; do zless assets/$A | head -n $EC | gzip - > for-drive/$A.gz; done
    zless updates.gz | head -n `echo 3+$EC-425|bc` | sed -e "s/TIMESTAMP/`date +%s000/`" | gzip - > for-drive/updates.gz
Deploy:
    MOVE titles.gz, numbers.gz, dates.gz from Debug to Release
    MOVE 421.gz, ..., 440.gz from Debug to Release
    COPY updates.gz from Debug to Release
Sledeći put:
    MOVE updates.gz sa NOVIM titles.gz, numbers.gz, dates.gz from release to rollback
    NOVI updates.gz sa NOVIM titles.gz, numbers.gz, dates.gz
