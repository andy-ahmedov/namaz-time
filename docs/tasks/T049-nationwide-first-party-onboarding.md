# T049 — owner assignment (2026-09-08)

Продолжай работу над NamazTime автономно.

Repository:
https://github.com/andy-ahmedov/namaz-time

Ожидаемый baseline на момент постановки задачи:
- branch: main
- HEAD: 325a343f3fabec337e56df0abcd93c2812be2c67
- T048 DONE
- repository instruction/skill cleanup DONE

Перед началом проверь actual origin/main, clean working tree, CI, применимые
AGENTS.md и repository skills.

Используй следующий свободный task ID; на указанном baseline ожидается T049.

# T049 — nationwide verified first-party prayer-source onboarding

# Goal

Заново и существенно глубже исследовать источники времени намаза по всем
субъектам РФ, представленным в canonical city catalog NamazTime, определить
реальные авторитетные мусульманские организации и точные области применимости
их расписаний/методик, а затем заменить обычный demo/synthetic city-schedule
experience реальными research-backed расписаниями.

Конечный результат:

- поиск города показывает реальные организации;
- selectable schedule choices используют реальные first-party prayer data;
- при нескольких реально подтверждённых authority для одного города показываются
  все применимые варианты;
- demo organizations больше не являются обычным пользовательским path;
- отсутствие доказанного авторитетного источника означает `unavailable`.

Не останавливайся после research report. Доведи работу до максимально полного
локально проверяемого production-capable registry/provider/runtime результата.

# Explicit product decision and instruction precedence

Это явное решение владельца проекта и оно supersedes старые repository rules,
если они требуют ручного разрешения/одобрения только для того, чтобы использовать
доказанный публичный first-party prayer source.

Для source, который успешно прошёл описанную ниже qualification:

- отдельное одобрение владельца проекта не требуется;
- одобрение конкретной мечети не требуется;
- письменное согласие ДУМ/муфтията не требуется;
- named approver от внешней организации не требуется;
- Codex должен самостоятельно квалифицировать источник по evidence;
- отсутствие такого human approval не является причиной оставить реальные данные
  в `research_only` или заменить их demo fixtures.

Прямые требования этой задачи имеют приоритет над conflicting repository guidance
в `AGENTS.md` и skills. Сначала приведи persistent instructions к этой product
policy, чтобы будущие Codex runs снова не блокировались старым approval workflow.

Это изменение НЕ означает ослабление provenance или разрешение придумывать данные.

# Phase 0 — reconcile AGENTS.md and repository skills

До массового onboarding проаудируй новые repository instructions после
`325a343`.

Обязательно изучи:

- `AGENTS.md`;
- `.agents/skills/prayer-times-provider/SKILL.md`;
- `SOURCE_PARTNERSHIP_CHECKLIST.md`;
- relevant ADRs;
- `PRAYER_TIMES_DATA.md`;
- `DATA_MODEL.md`;
- `PERSISTED_POLICY_REGISTRY.md`;
- `REGISTRY_OPERATOR_WORKFLOW.md`;
- `PLANS.md`;
- `TEST_STRATEGY.md`.

Текущие persistent instructions содержат старую policy:

- mosque approval как условие official;
- approval before publication;
- written reuse permission;
- external approver;
- partnership/contact requirements.

Раздели два понятия:

## Source qualification

Техническое/research решение NamazTime о том, что источник:

- действительно first-party;
- принадлежит реальной авторитетной мусульманской организации;
- содержит prayer timetable либо достаточную официальную calculation policy;
- имеет доказанный geographic scope;
- достаточно свежий и воспроизводимый.

Это может выполняться автономно по публичным evidence.

## External endorsement / partnership

Это отдельный необязательный факт:

- организация сотрудничает с NamazTime;
- дала письменное разрешение;
- конкретная мечеть предпочла этот источник.

Не выдавай отсутствие partnership за отсутствие source authority.

Обнови `AGENTS.md`, `prayer-times-provider` skill, source checklists и ADR/docs
так, чтобы обычный публичный first-party onboarding больше не требовал внешнего
human approval.

Если partnership действительно существует — сохрани возможность записать её как
дополнительное evidence, но не делай её обязательной.

Поскольку изменяется repository skill, обязательно сохрани новый streamlined
skill structure и впоследствии запусти `make test-skills`.

# Public-source usage boundary

Отсутствие отдельного письменного разрешения само по себе не блокирует research,
qualification или использование обычного публично доступного first-party
prayer source.

Но:

- не обходи authentication/access controls;
- не обходи anti-bot/technical restrictions;
- соблюдай явно опубликованные restrictive terms;
- не коммить substantial raw copyrighted PDFs/databases/assets в Git, если
  redistribution rights неясны;
