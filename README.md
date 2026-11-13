# Librus All-in-One

Aplikacja Android z własnym backendem FastAPI, automatyczną anonimową sesją urządzenia przez Supabase i bezstanową komunikacją z Librus Synergia przez Render.

## Architektura

- Android: natywna aplikacja Kotlin.
- Uwierzytelnianie techniczne: anonimowa sesja Supabase Auth — użytkownik nie rejestruje się e-mailem ani nie potwierdza adresu.
- Backend nadal weryfikuje token Supabase; endpointy API nie są publicznie dostępne bez sesji.
- W panelu Supabase musi być włączona opcja Anonymous Sign-Ins (Authentication → Sign In / Providers → Anonymous).
- Backend: FastAPI na Render.
- Librus: `librus-synergia` 0.3.30; sesja Librusa przekazywana przez klienta i nieprzechowywana po stronie serwera.
- Dane dostępne po synchronizacji: profil, oceny, frekwencja, plan lekcji, wiadomości, ogłoszenia, zadania domowe, terminarz, uwagi i szczęśliwy numerek — zależnie od uprawnień konta i dostępności sekcji w Librusie.

## Funkcje aplikacji

- Brak osobnej rejestracji i logowania e-mail; połączenie bezpośrednio danymi Librusa.
- Supabase tworzy w tle anonimową sesję urządzenia. Po wyczyszczeniu danych aplikacji lub reinstalacji sesja nie może zostać odzyskana — użytkownik tworzy nową i ponownie łączy Librusa.
- Bezpieczne lokalne przechowywanie poświadczeń Librusa.
- Synchronizacja danych i aktualizacja zapisanej sesji.
- Oceny, frekwencja, plan lekcji i wiadomości.
- Sekcje zadań domowych, terminarza, uwag oraz szczęśliwego numerka.
- Pobieranie pełnej treści wiadomości na żądanie.

Niektóre sekcje mogą być puste, jeśli Librus nie udostępnia ich na danym koncie. Pełna treść wiadomości może zostać oznaczona jako przeczytana przez Librusa.

## Budowanie i kontrola jakości

GitHub Actions uruchamia Android Lint, testy jednostkowe Androida, budowanie APK i weryfikację stałego podpisu wydawniczego. Backend ma osobny workflow: kontrolę zależności, `pip-audit`, kompilację i testy regresyjne `unittest`.

Szczegóły zmian niezawodności oraz znane ograniczenia opisuje [audyt techniczny](docs/AUDIT-RELIABILITY.md).


## Informacje o źródłach

Szczegóły zależności i przeglądanych projektów zewnętrznych znajdują się w [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
