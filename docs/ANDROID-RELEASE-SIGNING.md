# Podpisywanie wydań Android

Repozytorium jest publiczne, dlatego prywatny klucz podpisu nie może być przechowywany w kodzie ani w repozytorium.

## Jednorazowa konfiguracja GitHub Actions

W repozytorium otwórz **Settings → Secrets and variables → Actions → New repository secret** i dodaj:

- `ANDROID_KEYSTORE_BASE64` – zawartość pliku Base64 z pakietu klucza wydawniczego
- `ANDROID_KEYSTORE_PASSWORD` – hasło magazynu kluczy
- `ANDROID_KEY_ALIAS` – alias klucza
- `ANDROID_KEY_PASSWORD` – hasło klucza

Po zapisaniu wszystkich czterech sekretów kompilacja na gałęzi `main` i ręczne uruchomienie workflow będą tworzyły artefakt **Librus-All-in-One-APK-RELEASE** podpisany stałym kluczem.

Jeśli sekrety nie są skonfigurowane, CI nadal uruchomi lint i testy oraz zbuduje artefakt **DEBUG-ONLY**. Taki plik jest wyłącznie do testów i nie powinien być traktowany jako aktualizacja wydania.

## Ochrona klucza

Odcisk SHA-256 certyfikatu przygotowanego klucza: `3F:CA:39:0F:45:0C:25:E6:11:89:EA:60:59:78:CE:48:60:A0:48:2A:7A:95:F7:48:40:6E:5E:D9:EE:04:6E:94`. Workflow sprawdza ten odcisk po zbudowaniu APK, aby wykryć przypadkowe użycie innego klucza.

- Zachowaj kopię pliku JKS i haseł w bezpiecznym miejscu.
- Nie wysyłaj klucza do repozytorium, issue ani pull requestów.
- Nie generuj nowego klucza dla każdej wersji. Wszystkie przyszłe wydania muszą używać tego samego klucza.
- Utrata klucza oznacza brak możliwości aktualizacji aplikacji podpisanej tym kluczem.

## Ważna migracja ze starych kompilacji debug

Starsze APK z GitHub Actions były podpisywane automatycznym kluczem debug tworzonym na efemerycznym runnerze. Klucz prywatny nie jest dostępny w repozytorium ani w samym APK. Nowe wydanie release będzie miało stały podpis, więc Android może odmówić instalacji go jako aktualizacji nad starą instalacją debug.

Nie odinstalowuj starej aplikacji, dopóki nie sprawdzisz możliwości aktualizacji i nie zabezpieczysz potrzebnych danych. Manifest ma `allowBackup=false`, więc odinstalowanie usuwa lokalne dane aplikacji. W razie konieczności migracji trzeba najpierw przewidzieć ponowne logowanie i odtworzenie lokalnych ustawień.
