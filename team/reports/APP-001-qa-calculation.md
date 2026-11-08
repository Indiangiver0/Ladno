# APP-001 — расчётный QA частичного замороженного пакета

Дата финального расчётного этапа: 30.10.2026, Asia/Qyzylorda. Исполнитель: `/root/personal_finance_qa_calculation`, calculation-test-engineer; принимающий результат: `/root/personal_finance_qa`, qa-lead. Владение сохранено только за этим отчётом и `APP-001-qa-calculation.py`; источники не изменялись.

**Текущий результат:** проверенная расчётная и локальная статическая часть прошла, общий `approve` не выносится. Все 26 хэшей пакета совпали; контрольный manifest hash совпал; D1 согласован; локальный граф FigJam точно соответствует архитектурному Mermaid. Подтверждены неполнота состояний/формы и пределы визуальных доказательств. Свежая Figma-проверка, визуальный осмотр этим специалистом, кликабельность и приложение **не выполнялись**.

## Зафиксированная версия и воспроизведение

Пакет: `team/snapshots/app-concept-v1-partial.json`, version=`app-concept-v1-partial`, 26 файлов. SHA-256 самого JSON: `72f31b0970e6308463d0e28e192075e371255d02f8759744d3af0eca3d229ac2`. Контрольный digest списка: `7548a12d9d0b60064bbd113e38248e785c5f88ffac004fde31f2f2251384f892`.

Алгоритм: прочитать реальные байты всех 26 файлов, сравнить SHA-256 каждого с snapshot; сформировать UTF-8-строки `sha256 + два пробела + POSIX path + LF`, сортируя пути **ordinal**, затем SHA-256 объединения. В Python используется `sorted(paths)`, а пути пакета ASCII: культурная сортировка не применяется. Порядок записей самого JSON не является частью алгоритма. Все байты файлов и snapshot повторно сравнены в конце запуска и не изменились.

Текущий SHA-256 скрипта: `eea0e699b51081b222e8e1fd1aca9544c4015848729e751e4cc7382ee61aa6f4`. Он включает предварительный независимый эталон и новые проверки freeze/design/graph, не копирует и не запускает авторский `design/figma-check.js`. Авторский проверочный скрипт пишет frozen `figma-local-checks.json`, поэтому запускать его на этом этапе было бы неправильно. Figma-инструменты не вызывались: действует запрет повторов/обхода Starter quota.

Команда из корня, Python 3.13.3, стандартная библиотека, без зависимостей и записи источников:

```powershell
python 'team/reports/APP-001-qa-calculation.py'
```

Ожидание: exit 0; 26/26 actual hashes и контрольный digest совпали; самостоятельный расчёт D1 и контрактов корректен; локальные доказательства не объявлены live/visual проверкой. Факт финального запуска: **exit 0**, полный stdout:

```text
APP-001 frozen partial calculation QA; concept/D1/local evidence only; no application or live Figma tests
PASS SHA256 8c6bb181ed342867e453640c1f3f61384dc2c4425a1789d813d01236a79e3c81  design/demo-data.json
PASS SHA256 6109cb5264579f05792c8df92b0fdaca37486ab6b03891d395d88f78cc5072ca  design/figjam-readback.xml
PASS SHA256 142a810abb7a5b3387a4e64d06fea1aacbc3dcc829ca0f6f7bda9be3e55c2082  design/figjam-structure.json
PASS SHA256 4ee321487a05bcec90a6d47aeeef539df313d4e0574a2b92d3cad67b12e78fcf  design/figma-01-foundations.js
PASS SHA256 cd7f2a02abe7e07b45355e53d3f3af49923475a02b744f6d1a7648931b09be1f  design/figma-02-overview.js
PASS SHA256 1033616a3bafd5998d2634fb6385e421eca6f523fa66d621fd10ed036bfd1a2d  design/figma-02a-layout-fix.js
PASS SHA256 96690a16c3faec0ca0048dbee5dd462685d8cccb1f00d7d90fe7446617aa6164  design/figma-03-screens.js
PASS SHA256 553325880602568bbcebf4d689161639e086f58338a933ffa2f9ad7f625df581  design/figma-04-mobile.js
PASS SHA256 4b98d1937659c4db8dc938dddc4c574231ec5d5272e7dc406313000b8c8823cf  design/figma-05-states-form.js
PASS SHA256 d36da930c51620805b4ffaafb093b1990a160386bde935bed77dca62b47ba760  design/figma-check.js
PASS SHA256 3dea4fae7df711286c86cdc6de93c05441d4439c91c750861d16736166c3593f  design/figma-core.js
PASS SHA256 575ac5ea9227d4191eea7bd4c11261999995b51238b23a57d747407f823ffa96  design/figma-evidence.json
PASS SHA256 2daacb826f13727123d4688b2128bc95923885f883157d723672cbad538be747  design/figma-local-checks.json
PASS SHA256 724ed1b5da3d8076d0670a4020b1e1bc23104545c48a7f022eddbd3cfb9385d4  design/figma-manifest.json
PASS SHA256 7cdc03d62fe3f64f582ea4777cbe8c5d631ea5290c1fc101179e95f42c415d6f  design/figma-report.json
PASS SHA256 8dd89daa9d81a12c3d5775b518efd0da9488c11ce58e9e32da0627294e79f9ac  design/figma-state.json
PASS SHA256 244e9159474a35e6687e92ac9669ca83564cdacde07b33ab0034dd588d9e8bd4  design/previews/SC-01-desktop.png
PASS SHA256 7657ff724191b1b649458cacdeceb75039d8325c581de919e6c1af3e654db60f  docs/architecture/SECURITY.md
PASS SHA256 9bee45e95c6a3b7307521768934293cc2b23d2a80c091d5d0d330b65b6edca60  docs/architecture/STRUCTURE.md
PASS SHA256 69de89fe158318b88acf8f7649471c60e7ee94236042fe8cd65586d3c1bd2a3d  docs/design/HEAD_REVIEW.md
PASS SHA256 5ac6abc48b90a8565746e1b93704c805dd2e8ff60fa521e3278742db73ad030f  docs/design/UX.md
PASS SHA256 36449a35edce7313910f24e73213097bfa1305b5fc84e8664e4a16b1583dd64d  docs/finance/HEAD_REVIEW.md
PASS SHA256 f020cb4dd08d8801a03ee5c7203edc76740f91cea77ce49b3f1719a42ca98674  docs/finance/MODEL.md
PASS SHA256 fdb5a41fb0d1a8e511008f845c8489a5909700a58a7196fd0b7af94fbdffba1b  docs/product/CONCEPT.md
PASS SHA256 f2217b2d387a9ff2ec5c22e50f46d6ed3720098e7b02cb21438d21e80df99503  docs/product/SCREENS.md
PASS SHA256 a6d3ae0637ea3d0eace40e86ad196a9c0d9e367d3ded170943f005b3f10ea5a9  docs/README.md
PASS frozen files=26/26; canonical_manifest_sha256=7548a12d9d0b60064bbd113e38248e785c5f88ffac004fde31f2f2251384f892
SNAPSHOT_JSON_SHA256 72f31b0970e6308463d0e28e192075e371255d02f8759744d3af0eca3d229ac2
PASS checker-only: zero/null distinction, extra decimals, large exact value, canonical format, calendar edges
PASS canonical MoneyDTO: 27; real LocalDate: 13; month=2026-10, interval=[2026-10-01,2026-11-01), snapshot=2026-10-30
PASS operations=4; income=500000.00; expense=300000.00 KZT (minor: 50000000,30000000)
PASS budget planned=350000.00; spent=300000.00; remaining=50000.00; categories=0.00,30000.00,20000.00 KZT
PASS opening=null -> balance unknown; income-expense=200000.00 KZT is a flow difference, not a balance
PASS goal=180000.00/600000.00 KZT -> exactly 30%; no October contributions or ledger effect
PASS debt owe=80000.00; owedToMe=50000.00 KZT separately; no invented principal, repayments or ledger effect
PASS references/revisions/unique IDs: 13 entities; fixture.expected agrees with independent oracle
PASS static build-return/manifest agreement: 5 frames, 238 TEXT, 36 INSTANCE, 0 reported image-fill nodes; not a fresh Figma read
PASS static source D1 labels/amounts: SC01 desktop, SC02 four rows, SC03 30%, SC04 separate debts, SC01 mobile; no live text/visual proof
CONFIRMED PARTIAL: AN01/02 skeleton; stage05 not applied; mobile nav not componentized; final PNG only SC01 (header, not visual inspection)
PASS local FigJam XML: 10 nodes, 9 directed edges, exact architecture Mermaid agreement; not deployed services or fresh Figma read
PASS all 26 frozen input files and snapshot bytes unchanged during run
RESULT PASS_CHECKED_PARTIAL_SCOPE; LIVE_FIGMA_VISUALS_PROTOTYPE_APP_NOT_PERFORMED; NO_GENERAL_APPROVE
```

