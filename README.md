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
   make test-android-unit
   make lint
   ```

5. Продолжить с задачей `T002` из [CODEX_TASKS.md](CODEX_TASKS.md).

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

T001 добавил компилируемые Go entry points, минимальный Android TV launcher,
unit-тесты, Gradle wrapper и CI. Это всё ещё **технический scaffold, а не готовое
приложение**: молитвенная доменная модель начинается в T002, а Compose/Room UI —
в T003. Статический APK-анализ завершён. Runtime-проверка на физическом Android
TV/box ещё не выполнена и не подменяется предположениями.
