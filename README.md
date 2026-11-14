# EduFlow

## Najnowsze wydanie — 0.4.9

- Rozbudowane centrum „Więcej” o bezpośrednie skróty do ocen, planu lekcji i wiadomości.
- Zachowano dotychczasowe moduły szkolne, notatki, listy zakupów, terminarz i ustawienia.
- Numer wersji Android: `versionCode 22`.

EduFlow to niezależna aplikacja Android do wygodnego przeglądania szkolnych informacji z kont Librus Synergia. Projekt wykorzystuje własny backend FastAPI, anonimową sesję urządzenia przez Supabase i komunikację HTTPS z usługą Render.

## Architektura

- Android: natywna aplikacja Kotlin.
- Uwierzytelnianie techniczne: anonimowa sesja Supabase Auth przypisana do instalacji urządzenia — użytkownik nie rejestruje się e-mailem.
- Backend: FastAPI na Render; endpointy API wymagają prawidłowego tokenu Supabase.
- Librus: biblioteka `librus-synergia` 0.3.30; sesje Librusa są przekazywane przez klienta i nie są przechowywane po stronie serwera.
- Dane dostępne po synchronizacji zależą od uprawnień konta i dostępności sekcji w dzienniku.

## Profile rodzinne

- Jedna instalacja może przechowywać wiele kont rodziców i uczniów/dzieci.
- Każdy profil ma własną nazwę, typ konta, login, hasło, sesję Librusa i dane szkolne.
- Dane logowania każdego profilu są szyfrowane osobnym kluczem AES-256-GCM przechowywanym w Android Keystore.
- Przełączanie profili nie łączy ocen, frekwencji, planu lekcji ani terminarzy różnych kont.
- Powiadomienia i stan wykrytych nowości są przypisane do właściwego profilu.
- Usunięcie profilu usuwa tylko jego lokalne poświadczenia i sesję; pozostałe profile pozostają bez zmian.
- Istniejące konto z poprzednich wersji jest automatycznie zachowywane jako profil rodzica o wewnętrznym identyfikatorze `parent`.

## Funkcje

- Oceny, w tym oceny opisowe i symulator średniej.
- Frekwencja i kalendarz obecności.
- Plan lekcji z oznaczeniem aktualnej lekcji/przerwy.
- Wiadomości i ogłoszenia.
- Zadania domowe, terminarz, uwagi i szczęśliwy numerek.
- Pełna treść wiadomości pobierana na żądanie.
- Automatyczna synchronizacja w tle i powiadomienia, jeśli Android pozwala na pracę w tle.
- Kontrola połączenia wykonywana podczas rzeczywistych operacji; pulpit nie wymaga ręcznego sprawdzania serwera.

Niektóre funkcje, szczególnie operacje dostępne wyłącznie dla rodzica, wymagają profilu rodzica. Konto ucznia jest odrębnym profilem i nie otrzymuje uprawnień rodzicielskich.

## Bezpieczeństwo i migracja

- Nie zmieniamy identyfikatora aplikacji Androida `pl.domreksio.librusallinone`, aby zachować możliwość aktualizacji istniejącej instalacji.
- Nie zmieniamy projektu Supabase, backendu ani klucza podpisu APK.
- Dane logowania Librusa są przechowywane lokalnie w zaszyfrowanym magazynie.
- Sesja Supabase jest wspólna dla instalacji urządzenia; po wyczyszczeniu danych aplikacji lub reinstalacji wymaga ponownego utworzenia.

## Budowanie i kontrola jakości

Projekt zawiera Gradle Wrapper 8.11.1. Lokalnie budowanie uruchamiaj przez `./gradlew assembleDebug` (Linux/macOS) lub `gradlew.bat assembleDebug` (Windows).

GitHub Actions uruchamia Android Lint, testy jednostkowe, budowanie APK RELEASE oraz weryfikację stałego odcisku certyfikatu podpisu. Backend ma osobny workflow kontroli zależności, `pip-audit`, kompilacji i testów regresyjnych.

Szczegóły niezawodności oraz znane ograniczenia opisuje [audyt techniczny](docs/AUDIT-RELIABILITY.md).

## Informacje o źródłach

Szczegóły zależności i przeglądanych projektów zewnętrznych znajdują się w [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
