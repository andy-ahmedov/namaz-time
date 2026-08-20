# Mosque Prayer TV

> Документированный технический каркас Android TV-приложения, которое будет показывать в мечети время азана и икамата, обратный отсчёт, объявления и QR-коды.

## Что изменилось после анализа APK

Статический clean-room анализ переданного APK `IslamApp для Мечети 1.6.2` подтвердил, что конкурент **не парсит сайты ДУМ при каждом открытии и не получает каждое время намаза отдельным запросом**. В приложении используется гибридная схема:

1. в APK лежит зашифрованный региональный snapshot параметров;
2. приложение периодически проверяет новую версию этого snapshot;
3. выбранные координаты сопоставляются с географическими полигонами;
4. для полигона используется либо готовая дневная таблица, либо локальный астрономический расчёт по региональному профилю;
5. если профиль не найден, применяется расчётный fallback;
6. результат работает офлайн.

Полный разбор: [APK_RESEARCH_ISLAMAPP_1_6_2.md](APK_RESEARCH_ISLAMAPP_1_6_2.md).

## Главный принцип собственного продукта

Приложение не должно выдавать астрономический расчёт, импорт или HTML-парсинг за «официальное время ДУМ». Каждое расписание хранится вместе с происхождением, географической областью, часовым поясом, датой получения, контрольной суммой и статусом одобрения мечетью.

## Предлагаемая архитектура

- **TV-клиент:** Kotlin, Jetpack Compose for TV, Room/SQLite, DataStore, WorkManager.
- **Backend и ingest:** Go, сначала модульный монолит.
- **БД backend:** PostgreSQL.
- **Админ-панель:** сначала server-rendered Go UI.
- **Доставка на TV:** подписанные версионированные snapshots, ETag, last-known-good и локальный кэш.
- **Fallback-расчёт:** проверенная библиотека, но только как явно одобренный источник, а не скрытая замена официальной таблицы.

## Как начать

1. Прочитать [START_HERE.md](START_HERE.md).
2. Заполнить блокирующие решения в [DECISIONS.md](DECISIONS.md).
3. Установить Go 1.24, JDK 17 и Android SDK 35.
4. Выполнить проверки каркаса:

   ```bash
   make docs-check
   make test-go
   make test-contracts
   make test-android-unit
   make lint
   ```

5. Проверить локально завершённые Phase 1/T009 и блокеры T010 в
   [PLANS.md](PLANS.md).

Реальный pilot fixture можно безопасно прогнать до candidate/diff без
одобрения и signing key:

```bash
go run ./cmd/ingestor inspect \
  --fixture-dir fixtures/pilot/ulyanovsk-2026-08
```

Команда не имеет approve/publish режима и возвращает `needs_review`.

Gradle запускается через репозиторный wrapper. Android application ID
`com.example.namaztime.tv` является временным значением T001 и должен быть
заменён после решения D-005.

## Карта документов

| Документ | Назначение |
|---|---|
| [RESEARCH_REPORT.md](RESEARCH_REPORT.md) | итоговый исследовательский отчёт |
| [APK_RESEARCH_ISLAMAPP_1_6_2.md](APK_RESEARCH_ISLAMAPP_1_6_2.md) | статический разбор APK и подтверждённая внутренняя схема |
| [PRAYER_TIME_SOURCE_PATTERNS.md](PRAYER_TIME_SOURCE_PATTERNS.md) | как IslamApp и другие приложения получают времена; ответ про парсинг |
| [MAWAQIT_API_RESEARCH.md](MAWAQIT_API_RESEARCH.md) | актуальный разбор официального authenticated API-клиента MAWAQIT и стороннего scraping |
| [COMPETITOR_RESEARCH.md](COMPETITOR_RESEARCH.md) | сравнительная матрица продуктов |
| [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) | продуктовые требования и MVP |
| [PRAYER_TIMES_DATA.md](PRAYER_TIMES_DATA.md) | источники, приоритеты, provenance и fallback |
| [ARCHITECTURE.md](ARCHITECTURE.md) | целевая архитектура и потоки данных |
| [DATA_MODEL.md](DATA_MODEL.md) | доменная модель и инварианты |
| [API_CONTRACT.md](API_CONTRACT.md) | контракт TV ↔ backend |
| [UI_UX_SPEC.md](UI_UX_SPEC.md) | экраны и TV-специфика |
| [TEST_STRATEGY.md](TEST_STRATEGY.md) | тестирование времени, snapshots и TV |
| [SECURITY_PRIVACY.md](SECURITY_PRIVACY.md) | угрозы, разрешения и приватность |
| [OPERATIONS.md](OPERATIONS.md) | offline, часы устройства, мониторинг и инциденты |
| [BLACK_BOX_VALIDATION_PLAN.md](BLACK_BOX_VALIDATION_PLAN.md) | оставшийся runtime-этап на реальном TV/box |
| [ANDROID_TV_RUNTIME_SETUP_WSL.md](ANDROID_TV_RUNTIME_SETUP_WSL.md) | подключение тестового Android TV к ADB из WSL и безопасный сбор evidence |
| [SOURCE_PARTNERSHIP_CHECKLIST.md](SOURCE_PARTNERSHIP_CHECKLIST.md) | получение разрешения и формата у ДУМ/мечети |
| [CODEX_WORKFLOW.md](CODEX_WORKFLOW.md) | работа из WSL + VS Code + Codex CLI |
| [CODEX_TASKS.md](CODEX_TASKS.md) | первые ограниченные задачи для Codex |
| [PLANS.md](PLANS.md) | этапы и журнал выполнения |
| [SOURCES.md](SOURCES.md) | реестр публичных источников |