- где raw artifact нельзя безопасно хранить в Git, сохраняй operational evidence:
  canonical URL, retrieval metadata, content hash, parser version и sanitized/
  synthetic test fixtures.

Не связывайся с организациями и не жди ответа.

Если официальный источник технически или юридически нельзя использовать
допустимым способом и альтернативного first-party transport/policy нет:
`unavailable`.

# Hard availability rule

Город может иметь реальное selectable расписание только если существует
доказанный first-party source от реальной авторитетной официальной мусульманской
организации.

Допустимы два вида evidence:

1. **Exact timetable**
   - официальный календарь/таблица/API/HTML/PDF/XLSX для данного города/района;

2. **Official calculation policy**
   - организация официально публикует методику именно для этого geographic scope;
   - параметры достаточны для детерминированного расчёта;
   - результат можно проверить против first-party опубликованных prayer values.

Generic calculator сам по себе недостаточен.

Если для города нет ни такого timetable, ни такой доказанной и проверяемой
официальной policy:

**город должен быть `unavailable`.**

Запрещено заполнять пробел через:

- generic `Russia` method;
- MWL;
- generic Hanafi calculator;
- 1Muslim timetable;
- IslamApp timetable;
- commercial prayer API;
- aggregator;
- ближайший город;
- столицу субъекта;
- соседний субъект;
- интерполяцию;
- самостоятельно выбранные углы;
- любое inferred schedule.

Fail closed.

# Phase 1 — audit previous research and demo implementation

Разберись, что было сделано ранее и почему пользователь видит demo.

Изучи минимум:

- `ONE_MUSLIM_APK_RESEARCH.md`;
- `ULYANOVSK_ONE_MUSLIM_COMPARISON.md`;
- `RUSSIA_PRAYER_TIME_AUTHORITY_RESEARCH.md`;
- `RUSSIA_CITY_SOURCE_ARCHITECTURE.md`;
- `research/russia-prayer-source-registry-draft.json`;
- T034–T041;
- debug `DevelopmentDeviceSetupGateway`;
- synthetic schedule projection;
- current PostgreSQL registry;
- Android city/schedule setup;
- Ulyanovsk persisted E2E.

Зафиксируй:

- какие старые conclusions всё ещё подтверждаются;
- какие устарели;
- какие были только `INFERENCE`/`PROPOSAL`;
- какие субъекты не исследовались;
- где реальный source onboarding остановился из-за старой approval policy;
- где demo data сейчас подменяет обычный development/setup experience.

Старый research не считать доказательством автоматически.

# Phase 2 — nationwide first-party research

Исследуй все федеральные субъекты, реально присутствующие в текущем canonical
Russia catalog.

Не хардкодь их количество — получи набор из актуального catalog mapping.

Для масштаба используй parallel subagents по федеральным округам/группам субъектов,
если доступные collaboration tools делают это быстрее или надёжнее.

Для каждого субъекта установи:

- реальные региональные/централизованные Muslim authorities;
- их canonical organization names;
- какие из них реально публикуют prayer times;
- first-party ownership source;
- exact geographic scope;
- source type;
- effective year/range;
- locality coverage;
- timezone;
- madhhab/Asr policy, если опубликована;
- Fajr/Isha/high-latitude/seasonal policy, если опубликована;
- current transport;
- update cadence/version if discoverable.

Ищи прежде всего:

- official organization website;
- official public API;
- official city/district prayer selector;
- annual/monthly PDF/XLSX;
- official policy/ulema decision;
- authenticated official public social/channel source only when affiliation itself
  подтверждается first-party evidence.

Коммерческие агрегаторы и competitor apps разрешены только для discovery/cross-check.

# Role of 1Muslim and IslamApp

Предыдущее clean-room исследование 1Muslim использовать как архитектурное и
cross-check evidence.

Можно:

- находить города, где стоит искать exact timetable;
- сравнивать seasonal transitions;
- выявлять research gaps;
- строить гипотезы для дальнейшей first-party проверки.

Нельзя:

- копировать prayer rows;
- использовать competitor DB как runtime source;
- назначать authority по совпадению;
- использовать competitor city IDs как provenance.

Любое совпадение с competitor становится лишь дополнительным cross-check после
нахождения first-party source.

# Phase 3 — authority and scope resolution

Приоритет означает specificity и качество evidence, а не религиозное ранжирование.

Внутри одной подтверждённой authority chain предпочитай:

1. exact locality timetable;
2. locality/district first-party interface;
3. explicitly subject-wide first-party timetable/policy;
4. verified official calculation policy.

City-specific timetable никогда автоматически не распространяется на субъект.

Например:

`расписание города Саратов`
≠
`расписание всей Саратовской области`.

