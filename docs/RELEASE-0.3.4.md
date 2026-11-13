# Librus All-in-One 0.3.4

Version code: 7

## Zmiany

- Stabilniejsza synchronizacja powiadomień o ocenach: błąd pojedynczego źródła nie przerywa pozostałych pobrań.
- Aplikacja rozróżnia problemy sesji technicznej Supabase od błędów logowania do Librusa.
- Diagnostyka informuje o częściowo udanej synchronizacji i wskazuje niedostępne kanały.
- Dodano endpoint gotowości backendu `/ready`.
- Dodano backendowe testy regresyjne uruchamiane przez GitHub Actions.

## Aktualizacja

To wydanie zachowuje dotychczasowy `applicationId` i stały certyfikat wydawniczy. Powinno aktualizować poprzednią wersję RELEASE podpisaną tym samym kluczem. Nie odinstalowuj wcześniejszej aplikacji, jeśli instalator zgłosi konflikt podpisu lub jeśli nie masz zabezpieczonych danych logowania.

## Status publikacji

Wersja przygotowana w PR #46. Nie jest wydaniem produkcyjnym, dopóki PR nie zostanie scalony, a końcowy workflow nie potwierdzi podpisu i kompilacji APK.
