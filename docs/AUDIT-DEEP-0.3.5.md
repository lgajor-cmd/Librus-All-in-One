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

## Potwierdzone kontrole

- CI Android: Android Lint, testy jednostkowe, budowanie release i weryfikacja certyfikatu.
- CI backend: kontrola zależności, pip-audit, kompilacja, testy regresyjne i sprawdzenie tras API.
- Dane Librusa pozostają lokalnie szyfrowane przez AES-GCM i Android Keystore.
- Aplikacja nadal nie zezwala na ruch cleartext HTTP.

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