При подготовке расширения та же команда дважды завершилась exit 1 по ошибкам самого QA-инструмента: сначала `AssertionError: snapshot paths must be sorted` (лишнее требование к порядку JSON), затем `AssertionError: D1 display amount` (лексический поиск захватывал числовые параметры JS вне строк). Исправлены только принадлежащие исполнителю строки скрипта: сортируется канонический список, денежные literals читаются внутри строк. Frozen источники не исправлялись. Третий, финальный запуск выше прошёл. Это история инструмента QA, не подтверждённые дефекты продукта.

## Что именно дополнительно проверено

- D1 повторно проверена тем же независимым эталоном из SCREENS и задания: целые минимальные единицы и Decimal; 4 операции, 500000/300000/350000/50000, остатки категорий 0/30000/20000, цель 30%, отдельные долги 80000/50000; `expected` сравнен после собственного расчёта. Пять исходных финансовых хэшей совпадают с preliminary. Подробная арифметика сохранена в исторической части ниже.
- Manifest и сохранённые успешные build-return сопоставлены по реальным ID/названиям, размерам, descendants, types, imageFillNodes и fonts: `6:222`–`6:226`, четыре 1440×1120 и mobile 390×2020; 238 TEXT / 36 INSTANCE / 0 reported image-fill. Проверены frame URLs и D1 metadata; `figma-state.json` совпадает с сохранённым foundations-return. Авторские сохранённые hashes в figma-local-checks независимо сравнены с реальными файлами. Это внутренняя согласованность локальных доказательств, а не fresh read Figma.
- Основные композиции: независимый Python-скрипт читает JS **как данные**, не исполняет Plugin API. По каждому из пяти roots сопоставляет денежные строки с отдельным эталоном. На обзоре desktop/mobile отдельно читает пары план/потрачено, факт/план трёх категорий, названия направлений и их суммы; проверяет неизвестный opening, смысл остатка бюджета, 30%, дату состояния. SC-02: точная литеральная таблица четырёх операций, включая даты, типы, категории, суммы и признак дохода. SC-03: 180000/600000, 30%, дата и отсутствие октябрьского пополнения. SC-04: 80000/50000, направления, сроки, отсутствие известных исходных сумм/истории. В основных композициях нет `200 000,00` и несохранённого `1200,999`.
- Период/валюта: shared shell содержит «Октябрь 2026 · Демо · KZT», SC-03/04 имеют «На 30.10.2026»; overview/operations/mobile содержат 01.10–31.10.2026. Геометрические коэффициенты полос `6/7`, `1`, `0.8`, `0.6`, `0.3` не используются для денежной арифметики или округления денег. Проверки исходных literals не доказывают фактические переносы текста, отсутствие clipping или current characters каждого слоя.
- Граф: независимо разобраны Mermaid из STRUCTURE и XML FigJam; проверены все 10 имён узлов, все 9 направленных рёбер, существование их концов и направление стрелок. Результат: множества узлов/рёбер совпали точно, без лишних рёбер. Проверен `figjam-structure.json`: fileKey/URL/source/kind указывают на внутренние модули предлагаемого приложения, не на развёрнутые сервисы. Это локальное readback-свидетельство, не новый доступ к FigJam.
- Наличие PNG проверено по frozen manifest; единственный итоговый SC01 файл имеет PNG signature и 1440×1120 в заголовке. Самостоятельный визуальный осмотр этим расчётным специалистом не выполнялся; соответствующий сценарный QA — отдельная роль.

## Замечания с ID, приоритетом и границами

