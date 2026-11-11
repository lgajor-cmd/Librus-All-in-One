# Librus All-in-One — backend integracyjny

To jest pierwszy, tylko do odczytu adapter HTTPS oparty na nieoficjalnej bibliotece `librus-synergia`.

## Ważne zasady bezpieczeństwa

- Backend nie zapisuje loginów, haseł, ciasteczek sesji ani danych szkolnych do plików lub bazy.
- Aplikacja Android musi przechowywać login/hasło i osobną sesję każdego profilu lokalnie, z szyfrowaniem opartym o Android Keystore.
- Każde żądanie wysyła dane profilu i jego sesję przez HTTPS. Hasło jest potrzebne, bo Librus może wymagać ponownego logowania po wygaśnięciu sesji.
- Profil rodzica i ucznia nie mogą współdzielić ciasteczek.
- Pierwsza wersja tylko odczytuje dane. Nie wysyła wiadomości, nie usprawiedliwia nieobecności i niczego nie zmienia w dzienniku.

## Endpointy

- `GET /health`
- `POST /v1/connect` — sprawdza logowanie i zwraca dane konta oraz sesję.
- `POST /v1/sync` — pobiera dostępny zestaw danych i zwraca odświeżoną sesję.

Przykładowe żądanie:

```json
{
  "profile": "parent",
  "login": "LOGIN_SZKOLNY",
  "password": "HASLO",
  "session": null
}
```

W kolejnych żądaniach aplikacja przekazuje obiekt `session` zwrócony przez poprzednią odpowiedź. Dla rodzica i ucznia musi przechowywać osobne obiekty.

## Wdrożenie

Plik `render.yaml` opisuje usługę Render. Po dodaniu plików do repozytorium można utworzyć usługę typu Blueprint z repozytorium. Darmowa usługa może usypiać po okresie bezczynności; do stałej dostępności potrzebny jest hosting bez usypiania. Do aplikacji należy wpisać wyłącznie adres HTTPS.

## Ograniczenia

Biblioteka korzysta z nieoficjalnego, prywatnego API Librusa, które może się zmienić. Niektóre moduły mogą nie być dostępne na danym koncie. Librus może wymagać dodatkowej weryfikacji — adapter nie omija CAPTCHA ani zabezpieczeń konta.
