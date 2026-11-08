# APP-003 — независимая расчётная runtime-проверка

Дата: 02.11.2026, Asia/Qyzylorda. Исполнитель: `/root/personal_finance_qa/calculation_runtime`, calculation-test-engineer. Принимающий результат: `/root/personal_finance_qa`, qa-lead.

Результат проверенного объёма: **21 группа / 486 утверждений прошли, exit 0**. Реально исполнены `LadnoModel` и `LadnoApp` через Node vm; это не только чтение исходников. Новых подтверждённых дефектов в этом объёме не найдено. Специалист не выносит общий approve; отдельные браузерные проверки, проверка главы и суд этим отчётом не заменяются.

## Задача, версия и владение

- Проверяемая версия: `local-prototype-v1`; snapshot `team/snapshots/local-prototype-v1.json`, 46 файлов.
- Канонический SHA-256 списка: `be7ef8f3e912744b04804a7a225fdee44ad6917254de68529f97a2af728e354e`. Все 46 фактических хэшей совпали; все 26 исторических файлов совпали с прежним snapshot и включены в новый с теми же хэшами.
- SHA-256 самого JSON snapshot: `053b0f9bffe25a4300b670ed57c390b0321e2dbf5738447e3efc70fea4a33887`.
- Канонизация списка: ordinal/code-unit сортировка POSIX paths; UTF-8, строки `sha256 + два пробела + path + LF`. JSON-порядок записей не используется для digest.
- Изменены только принадлежащие исполнителю `team/reports/APP-003-qa-calculation.js` и этот отчёт. Frozen-источники, сервер, UI и Figma не изменялись/не использовались.
- SHA-256 скрипта: `05a9215c7a331f4a8e3f5d4b81abe4d32933d1570fd6036b2098e2fcadfcbd7a`.

Прочитаны AGENTS.md, PROTOCOL.md, полный профиль calculation-test-engineer, APP-003 P1–P6, QA-план, MODEL, SCREENS D1, LOCAL-PROTOTYPE, frontend HEAD_REVIEW и фактические model/app. Отчёт frontend-head использован как контекст API/ограничений; его тест не извлекался/не исполнялся и не копировался вместо собственного oracle.

## Независимые ожидания и фактические результаты

Денежные ожидания явно заданы из SCREENS D1 и MODEL, а не получены вызовом проверяемых селекторов и не взяты только из `fixture.expected`. Из `design/demo-data.json` читается полная исходная копия для проверки идентичности; `fixture.expected` не используется как финансовый oracle в скрипте.