- **QA-CAL-01, P2, подтверждённая неполнота обязательного результата, связана с VIS-B01:** AN-01/02 — skeleton/seed, `placeholder=true`, `compositionApplied=false`; stage05 только подготовлен. Доказательства: `figma-manifest.json:178`, `:179`, `:189`, `:190`, `:347`; saved quota errors `figma-evidence.json:1046` и `:1058`. Ожидание AC-SC-05 — показанные состояния SC-01–04 и образец ввода; факт — их реализация в Figma не применена. Это блокирует полный обязательный объём, но не меняет правильную арифметику D1. Frozen источники не исправлять в рамках этого QA.
- **QA-CAL-02, P2, недостаточность обязательных визуальных доказательств:** для SC02/03/04/mobile `previewPath=null`, `visualInspection=not-performed`; свежий независимый Figma read отсутствует. Сохранённые composition-return counts и source literals не закрывают видимость сумм/действий, отсутствие обрезок и полноту AC-SC-03/06/07. Доказательства: `figma-manifest.json:87`, `:114`, `:141`, `:168`, тексты отказов в evidence. Здесь подтверждено отсутствие проверки, а не наличие конкретной визуальной ошибки.
- **QA-CAL-03, P3, отложенное качество повторного использования:** mobile navigation пока frames/text, не reusable component instances, а её замена находится в неприменённом stage05. Доказательство `figma-manifest.json:201` и чтение figma-04/05. Само по себе это не неверный денежный результат и не отдельное нарушение числового критерия.
- Подтверждённых ошибок финансового результата P0/P1 в проверенном scope нет. Непроверенные критерии от этого не становятся пройденными. Статус `metricsWereReadBack=true` прочитан вместе с `metricsEvidence=use_figma composition return`: это данные авторского return, не самостоятельный поздний read Figma со стороны QA.

## Что не выполнялось и передача главе

Not-performed: новая Figma/FigJam API-проверка; полный readback текстов всех текущих слоёв; визуальная инспекция этим специалистом; screenshots отсутствующих кадров; применение stage05; replay; кликабельность; приложение, хранение и runtime-проверки. Внешних ставок, налогов и законодательных правил задача не требует. Q-02–04 остаются открытыми решениями до реализации.

Передать этот отчёт qa-lead для независимого повторного запуска команды и сверки критериев. Расчётный специалист не принимает свою работу и не выносит общий approve. Локальная финальная сверка freeze/manifest выполнена; оставшееся недоказанное явно сохраняется в QA-CAL-01/02. Восстановление доступности внешнего инструмента и изменение дизайна — последующая задача владельца после отдельного решения chief, а не изменение замороженного пакета этим исполнителем.

---

## История предварительной проверки до freeze

Ниже сохранён предыдущий отчёт в его историческом контексте: его статус, первоначальный SHA скрипта и рекомендуемое будущее действие относятся только к моменту preliminary. Текущий результат и команда находятся выше.

Дата: 30.10.2026, Asia/Qyzylorda. Исполнитель: `/root/personal_finance_qa_calculation`, calculation-test-engineer, независимо от авторов проверяемых материалов. Направление: qa-lead, запуск начальником от его имени по `APP-001-qa-plan.md`.

Статус: **предварительная проверка стабильных документов и D1 прошла**. Это не итоговый QA, не приёмка работы и не тестирование приложения. Главе QA результат ещё предстоит проверить. Figma, manifest/readback и PNG на этом этапе не читались.

## Задача и версия

Проверены CONCEPT v0.1.1, SCREENS APP-001-PROD-AC v1, MODEL APP-001-FIN-MODEL v1, STRUCTURE APP-001-TECH-SYS v1 и D1 fixtureVersion 1.0.0. Точная версия ограничена следующими SHA-256 байтов входов, прочитанных скриптом:

```text
fdb5a41fb0d1a8e511008f845c8489a5909700a58a7196fd0b7af94fbdffba1b docs/product/CONCEPT.md
f2217b2d387a9ff2ec5c22e50f46d6ed3720098e7b02cb21438d21e80df99503 docs/product/SCREENS.md
f020cb4dd08d8801a03ee5c7203edc76740f91cea77ce49b3f1719a42ca98674 docs/finance/MODEL.md
8c6bb181ed342867e453640c1f3f61384dc2c4425a1789d813d01236a79e3c81 design/demo-data.json
9bee45e95c6a3b7307521768934293cc2b23d2a80c091d5d0d330b65b6edca60 docs/architecture/STRUCTURE.md
```

Созданы только назначенные исполнителю файлы `team/reports/APP-001-qa-calculation.py` и этот отчёт. Проверяемые источники не изменялись. SHA-256 скрипта: `9820cf87fefbe07b2c249c7b3fd403e70381f2aa1ab0adfd394ba76ad7d16227`.

## Выполненное и независимые ожидания

Прочитаны AGENTS.md, PROTOCOL.md, постоянный профиль calculation-test-engineer, QA-план и пять входов. Независимый эталон перенесён из условий D1 в `SCREENS.md:29`, `SCREENS.md:34`, `SCREENS.md:37`, `SCREENS.md:38`, `SCREENS.md:39` и задания начальника, а не из `fixture.expected`. Код приложения отсутствует; проверяемой реализации формул нет. Поле `expected` сравнивается только после самостоятельного подсчёта.

