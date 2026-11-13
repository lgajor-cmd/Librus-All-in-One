# Librus All-in-One

Aplikacja Android z własnym backendem FastAPI, logowaniem użytkownika przez Supabase i bezstanową komunikacją z Librus Synergia przez Render.

## Architektura

- Android: natywna aplikacja Kotlin.
- Uwierzytelnianie aplikacji: Supabase Auth (PKCE).
- Backend: FastAPI na Render.
- Librus: `librus-synergia` 0.3.30; sesja Librusa przekazywana przez klienta i nieprzechowywana po stronie serwera.
- Dane dostępne po synchronizacji: profil, oceny, frekwencja, plan lekcji, wiadomości, ogłoszenia, zadania domowe, terminarz, uwagi i szczęśliwy numerek — zależnie od uprawnień konta i dostępności sekcji w Librusie.

## Funkcje aplikacji

- Konto aplikacji i oddzielne połączenie z kontem Librus.
- Bezpieczne lokalne przechowywanie poświadczeń Librusa.
- Synchronizacja danych i aktualizacja zapisanej sesji.
- Oceny, frekwencja, plan lekcji i wiadomości.
- Sekcje zadań domowych, terminarza, uwag oraz szczęśliwego numerka.
- Pobieranie pełnej treści wiadomości na żądanie.

Niektóre sekcje mogą być puste, jeśli Librus nie udostępnia ich na danym koncie. Pełna treść wiadomości może zostać oznaczona jako przeczytana przez Librusa.

## Budowanie

GitHub Actions uruchamia `gradle assembleDebug` i publikuje APK jako artefakt workflow.

## Informacje o źródłach

Szczegóły zależności i przeglądanych projektów zewnętrznych znajdują się w [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
