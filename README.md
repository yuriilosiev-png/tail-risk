# Хвостовой риск / Tail Risk

Трекер стратегии хвостов: покупка дальних OTM-опционов ETH/BTC при низкой воле,
фиксация по цели ×N, статистика по каждому циклу. Bybit и Deribit, ключи только на чтение.

- Веб-версия: https://yuriilosiev-png.github.io/tail-risk/
- Android: WebView-оболочка, грузит ту же страницу; пуши через Worker из Crypto Options.
- Пакет: `com.options.tailrisk`

## Секреты репозитория (Settings → Secrets and variables → Actions)

| Секрет | Что положить |
|---|---|
| `KEYSTORE_PASSWORD` | Пароль ключа подписи, от 12 символов. Сохрани его отдельно — без него не выпустить обновление. |
| `KEYSTORE_BASE64` | Строка из лога workflow «Generate Keystore» (между НАЧАЛО и КОНЕЦ). |
| `GOOGLE_SERVICES_JSON` | Содержимое `google-services.json` из Firebase целиком, как текст. |

## Сборка

Actions → Build APK + AAB → Run workflow. Артефакты: `TailRisk-APK` (на телефон), `TailRisk-AAB` (в Play).
Перед каждой загрузкой в Play увеличивай `versionCode` в `app/build.gradle`.

## Не трогать без необходимости

- `APP_URL` в `MainActivity.java` — адрес страницы на GitHub Pages.
- `WORKER_URL` в `index.html` — общий Worker с Crypto Options.