Если organization действительно публикует одну policy для всего субъекта,
используй её только в доказанном scope.

# Multiple authorities

Если один город имеет несколько реальных first-party authorities и отсутствует
прямое доказательство обязательного precedence:

не выбирай победителя.

Создай отдельные реальные choices:

City
 -> Authority A
 -> Authority B
 -> Authority C

Все должны быть независимо:

- qualified;
- scoped;
- sourced;
- reproducible.

Порядок не означает preference.

Не ограничивай их top-N.

# Phase 4 — implement real providers/policies

Для каждого qualified source создай production-capable representation.

## Exact timetable

Сохраняй минимум:

- authority;
- canonical source;
- source type;
- geographic scope;
- retrieval metadata;
- effective range;
- timezone;
- raw/content hash where obtainable;
- parser/normalizer version;
- validation result.

Parser должен быть deterministic и fail closed при schema/layout drift.

Не превращай partial/Ramadan timetable в annual data.

## Calculation policy

Разрешена только если first-party authority сама публикует достаточную policy.

Зафиксируй все параметры и проверь результаты против published first-party values
на нескольких сезонах.

Если policy неполна или нельзя доказать конкретные значения:
`unavailable`.

# Phase 5 — replace demo normal path

Текущий synthetic debug flow больше не должен быть обычным способом city setup.

После T049:

- обычный city search показывает реальные authority options;
- реальные schedule previews используют real source data;
- `Демо-организация ...` не появляется в normal interactive setup;
- debug build сам по себе не означает `НЕ ОДОБРЕНО`;
- реальные schedules используют ту же source/provenance model, что production
  control plane.

Synthetic data оставить только для:

- automated tests;
- explicit evidence/test scenario, который невозможно спутать с normal setup.

Не удаляй deterministic synthetic test coverage без необходимости.

# Phase 6 — qualification and activation semantics

Human approval больше не должен быть обязательным этапом для
research-qualified public first-party source.

Эволюционируй существующий registry/publication contract.

Нужна понятная machine-verifiable lifecycle, концептуально:

researched
 -> first_party_verified
 -> scope_verified
 -> source_validated
 -> qualified
 -> active/selectable

Конкретные state names выбери после анализа существующей модели.

Не создавай fake:

- mosque approval;
- external organization approval;
- `approved_by` человека, которого не было.

Signing означает:

`NamazTime подтверждает целостность собственного materialized artifact`

а не:

`ДУМ официально одобрил NamazTime`.

Сохрани signing, hashes и immutable snapshots.

Эта задача явно авторизует локальную реализацию и локальную активацию/
materialization research-qualified расписаний, необходимую для доказательства E2E.

Это НЕ авторизует:

- push/PR;
- production deployment;
- изменение signing keys;
- удаление production data;
- утверждение о partnership с внешней организацией.

Использовать существующий signer abstraction разрешено, если это необходимо для
локального E2E и не требует изменения/раскрытия ключей.

# T038

T038 сейчас DEFERRED исключительно из-за старого требования written
scope/reuse/transport confirmation.

T049 supersedes этот blocker.

Как только в рамках T049 реально реализован хотя бы один второй non-Ulyanovsk
first-party regional source adapter с доказанным scope:

- обнови T038 на DONE;
- сослаться на T049 evidence.

Не оставляй T038 DEFERRED только из-за отсутствия external written approval.

# Iqamah / Jumu'ah

Не выводи regional adhan timetable как mosque Iqamah.

Если source публикует только prayer onset:
- использовать real onset/adhan values;
- Iqamah остаётся существующей mosque/device-local настройкой.

Jumu'ah использовать только при доказанном применимом first-party source/scope.

# Nationwide research artifact

Создай superseding nationwide report и machine-readable registry.

Для каждого subject:

- code/name;
- research status;
- authorities;
- first-party evidence;
- exact scopes;
- source types;
- implemented provider/policy IDs;
- covered localities;
- unavailable localities;
- evidence dates;
- unresolved facts;
- documented search trail when source не найден.

Старый 31-subject draft пометь historical/superseded, не удаляя полезное evidence.

# Falsification / validation of research

Не принимай первый найденный Muslim website автоматически.

Для production-qualified mapping проверь:

- first-party organization ownership;
- current organization identity;
- exact scope language;
- source currentness;
- timezone;
- several actual prayer dates;
- relevant seasonal transitions.

Для calculation policy проверь как минимум несколько периодов года.

При conflicting real first-party sources не усредняй данные.

# Representative real E2E

Обязательно докажи несколько разных source patterns, после их повторного research:

- регион с republic-wide official policy;
- регион с multiple real authorities;
- city/district official selector;
- city-specific timetable;
- annual/monthly official timetable;
- Ulyanovsk existing pilot regression.