- Ровно четыре операции: 500000.00 доход и 150000.00 / 120000.00 / 30000.00 расходы. Целочисленный расчёт в минимальных единицах: доход 50000000; расход 15000000 + 12000000 + 3000000 = 30000000. Факт совпал с ожиданием 500000.00 / 300000.00 KZT.
- План: 15000000 + 15000000 + 5000000 = 35000000 минимальных единиц. Остаток: 35000000 − 30000000 = 5000000, то есть 50000.00 KZT. По категориям: 0 / 3000000 / 2000000 минимальных единиц, то есть 0.00 / 30000.00 / 20000.00 KZT; их сумма равна общему остатку. Общий и категорийный факт не складывались повторно.
- Цель: 18000000 / 60000000 × 100 = 30% точно; отдельно проверено целочисленное равенство 18000000 × 100 = 60000000 × 30. Decimal дал тот же результат. Округление не применялось. Накопление до октября, октябрьских пополнений нет, ledgerEffect=none.
- Долги: owe=8000000, owedToMe=5000000 минимальных единиц; 80000.00 и 50000.00 KZT показаны раздельно без взаимозачёта. Даты сроков — 15.11.2026 и 20.11.2026; состояние — на 30.10.2026. История не предоставлена, principal отсутствует; октябрьских возвратов и влияния на ledger нет.
- Счёт «Основной»: opening=null, balanceState=unknown. Разность потоков 500000.00 − 300000.00 = 200000.00 KZT арифметически верна, но не восстанавливает баланс. В JSON нет поля с выдуманным балансом/доступной суммой. Название и смысл сверены с `SCREENS.md:32`, `MODEL.md:35`, `MODEL.md:62`, `STRUCTURE.md:62`.
- Все 27 MoneyDTO, включая справочные ожидания: ровно ключи currency/scale/amount, KZT, целочисленный scale=2, каноническая десятичная строка с двумя знаками. Ведущие нули, экспонента, плюс, группировка и отрицательный ноль не допускаются. Суммы операций строго положительны. Арифметика денег — Python int, без float; Decimal используется для независимой дополнительной проверки.
- Все 13 LocalDate — реальные календарные даты; month=2026-10. Все четыре операции входят в `[2026-10-01, 2026-11-01)`, цели/долги относятся к 30.10.2026. Начало и конец периода проверены отдельно, дата не преобразуется через UTC.
- Проверены 13 уникальных ID сущностей и положительные целочисленные revision; ссылки операций на счёт/категории, соответствие kind типу операции, ссылки планов только на расходные категории без дубликатов, ссылки ожидаемых категорий/цели. Валюта и точность операций совпадают со счётом. Числа `fixture.expected` совпали с независимо полученными фактами.

Инструмент имеет собственные небольшие проверки: 0.00 и недопустимый amount=null; дробность 1.000; канонический формат; точная сумма 9007199254740993.01 → 900719925474099301 минимальных единиц; дата 29.02.2024 допустима, 29.02.2026 и 31.04.2026 недопустимы. Это проверки инструмента QA, а не парсера или финансовой библиотеки будущего приложения. Прикладной верхний лимит этим не утверждается.

Документная сверка вручную: `CONCEPT.md:45` и `CONCEPT.md:50` отделяют цели/долги от движений счёта; `SCREENS.md:40`, `MODEL.md:41`, `MODEL.md:61` и `STRUCTURE.md:85` согласованы с фикстурой. Runtime-сущность Debt из `STRUCTURE.md:82` содержит principal, а D1 намеренно предоставляет только остатки: metadata.purpose=design-fixture, isRuntimeBackup=false, historyState=not-provided. Поэтому отсутствие principal не маскирует готовую запись для runtime и не требует выдумывать историю. SCREENS и MODEL согласованы с MoneyDTO/LocalDate-контрактом `STRUCTURE.md:43`, `STRUCTURE.md:51`. Финансовые правила Q-02–04 остаются предложениями до реализации.

## Доказательства: команда, ожидание, факт

Среда: PowerShell, `python --version` → `Python 3.13.3`. Воспроизведение из корня репозитория без установки зависимостей:

```powershell
python 'team/reports/APP-001-qa-calculation.py'
```

