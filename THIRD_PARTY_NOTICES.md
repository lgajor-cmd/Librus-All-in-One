# Informacje o wykorzystanych projektach

## Szkolny.eu Android

- Repozytorium: https://github.com/szkolny-eu/szkolny-android
- Organizacja: SzkolnyEU
- Licencja upstream: GNU GPL-3.0
- Zakres analizy: obsługa Librus Synergia, oceny, frekwencja, plan, wiadomości, zadania domowe, terminarz, uwagi, szczęśliwy numerek i obsługa załączników.

W tej zmianie nie skopiowano plików źródłowych ani fragmentów kodu Szkolny.eu. Zastosowano niezależną implementację widoków i przepływu funkcji w oparciu o publicznie opisane zachowanie aplikacji oraz strukturę danych zwracaną przez bibliotekę `librus-synergia`.

Jeżeli w przyszłości do repozytorium zostaną włączone fragmenty kodu Szkolny.eu, należy zachować ich oryginalne nagłówki copyright, dołączyć GPL-3.0 oraz spełnić warunki tej licencji dla utworzonego utworu pochodnego.

## librus-synergia

- Repozytorium: https://github.com/MichalZaniewicz/librus-synergia
- Autor: Michał Zaniewicz
- Licencja: MIT
- Używana zależność backendu: `librus-synergia==0.3.30`

Biblioteka jest niezależną zależnością backendu i nie jest częścią kodu Android.