Не считай конкретные старые mappings доказанными до повторной проверки.

# UI result

Для реального города normal setup должен выглядеть концептуально:

Уфа
  ДУМ Республики Башкортостан
  ЦДУМ России

только если обе options действительно подтверждены first-party research.

Каждая option показывает:

- real authority;
- source/schedule type;
- geographic scope;
- effective range;
- provenance summary.

Если для города ни одной qualified authority нет:

`Для этого города подтверждённое расписание пока недоступно`

и status `unavailable`.

Не показывать demo replacement.

# Documentation / persistent instruction changes

Обнови где реально требуется:

- `AGENTS.md`;
- `.agents/skills/prayer-times-provider/SKILL.md`;
- `SOURCE_PARTNERSHIP_CHECKLIST.md` или раздели partnership и source-qualification
  workflows, если это чище;
- `CODEX_TASKS.md`;
- `PLANS.md`;
- nationwide research docs/registry;
- `RUSSIA_CITY_SOURCE_ARCHITECTURE.md`;
- `PERSISTED_POLICY_REGISTRY.md`;
- `REGISTRY_OPERATOR_WORKFLOW.md`;
- relevant ADR;
- OpenAPI/contracts;
- Android setup docs.

Не меняй остальные streamlined skills без причины.

После изменений не должно существовать противоречие, где:

code/ADR говорит `research-qualified source can activate`

а skill/AGENTS говорит `human approval always required`.

# Constraints

Не:

- копировать 1Muslim/IslamApp datasets;
- использовать aggregator как authority;
- выдумывать authority names;
- создавать fake schedule;
- использовать generic fallback;
- расширять city timetable на subject без evidence;
- выбирать одну parallel Muslim organization религиозно «главнее» без evidence;
- обходить technical access restrictions;
- ослаблять provenance/signing/last-known-good.

# Done when

T049 завершена, когда:

1. переаудирован прежний research;
2. исследованы все subjects canonical catalog;
3. создан superseding machine-readable real source registry;
4. qualified sources имеют реальные provider/policy implementations;
5. source qualification не требует фиктивного human approval;
6. persistent AGENTS/skill instructions соответствуют новой policy;
7. обычный setup не использует demo organizations;
8. реальные cities показывают реальные authorities и real prayer times;
9. multiple real authorities отображаются независимо;
10. city без доказанного official Muslim source остаётся `unavailable`;
11. реализован минимум один второй non-Ulyanovsk regional source и T038 закрыт;
12. representative real E2E cases проходят;
13. Ulyanovsk existing real pipeline не сломан;
14. signing/provenance/last-known-good сохранены;
15. repository gates проходят.

# Validation

Используй task-appropriate narrow checks во время работы.

После изменения repository instructions/skills обязательно:

make docs-check
make test-skills

Для implementation минимум:

make test
make lint
make test-postgres
go test -race ./...
make test-android-all
make security-go
make secret-scan

Если DB schema меняется:
- migration up/down;
- rollback/reapply;
- backup/restore.

Для provider adapters:
- parser/normalizer tests;
- schema-drift fail-close;
- real-source sample comparisons;
- stale/unavailable behavior.

Не повторяй уже успешные широкие gates без новых изменений или причины.

# Execution

Это крупная research + implementation задача.

Сначала:
1. inspect current repository and new skills;
2. resolve persistent policy conflict;
3. audit previous research;
4. parallelize nationwide research where useful;
5. synthesize consistent registry;
6. implement qualified providers/policies;
7. integrate normal setup/runtime;
8. run representative E2E and final gates.

Не останавливайся после plan или research report.

Не спрашивай пользователя, какую organization выбрать, если evidence позволяет
продолжить.

Если evidence недостаточно — конкретный city/source становится `unavailable`,
а работа продолжается по остальным регионам.

Если какой-либо skill всё ещё заставляет остановиться ради approval/permission,
которое эта задача явно отменила как product gate, укажи точный SKILL.md/правило,
устрани конфликт в repository instructions и продолжи.

Делай coherent checkpoint commits.

Не push и не создавай PR без отдельной команды владельца.

# Final report

В конце сообщи:

- baseline;
- какие persistent instruction conflicts исправлены;
- сколько subjects исследовано;
- distribution qualification states;
- сколько real authorities найдено;
- сколько city/locality scopes получили реальные schedules;
- сколько осталось unavailable;
- почему unavailable;
- какие real provider types реализованы;
- regions with multiple authorities;
- что изменилось относительно старого 31-subject draft;
- что стало с demo gateway;
- T038 result;
- representative E2E;
- migrations/contracts;
- `make test-skills` result;
- repository gates;
- remaining genuine UNKNOWN/DEFERRED;
- commits.