- **CAL-01/02:** actual hashes 46/46 и historical 26/26; свежие VM загружают `demo-data.js → model.js → app.js`. Начальная копия D1 deep-equal источнику: 4 операции; доход 500000.00, расход 300000.00, план 350000.00, остаток 50000.00 KZT. Остатки категорий 0.00/30000.00/20000.00; цель 180000.00/600000.00 = 30%; долги 80000.00 и 50000.00 отдельно. `opening=null`, `balanceState=unknown` сохранены.
- **CAL-03/04:** `toMinor` возвращает bigint; проверены exact round-trip, отрицательные вычисленные значения, `-0.00`/ведущие нули/плюс/экспоненты/неправильная дробность и типы. Raw `1`, `1,2`, `00012,01` нормализуются до 1.00/1.20/12.01; ноль допустим только при allowZero. `1200,999` не округляется. 9007199254740993.01 → 900719925474099301n, +1n → 9007199254740993.02; форматирование остаётся точным.
- **CAL-05/09/10:** календарные даты, включая 2000/2024 leap и неверные 1900/2026 leap/31 апреля; неправильные месяцы/нулевой год/временной суффикс отвергаются. Четыре реальные добавления по 0.01 на 30 сентября, 1/31 октября, 1 ноября дали октябрьский расход 300000.02/остаток 49999.98, сентябрьский и ноябрьский расход по 0.01 при неизвестном бюджете. Нет смешения месяцев или UTC-преобразования даты.
- **CAL-06/07:** controller submit `1200,999` оставил сумму и все остальные raw-поля, показал ошибку точности, сохранил ledger/счёт/итоги и 4 операции. Корректный расход 1200.99 создал ровно пятую операцию: расход 301200.99, остаток бюджета 48799.01, остаток продуктов 28799.01; доход не изменился. Второй submit закрытой формы отклонён, дубля нет.
- **CAL-08:** доход 0.01 меняет доход до 500000.01, не расходы/бюджет. В отдельном чистом наборе доход 9007199254740993.01 даёт точный итог 9007199255240993.01, остальные денежные показатели не меняются.
- **CAL-09/12:** пусто/ноль/отрицательное/избыточная дробность, несуществующие дата/счёт/категория/тип и несовместимая валюта/точность не становятся сохранёнными операциями. Денежная summary отклоняет несовместимую валюту/scale, а не складывает её с KZT.
- **CAL-11:** отсутствие бюджета даёт planned/remaining=null, а не ноль; явный total-план 0.00 даёт remaining=-300000.00/status=over. Расход без плана категории включён в общий факт; у непланированной категории planned/remaining=null. Дублируемый план категории отклонён. Это синтетические входы селектора, не новая форма редактирования бюджета.
- **CAL-13:** перевод без второго счёта и запрос с одинаковыми from/to заблокированы с объяснением «два разных счёта»; исходный единственный счёт/ledger/итоги неизменны. Успешный перевод/создание второго счёта не входит в этот прототип и не проверялся.
- **CAL-14/15:** создание цели с накоплением 0, изменение до превышения цели и ошибки суммы не меняют ledger/счёт/месячные итоги. Целевой progress 1.99/2.00 округлён до 100%, но reached=false до достижения точной суммы; 2.02/2.00 даёт 101%, полоса 100%, reached=true. Это отдельная проверка различия округлённого процента и денежного порога.
- **CAL-16/17:** новый долг одного направления не взаимозачитывается с другим и не меняет ledger. Ошибочные метка/направление/дата/сумма отклоняются. Возврат 80000.01 при остатке 80000.00, ноль, несуществующая дата и дата после фиксированного 30.10.2026 отклонены. Возврат 0.01 → 79999.99; затем 79999.99 → точный 0.00; лишний возврат отклонён. Другой долг и ledger/бюджет неизменны.
- **CAL-18/19:** реальные dispatch меняют section/filter/screenState, фильтр transfer даёт 0 строк и не меняет budget; reset-filter возвращает 4. Демосостояния empty/loading/error не удаляют данные; retry возвращает ready. Reset восстанавливает D1/октябрь/overview/all/owe/закрытую форму; отдельный новый VM-контекст тоже стартует с D1. Внешняя мутация копии `LadnoApp.state` не меняет внутренние данные; глобальный `LadnoDemoData` не мутирован.
- **CAL-20/21:** статическая проверка шести файлов обнаружила только локальные ресурсы styles/data/model/view/app и отсутствие указанных внешних сетевых/storage/Number-конверсионных API. Все 46 frozen-байтов и snapshot совпали до/после runtime-тестов. Статическая проверка ресурсов не названа испытанием браузера при отключённой сети.

## Воспроизведение: команда, ожидание и полный stdout

Среда: PowerShell, Node **v22.20.0**, стандартные `node:fs/path/vm/crypto/assert`; пакеты не устанавливались. Из корня workspace:

```powershell
node 'team/reports/APP-003-qa-calculation.js'
```

Ожидание: 46/46 hashes и ordinal digest совпадают, все независимые runtime-группы проходят, frozen-байты не меняются; код завершения 0. Первый и единственный запуск сохранённого скрипта выполнен: **exit 0**, факт:

```text
PASS CAL-01: 46 frozen hashes, ordinal digest and all 26 historical hashes
PASS CAL-02: fresh VM globals, deep-equal source D1, initial independent financial oracle
PASS CAL-03: canonical money and BigInt round-trip, including exact values above 2^53
PASS CAL-04: raw input normalization, precision rejection and zero/negative rules
PASS CAL-05: real calendar dates and valid month boundaries
PASS CAL-06: invalid controller submit preserves all raw values with no extra operation
PASS CAL-07: valid expense changes correct October totals exactly once
PASS CAL-08: income remains outside expense budget and huge value remains exact
PASS CAL-09: invalid amounts/dates/references reject without altering data
PASS CAL-10: month inclusion is date-only at Sep/Oct/Nov boundaries
PASS CAL-11: no budget differs from explicit zero and unplanned expense is included
PASS CAL-12: different currencies/scales cannot enter monetary summary
PASS CAL-13: single-account transfer, including same from/to, is blocked without invented account
PASS CAL-14: goal create/edit/zero/exceed target preserves ledger and independent account state
PASS CAL-15: goal HALF_UP percentage does not determine reached threshold
PASS CAL-16: new debts stay separate and reject invalid label/direction/date/amount
PASS CAL-17: repayment overpayment/date errors and exact full repayment have no ledger effect
PASS CAL-18: controller tabs, filters and demo states alter selection, not underlying money
PASS CAL-19: reset/fresh execution restore D1; caller cannot mutate controller through state getter
PASS CAL-20: static package references are local; network/storage/float APIs are absent (not an offline-browser test)
PASS CAL-21: all 46 inputs and snapshot unchanged after runtime tests
{
  "version": "local-prototype-v1",
  "manifestSha256": "be7ef8f3e912744b04804a7a225fdee44ad6917254de68529f97a2af728e354e",
  "snapshotSha256": "053b0f9bffe25a4300b670ed57c390b0321e2dbf5738447e3efc70fea4a33887",
  "groupsPassed": 21,
  "groupsFailed": 0,
  "assertions": 486,
  "execution": "Node vm: actual demo-data/model/app, no DOM or browser",
  "failures": []
}
```

Дополнительно выполнены `node --version` и `Get-FileHash -LiteralPath 'team/reports/APP-003-qa-calculation.js' -Algorithm SHA256`, exit 0. Версия/хэш приведены выше.

## Замечания, границы и оставшиеся вопросы

Подтверждённых дефектов P0/P1/P2/P3 в выполненном расчётном/runtime scope не выявлено. Проверенный ранее HEAD-FE-01 о canonical negative zero независимо покрыт CAL-03/04; `-0.00` отклонён. Это не подтверждение отсутствия дефектов во всём UI.

- **CAL-LIM-01, ограничение метода, не дефект:** VM намеренно не имеет document/window/DOM, view не загружается и UI-события не исполняются; реально тестируются public model/controller API и их чистые state-переходы. Взаимодействие делегированных DOM-событий, focus/inert/keyboard и HTML-отображение остаются браузерному специалисту.
- **CAL-LIM-02, not-performed этим специалистом:** real browser flows, desktop/390px, clipping/длинные суммы/доступность, screenshot, browser reload, отключение сети, прямой file://, запуск локального сервера, Figma. Fresh VM не называется реальным reload; статический no-deps не называется offline-disconnect.
- **CAL-LIM-03, границы продукта:** нет проверки production-хранения/банков/налогов/процентов/публикации; они не реализованы в этом объёме. Фиксированный срез возврата до 30.10.2026 — явно заявленное ограничение демо, не универсальное финансовое правило. Будущие Q-01–05 остаются открытыми.
- Финансовые проверки выполнены на KZT/2; другие валюты не агрегируются. D1 остаётся октябрём 2026 при текущей ноябрьской дате. Внешних ставок, курсов и законодательных утверждений не использовано.

## Передача главе

Для P3/P4 передаются положительные доказательства actual Node-runtime; P2 покрыт только на state/API уровне, P5 — на уровне неизменности/чистого reset/static local dependencies. P1, полный P2/P5 и P6 этим расчётным отчётом не закрываются.

Рекомендуемое действие: qa-lead отдельно прочитать скрипт/отчёт и повторить сохранённую команду, совместить с реальными browser-доказательствами сценарного специалиста, составить P1–P6 matrix с честными not-performed и передать тот же frozen-snapshot независимому суду. Автор этого QA не принимает собственную работу; общий approve не выносится.