Полный перечень: [MANIFEST.md](MANIFEST.md).

## Статус

T001 добавил компилируемые Go entry points, Android/Gradle scaffold и CI. T002
добавил source-independent Go-модель snapshot и JSON Schema parity tests. T003
добавил запускаемый Compose for TV shell, проверяемый D-pad путь, DataStore
настроек и Room schema v1. T004 добавил строгую Android-валидацию snapshot,
offline bootstrap встроенного синтетического примера, атомарный import/activate,
безопасную диагностику/previous restore и миграции Room v1→v2→v3 для source
flags и полной provenance/theme metadata. T005 добавил адаптивный главный
экран с шестью строками азана/икамата, offline-фоном, source state и D-pad
переходом к настройкам. T006 добавил mosque-timezone clock, разрешение правил
икамата, отдельные пятничные сессии, точный countdown и безопасные состояния
для отсутствующего покрытия/DST-конфликтов. T007 добавил локальную генерацию
QR, строгую HTTPS/lifecycle-валидацию, безопасное скрытие невалидных и
просроченных кампаний и operator preview без сетевого разрешения. T008 добавил
строгий manual CSV provider, raw/transcription/normalized provenance,
детерминированный diff, approval gate, canonical Ed25519 signing и
cross-platform Go/Android verification. Реальное расписание Ульяновска
сохранено как `needs_review`, потому что D-002 не называет religious approver;
оно не опубликовано и не названо официальным. Локальный Phase 1 завершён.
T009 добавил явно ephemeral/test-only one-use pairing fixture и bearer-scoped
Go read API, ETag/304 и Digest, полную registry-time signature/schema/mosque
validation, Android Keystore/AES-GCM provisioning, same-origin HTTPS,
provisioning-scoped durable stage/quarantine, WorkManager и атомарный
activate/rollback с file-backed process-interruption тестами. Ни test token, ни
private/production signing key в APK/Git не встроены; T010 заблокирован на
D-002, полном годовом источнике, D-013 и
физическом canary/rollback drill.
T011 добавляет независимое PostgreSQL-хранилище production pairing: случайные
одноразовые коды и device tokens, только hash/HMAC at rest, expiry/rate limits,
атомарный single-use redeem, revocation, mosque-scoped composite constraints и
append-only audit. Реальные container tests проверяют concurrency и restart.
T012 добавляет migration v2, verifier-only admin tokens, global/local RBAC,
mosque-scoped list/issue/revoke/assignment API, durable idempotency с ротацией
HMAC key ring и повторную registry-проверку assignment на device read path.
Schema-owner DSN используется только отдельной короткоживущей migration-командой;
На checkpoint T012 API запускался с runtime role и read-only проверял
точную версию v2; T013/T014 поднимают тот же fail-closed контракт
до v4.
Первичный admin bootstrap остаётся привилегированной out-of-band операцией без
сетевого shortcut. Наличие T011/T012 не снимает блокировку T010.
T013 добавляет bearer/path-scoped privacy-safe heartbeat: PostgreSQL хранит
только последнее health-состояние и server last-seen, admin list остаётся в
mosque RBAC, а Android best-effort reporter не влияет на sync/display result.
T014 добавляет bounded canary cohorts: группа до 100 устройств
атомарно получает только уже верифицированный snapshot, а rollback
создаёт новые monotonic manifest versions без частичного commit.
T015 даёт mosque-scoped `device-support-bundle/v1`: только текущие
device/assignment/latest-health поля, `no-store`, без secrets, URLs,
сетевых identifiers, логов и новой истории.
Runtime-проверка на физическом Android TV/box ещё не выполнена и не
подменяется Robolectric-тестом.
