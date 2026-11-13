# Librus All-in-One — backend integracyjny

To jest lekki, bezstanowy adapter HTTPS oparty na nieoficjalnej bibliotece `librus-synergia`.

## Ważne zasady bezpieczeństwa

- Backend nie zapisuje loginów, haseł, ciasteczek sesji ani danych szkolnych do plików lub bazy.
- Aplikacja Android musi przechowywać login/hasło i osobną sesję każdego profilu lokalnie, z szyfrowaniem opartym o Android Keystore.
- Każde żądanie wysyła dane profilu i jego sesję przez HTTPS. Hasło jest potrzebne, bo Librus może wymagać ponownego logowania po wygaśnięciu sesji.
- Profil rodzica i ucznia nie mogą współdzielić ciasteczek.
- Adapter umożliwia odczyt danych oraz wysyłanie zwykłych wiadomości przez moduł Wiadomości. Nie zmienia wpisów frekwencji i nie wysyła formalnych e-Usprawiedliwień — to odrębny moduł Librusa, zależny od konfiguracji szkoły. Wiadomość do wychowawcy może służyć jako prośba o usprawiedliwienie, ale jej wysłanie nie zmienia statusu nieobecności.

## Endpointy

- `GET /health`
- `POST /v1/connect` — sprawdza logowanie i zwraca dane konta oraz sesję.
- `POST /v1/sync` — pobiera dostępny zestaw danych i zwraca odświeżoną sesję.
- `POST /v1/message/recipients` — pobiera odbiorców dostępnych dla konta (w tym wychowawcę).
- `POST /v1/message/send` — wysyła wiadomość do wskazanych odbiorców. Wysyłka jest wykonywana tylko raz; przy niejednoznacznej odpowiedzi Librusa aplikacja nie może ponawiać jej automatycznie.
- `POST /v1/message/reply` — odpowiada w istniejącym wątku, tylko jeśli Librus dopuszcza odpowiedź; również bez automatycznych ponowień.
- `POST /v1/message/attachment` — pobiera załącznik wiadomości i zwraca go w Base64, bez zapisywania pliku na serwerze.
- `POST /v1/homework/attachment` — pobiera załącznik zadania domowego; limit 20 MB, bez zapisywania pliku na serwerze.
- `POST /v1/justification/submit` — składa formalne e-Usprawiedliwienie w nieoficjalnym endpointcie modułu Librusa (zakres dat, lekcje, powód i powiadomienie nauczycieli); nie ponawia niejednoznacznych wysyłek. Dostępność zależy od szkoły i konta.

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