Ожидание: код завершения 0, совпадение пяти входных хэшей с указанной версией, независимая арифметика D1 и проверки контрактов проходят; конец вывода явно оставляет финальную проверку невыполненной. Факт: команда выполнена, код завершения 0, полный stdout:

```text
APP-001 preliminary calculation QA; concept/D1 only; no application or Figma tests
SHA256 fdb5a41fb0d1a8e511008f845c8489a5909700a58a7196fd0b7af94fbdffba1b docs/product/CONCEPT.md
SHA256 f2217b2d387a9ff2ec5c22e50f46d6ed3720098e7b02cb21438d21e80df99503 docs/product/SCREENS.md
SHA256 f020cb4dd08d8801a03ee5c7203edc76740f91cea77ce49b3f1719a42ca98674 docs/finance/MODEL.md
SHA256 8c6bb181ed342867e453640c1f3f61384dc2c4425a1789d813d01236a79e3c81 design/demo-data.json
SHA256 9bee45e95c6a3b7307521768934293cc2b23d2a80c091d5d0d330b65b6edca60 docs/architecture/STRUCTURE.md
PASS checker-only: zero/null distinction, extra decimals, large exact value, canonical format, calendar edges
PASS canonical MoneyDTO: 27; real LocalDate: 13; month=2026-10, interval=[2026-10-01,2026-11-01), snapshot=2026-10-30
PASS operations=4; income=500000.00; expense=300000.00 KZT (minor: 50000000,30000000)
PASS budget planned=350000.00; spent=300000.00; remaining=50000.00; categories=0.00,30000.00,20000.00 KZT
PASS opening=null -> balance unknown; income-expense=200000.00 KZT is a flow difference, not a balance
PASS goal=180000.00/600000.00 KZT -> exactly 30%; no October contributions or ledger effect
PASS debt owe=80000.00; owedToMe=50000.00 KZT separately; no invented principal, repayments or ledger effect
PASS references/revisions/unique IDs: 13 entities; fixture.expected agrees with independent oracle
PASS input bytes unchanged during run
RESULT PASS_PRELIMINARY; FINAL_FIGMA_MANIFEST_AND_FROZEN_HASH_RECHECK_NOT_RUN
```

Дополнительные воспроизводимые шаги документной сверки: открыть приведённые строки пяти документов либо выполнить адресный поиск:

```powershell
rg -n 'MoneyDTO|LocalDate|opening=null|principal|Долги до|Счёт операций|Операции:|Категории бюджета|накоплено|ledger|остаток счёта|движение денег' docs/product/CONCEPT.md docs/product/SCREENS.md docs/finance/MODEL.md docs/architecture/STRUCTURE.md
Get-FileHash -LiteralPath 'team/reports/APP-001-qa-calculation.py' -Algorithm SHA256
```

Эти шаги чтения и вычисления хэша выполнены. Первоначальный адресный `rg` использовал более широкий набор финансовых терминов; все существенные строки и их смысл приведены выше. Чтение документа не проверяет будущую запись данных или исполняемую валидацию.

## Замечания и оставшиеся вопросы

Подтверждённых дефектов P0/P1/P2/P3 в проверенной предварительной части не выявлено. Это не свидетельство готовности всей задачи и не закрытие непроверенных критериев.

- QA-CAL-L01, ограничение этапа, без дефектного приоритета: **не выполнялись** сверка денежных текстов Figma/manifest/readback, финальные хэши после freeze, просмотр PNG, независимая проверка результата главой QA. Новая версия источников потребует повторной сверки затронутых утверждений.
- QA-CAL-L02, граница задачи, без дефектного приоритета: приложение, формы, хранение, округление произвольных будущих процентов, перевод/отмена транзакции, импорт и сценарии редактирования периода **не выполнялись и не тестировались**; реализации нет. Набор D1 не претендует на полную runtime-схему.
- Q-02–04 открыты: валюта/прикладные лимиты/зона, окончательные правила бюджета и связь целей/долгов со счетами. Для арифметической согласованности D1 они не требуют выдуманных ставок, налогов или законодательных правил; внешние источники не использовались.

## Рекомендуемое следующее действие

После freeze начальник передаёт followup с финальным manifest/readback и перечнем хэшей. Исполнитель повторяет проверку пяти входов, сверяет денежные тексты с D1 и фиксирует отличия/финальные доказательства в том же отчёте. Затем qa-lead независимо читает результат и повторяет команду. До этих шагов отчёт остаётся предварительным; решение о готовности и суд ещё не заменяются этой проверкой.
