# Audyt pogłębiony — wydanie 0.3.5

Data: 2026-11-13

## Zakres kontroli

Przegląd kodu Androida, synchronizacji backendu, harmonogramu powiadomień, obsługi sesji, magazynu danych logowania, terminarza i konfiguracji CI.

## Ustalenia i poprawki

### Kalendarz terminarza

- Kalendarz miesiąca uwzględnia teraz daty z obu dostępnych źródeł Librusa: `agenda` i `events`.
- Dni z wydarzeniami szkolnymi i prywatnymi mają odrębne znaczniki.
- Po wybraniu dnia wydarzenia szkolne z Librusa są pokazywane bezpośrednio pod kalendarzem, obok prywatnych wydarzeń.
- Komórki dni zwiększono z 62 dp do 70 dp, aby poprawić czytelność na wąskich ekranach.
- Lista wydarzeń wybiera pierwsze źródło zawierające dane, zamiast pustej sekcji, jeśli Librus zwrócił ją obok niepustego alternatywnego źródła.

### Ograniczanie liczby żądań API

Audyt wykazał, że backend weryfikował token Supabase przed sprawdzeniem limitu żądań. Przeniesiono limiter przed zdalną weryfikację tokenu, dzięki czemu nieprawidłowe żądania również podlegają limitowi i nie wymuszają niepotrzebnego wywołania Supabase. Dodano test regresyjny limitu 10 żądań na minutę na adres IP.

## Potwierdzone kontrole

- CI Android: Android Lint, testy jednostkowe, budowanie release i weryfikacja certyfikatu.
- CI backend: kontrola zależności, pip-audit, kompilacja, testy regresyjne i sprawdzenie tras API.
- Dane Librusa pozostają lokalnie szyfrowane przez AES-GCM i Android Keystore.
- Aplikacja nadal nie zezwala na ruch cleartext HTTP.

### Znacznik aktualnej lekcji

Naprawiono indeksowanie znacznika czasu w planie lekcji: po zakończeniu ostatniej lekcji linia „TERAZ” była ograniczana do indeksu przed ostatnią kartą. Teraz może zostać umieszczona za wszystkimi lekcjami.

### Dodatkowa kontrola nawigacji motywów

Naprawiono przypadek, w którym zmiana motywu otwarta z sekcji „Więcej” (np. z notatek lub listy zakupów) próbowała otworzyć nieistniejącą sekcję Librusa zamiast wrócić do pulpitu.

## Ograniczenia audytu

- Brak automatycznych testów UI/E2E na rzeczywistym urządzeniu i koncie Librusa.
- Powiadomienia korzystają z okresowego WorkManager (minimum 15 minut); Android i oszczędzanie baterii mogą opóźnić wykonanie.
- Bez danych z rzeczywistego konta nie można potwierdzić, że każda wersja odpowiedzi Librusa używa dokładnie tych samych nazw pól daty.
- Backend na planie Render Free może zasypiać. Pierwsze żądanie po uśpieniu może trwać dłużej.
- Główny ekran nadal jest dużym plikiem `MainActivity.kt`; rozdzielenie warstw UI i logiki należy wykonać osobno, etapami, z testami.

## Wersja

- versionCode: 8
- versionName: 0.3.5
- applicationId pozostaje bez zmian.
- Klucz podpisu wydawniczego pozostaje bez zmian.
