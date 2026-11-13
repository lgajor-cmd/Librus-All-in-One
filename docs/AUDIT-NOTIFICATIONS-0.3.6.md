# Audyt powiadomień — wydanie 0.3.6

Data: 2026-11-13

## Znaleziona luka

Podczas pierwszej synchronizacji w tle aplikacja mogła otrzymać dane z części kategorii ocen, gdy inne kategorie były chwilowo niedostępne. Zapisywała wtedy niepełny stan początkowy. Po odzyskaniu brakującej kategorii jej istniejące, historyczne wpisy mogły zostać błędnie uznane za nowe i wywołać fałszywe powiadomienia.

## Poprawka

- Stan powiadomień rozdziela teraz znane identyfikatory od kategorii oczekujących na pierwszy pełny odczyt.
- Pierwszy odczyt dostępnej kategorii zapisuje jej stan bez powiadomień o historii.
- Gdy niedostępna wcześniej kategoria wraca po raz pierwszy, jej aktualne wpisy są bezpiecznie inicjalizowane.
- Nowe wpisy w kategoriach, które były już wcześniej dostępne, nadal generują powiadomienia nawet wtedy, gdy inna kategoria pozostaje niedostępna.
- Późniejsze, chwilowe błędy kategorii nie usuwają wcześniej zapamiętanych identyfikatorów.
- Stan znanych wpisów i oczekujących kategorii jest zapisywany razem w jednym zapisie SharedPreferences.

## Testy regresyjne

Dodano testy JVM dla:
- niepełnego pierwszego odczytu,
- odzyskania wcześniej niedostępnej kategorii,
- wykrywania nowych ocen w dostępnej kategorii podczas częściowej synchronizacji,
- zachowania znanych wpisów podczas późniejszej awarii,
- odróżnienia pustej, ale dostępnej kategorii od niedostępnej.

## Wersja

- versionCode: 9
- versionName: 0.3.6
- applicationId i klucz podpisu wydawniczego bez zmian.

## Ograniczenia

Powiadomienia nadal korzystają z WorkManager i cyklu okresowego (minimum 15 minut); system Android może opóźniać wykonanie. Nie są to natychmiastowe powiadomienia push.
