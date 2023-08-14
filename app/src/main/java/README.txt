FEEDBACK:
* Omiljene epizode (jedan)
* Full Screen (nekoliko)
* ako neko ode na drugi strip pa se vrati na prvi, da ne krene od početka
* Izgleda da sam nekom obećao Full Screen u review feedback :(

TODO:
* SearchProvider: dosta ljudi iz Bosne i Hrvatske ne razlikuju č i ć, trebalo bi da search bude pametniji
* Broj 176 (Festival) da li fali poslednja strana?
* Broj 128: fale dve strane između 18 i 19 (18 i 21 po Klasik)
* Vjesnikova izdanja (CBRs)
- One oko Pochite strane koje su pomešane; kao da je bila još neka pomešana, treba pogledati
- Ali recimo u epizodi br. 19, Trojica iz Yume bi bilo poželjno koristiti prvo izdanje, jer su u naredna dva izbacili dve strane zbog cenzure (nacistički pozdrav).
- Neke brojeve (10, 38, 39, 40, 43, 125), mogu se pronaći samo u njihovim tadašnjim spec. izdanjima (trobroj ili max). Oko toga mogu pomoći za linkove.
- Broj 86, Hladni pol i 180 Olimpijski plamen isključivo u klasik izdanju
- Setio sam se nečeg. Broj 76, 77 i 130 ostavi kao klasik, jer su one ponovo crtane (imale su greške), za njih ne važi Vjesnikovo izdanje :)

* Pogledati MaxMagnus - da li su to kratke priče pa mogu da se sakupe od poslednjih strana Alan Forda, ili zasebna kolekcija?
* Pre ili kasnije, neko će da mi naredi sa ugasim servere.  Trebalo bi da namestim da mogu stripovi da se kopiraju sa jednog uređaja na drugi, nešto kao menjanje stripova.
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

Za lokalni download za Windows:
for e in $(s3cmd ls s3://yugostripalanford/ | awk '{print $2}' | sed -e 's|.*ford/||' | tr -d '/'); do echo md $e && echo cd $e && s3cmd ls s3://yugostripalanford/$e/ | awk '{print $4}' | sed -e 's|s3://yugostripalanford/|wget -c https://yugostripalanford.fra1.digitaloceanspaces.com/|' && echo cd ..; done > AF_Download.txt

Inostranstvo.png pokazuje da neki ljudi ne mogu da instaliraju

Provera Vjesnik izdanja:
git log -p -2 | grep Binary | awk '{print $3}' | sed -e 's|a/||' > promene
for p in pre post; do for a in $(cat promene); do cp $p/$a x.gz; gunzip x.gz; mv x $p/$a; done; done
vimdiff pre/app/src/alanFord/assets/227 post/app/src/alanFord/assets/227
izgleda da sam dodao broj 3 svuda
mkdir -p post-no3/app/src/alanFord/assets
for a in $(cat promene); do cat post/$a | sed -e 's|ford3.fra1|ford.fra1|' > post-no3/$a; done
takođe izgleda da sam obrizao broj epizode iz imena fajla, možda sam ovo uradio da bi moglo da se čita i jedna i druga verzija posebno
mkdir -p post-no3-id/app/src/alanFord/assets
for id in $(cat promene | sed -e 's|.*/||'); do cat post-no3/app/src/alanFord/assets/${id} | sed -e 's|/'${id}'/|/'${id}'/'${id}'_|' > post-no3-id/app/src/alanFord/assets/${id}; done
izgleda da Vjesnik imena fajlova nisu uniformisana, pa ne vredi integracija na nivou URL
na primer u 251 ima BDS-421-000.jpg i BDS-421-003.jpg vs. prethodno 251_000.jpg i 251_001.jpg (i BDS-421 i preskakanje je novo)
imaju isti broj linija, pa može integracija na nivou asset imena:
for id in $(cat promene | sed -e 's|.*/||'); do echo $(wc -l pre/app/src/alanFord/assets/${id}) $(wc -l post/app/src/alanFord/assets/${id}); done | awk '{print $1,$3}' | grep -v "121 121"
cat ../../yugostrip-app/app/src/alanFord/assets/numbers | gunzip - > pre/brojevi
provera da imena fajlova neće imati konflikt

Iz email-a ubacio one koje sam već preradio, osim 52 i 42 jer zahtevaju dodatni posao a sad nemam vremena
echo 001, 002, 003, 004, 005, 006, 007, 008, 009, 011, 012, 013, 014, 015, 016, 017, 018, 019, 020, 021, 022, 023, 024, 025, 026, 027, 028, 029, 030, 031, 032, 033, 034, 035, 036, 037, 041, 042, 044, 045, 046, 047, 048, 049, 050, 051, 052, 053, 054, 055, 056, 057, 058, 059, 061, 062 | sed -e 's/\ 0*//g' | tr ',' '\n' | sed -e 's/001/1/' | grep -vE "52|42" | gzip - > app/src/alanFord/assets/vjesnik
for v in $(cat app/src/alanFord/assets/vjesnik | gunzip - ); do mv vjesnik-assets-WIP/$v app/src/alanFord/assets/V${v}; done
V previše upada u oči, pa bolje da stavim da bude malo slovo i posle broja:
for v in $(cat app/src/alanFord/assets/vjesnik | gunzip - ); do mv app/src/alanFord/assets/V${v} app/src/alanFord/assets/${v}v; done
