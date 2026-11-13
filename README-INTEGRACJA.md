# Librus All-in-One — backend integracyjny

To jest pierwszy, tylko do odczytu adapter HTTPS oparty na nieoficjalnej bibliotece `librus-synergia`.

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
