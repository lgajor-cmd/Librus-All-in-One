# EduFlow 0.4.0

## Nowości

- Dodano profile rodzinne: wiele kont rodziców i uczniów w jednej instalacji.
- Każdy profil ma oddzielne szyfrowane poświadczenia, sesję Librusa, dane szkolne i stan powiadomień.
- Dodano przełączanie profili z pulpitu, menu „Więcej” oraz ekranu logowania.
- Powiadomienia prowadzą bezpośrednio do profilu, którego dotyczą.
- Usuwanie profilu nie usuwa pozostałych kont.
- Usunięto ręczny przycisk „Sprawdź serwer” z pulpitu; dostępność backendu jest weryfikowana przy rzeczywistych operacjach.
- Zmieniono nazwę aplikacji na EduFlow.

## Zachowana zgodność

- Zachowano Android applicationId `pl.domreksio.librusallinone`.
- Zachowano istniejący klucz podpisu wydawniczego.
- Dotychczasowy magazyn poświadczeń `parent` jest nadal używany jako profil rodzica, bez ponownego logowania po aktualizacji.
- Supabase i backend pozostają bez zmian.

## Kontrola jakości

- Android Lint: zaliczony.
- Testy jednostkowe Androida: zaliczone.
- APK RELEASE: zbudowany.
- Certyfikat APK: zweryfikowany względem stałego SHA-256.
