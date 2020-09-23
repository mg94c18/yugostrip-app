FEEDBACK:
* Omiljene epizode (jedan)
* Full Screen (nekoliko)

TODO:
* ? Ako "Idi na stranu" dobije veliki broj, a strip je N_b, uzeti u obzir N_a
* ? Kada detektuje da nema interneta, treba da se pojavi opcija gde mogu da se vide prethodno sačuvane epizode
* ? Nađi -> "grešku" da prikazuje poslednju grešku tako da korisnici mogu da pošalju screenshot
* ? Lint za performClick() za accessibility

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
