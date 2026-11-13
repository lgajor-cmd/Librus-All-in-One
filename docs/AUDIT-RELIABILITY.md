# Audyt techniczny i poprawki niezawodności

Data: 2026-11-13

## Zakres

Zmiany dotyczące stabilności synchronizacji w tle, rozróżniania błędów autoryzacji, kontroli gotowości backendu oraz testów regresyjnych. Nie zmieniają formatu danych logowania, identyfikatora aplikacji ani klucza podpisu APK.

## Wdrożone poprawki

### 1. Niezależne pobieranie kanałów ocen

Endpoint `POST /v1/sync` w trybie `notifications_only` pobiera dodatkowe kanały ocen przez `asyncio.gather(return_exceptions=True)`. Błąd jednego kanału nie przerywa pobierania pozostałych. Lista `data.failed_sections` informuje klienta, których kategorii nie udało się pobrać.

Aplikacja nie interpretuje brakującej/nieudanej kategorii jako pustej listy. Dzięki temu chwilowa awaria Librusa nie powinna powodować fałszywych powiadomień po kolejnym udanym odpytywaniu.

### 2. Kody błędów autoryzacji

Backend dodaje nagłówek `X-Librus-Error-Code` do kontrolowanych błędów sesji Supabase i Librusa. Treść `detail` pozostaje tekstowa dla zgodności z istniejącymi ekranami.

Worker Android rozróżnia:
- wygaśnięcie/nieprawidłowość sesji aplikacji Supabase,
- błąd logowania lub wymagane działanie po stronie Librusa,
- nierozpoznany błąd autoryzacji, który nie powinien być zgłaszany jako błędne hasło Librusa.

### 3. Diagnostyka częściowej synchronizacji

Worker zapisuje status `partial_success`, nazwy niedostępnych kanałów i zalicza takie sprawdzenie jako częściowo udane. Błędy częściowe pozostają widoczne w liczniku diagnostyki.

### 4. Liveness i readiness

- `GET /health` pozostaje lekkim testem życia procesu i jest nadal używany przez Render.
- `GET /ready` sprawdza, czy wymagane zmienne konfiguracji Supabase są obecne. Nie wykonuje kosztownego żądania sieciowego do Supabase i nie potwierdza poprawności klucza ani dostępności zewnętrznej usługi.

### 5. Ograniczenie pamięci limitera

Lokalny limiter usuwa nieaktywne wpisy po przekroczeniu progu liczby adresów IP. Nadal jest limiterem procesowym, a nie współdzielonym limitem pomiędzy wieloma instancjami.

### 6. Diagnostyka zaszyfrowanego magazynu danych

Magazyn Androida rozróżnia brak konfiguracji od brakującego klucza Keystore, błędu uwierzytelnienia AES-GCM i uszkodzonego formatu. Worker zapisuje szczegółowy status diagnostyczny i nie usuwa ani nie nadpisuje danych po błędzie odszyfrowania.

### 7. Testy regresyjne

Dodano testy standardowej biblioteki `unittest` dla:
- poprawnego pobrania wszystkich kanałów ocen,
- odporności na awarię pojedynczego kanału,
- odpowiedzi readiness przy kompletnej i niekompletnej konfiguracji,
- stabilnego kodu błędu autoryzacji.

Workflow backendu uruchamia te testy w CI. Kompilacja Androida w CI weryfikuje również zmiany magazynu danych logowania.

## Ograniczenia i dalsze działania

1. Darmowa usługa Render może zasypiać po okresie bez ruchu. Nie zmieniamy planu na płatny automatycznie. Dla możliwie szybkich i regularnych powiadomień należy rozważyć usługę bez usypiania.
2. WorkManager jest harmonogramem okresowym Androida, a nie usługą push. Doze, oszczędzanie baterii i ograniczenia producenta telefonu mogą opóźnić sprawdzenie.
3. Limiter pamięciowy jest lokalny dla procesu. Przy skalowaniu backendu należy zastosować współdzielony limiter (np. Redis/managed KV) i jawnie skonfigurować zaufane proxy.
4. Testy backendu nie zastępują testów end-to-end na koncie testowym Librusa.
5. `MainActivity.kt` wymaga stopniowego wydzielenia ekranów i logiki domenowej. Refaktoryzacja powinna być wykonywana etapami, z testami i bez jednoczesnej zmiany zachowania UI.
6. Dodano oficjalny Gradle Wrapper 8.11.1 (skrypty i JAR z repozytorium Gradle); CI używa teraz `./gradlew`. Nadal potrzebny jest sprawdzony zestaw testów UI Androida.

## Wdrożenie

Zmiany przygotowane na gałęzi `audit/reliability-and-test-hardening`. Nie należy scalać ich do `main` ani publikować APK, dopóki wymagane zadania GitHub Actions nie zakończą się powodzeniem.
