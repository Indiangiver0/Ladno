# APP-005-FIN-REVIEW v2 — отдельная проверка финансового отдела

Дата: 2026-11-02. Проверяющий: finance-lead /root/android_finance_lead; авторы — отдельные агенты.
Задача: проверить денежный контракт и независимые синтетические эталоны для native Android APP-005.
Статус: денежная методология и независимые эталоны согласованы главой для реализации/QA; FIN-HEAD-04 закрыто отдельным readback metadata.
Это head review документов/эталонов, не приёмка приложения; независимые QA и суд остаются отдельными этапами.

## Проверяемая версия и авторство

- calculation-analyst /root/android_money_contract: `docs/android/MONEY.md`, APP-005-MONEY v2, 66 строк; SHA-256 `ADD32DC37931AF0079236866B5248019AA59A3AE9C120B2E8030ED9A75C1CDCB`.
- financial-reference-specialist /root/android_finance_fixtures: `android-app/fixtures/money-cases.json`, APP-005-MONEY-CASES-v1, 40 групп MONEY-001..040, metadata MONEY v2/CONTRACT v2; SHA-256 `AED74DE9B8F0F3032EDF0509B5078E97143E707B5163EAD30BF15A11F54DF726`.
- Совместно прочитан `docs/android/CONTRACT.md` v2; SHA-256 `BD2C72C33E158CA78E37E0415EC633908610A4176C2D56B1283E1654BA27DEE0`. SECURITY прочитан для границ raw.
- Глава не менял файлы авторов, Java-код или прежние 46 файлов; единственное изменение главы — этот отчёт. Сохранность всего old46 этим отчётом не проверялась.

## Выполненное главой

- Лично прочитаны MONEY v2 и все 40 групп input/expected/derivation; сопоставлены wire kind/date, settings, источники, даты и повторная обработка с CONTRACT v2.
- Положительная canonical decimal string, ASCII-синтаксис, точный scale, лексический запрет лишних дробных цифр включая нули, UNNECESSARY, BigInteger minor и TEXT/JSON-string согласованы.
- Основная валюта выбирается явно; допустимый runtime Currency scale 0..6, 24 целые цифры отдельного ввода — инженерные ограничения. Агрегат длиннее 24 цифр допустим, разные валюты не складываются.
- Согласованы строгие даты 0001..9999, календарный месяц, приоритет source date, явный POST_TIME_IN_ZONE и сохранённый ZoneId; capturedAt не заменяет дату покупки. Повреждённая дата не исправляется fallback.
- Null-категория входит в expense; назначение/удаление категории сохраняет ID, деньги и полный итог. needs_review/draft не включаются в ledger; net не выдаётся за баланс.
- Сильная проверенная payment identity связывает доказательства с одной записью без суммы/перезаписи; слабые совпадения суммы/времени/fiscal ID требуют review и явного link/confirmDistinct.
- Generic неоднозначные разделители, неизвестная валюта/направление, refund/transfer/pending, баланс вместо суммы, сдача/наличные вместо paid total не становятся угаданной операцией.

## Доказательства: ожидание и факт

- `Get-Content` MONEY/CONTRACT/fixtures, затем раздельное чтение JSON-групп через `ConvertFrom-Json`/`ConvertTo-Json -Depth 30 -Compress`: все 40 групп прочитаны; первоначальный общий вывод был усечён и дополнен отдельными чтениями.
- `Get-FileHash -Algorithm SHA256 -LiteralPath <три пути выше> | Format-List`: ожидались SHA из передачи авторов; фактически все три совпали.
- После авторской правки FIN-HEAD-04 отдельный `python -` readback json/hashlib подтвердил v2/v2, 40 ID MONEY-001..040 и новый SHA выше, exit 0. Обратная замена только двух metadata-меток в памяти дала прежний SHA `05B759E3E6C08A64B8498AAE8BF12ADB2FFD995592DF744C24F337EF2521292B`: остальные байты, включая caseGroups, неизменны. Арифметика повторно не запускалась.
- PowerShell here-string → `python -`, только стандартные json/re/datetime/decimal/pathlib; вход — указанный JSON, без импорта приложения. Ожидание: согласованность эталонов; факт: `finance-head review: 437 assertions passed`, exit 0.
- Шаги Python: разобрать JSON, проверить 40 уникальных последовательных ID и обязательные input/expected/derivation; рекурсивно проверить целочисленные numeric metadata, ручные lexical/scale/minor варианты; пересчитать выборку в int и Decimal precision=100.
- MONEY-015: 900719925474099301+2−900719925474099299=4 minor → net `0.04`; MONEY-016: 2×99999999999999999999999999=199999999999999999999999998 minor → `1999999999999999999999999.98`; факт совпал.
- MONEY-008/009/013: excess precision, включая `1.200` scale2/`1.0` scale0, и 25 целых цифр отвергаются; допустимые ручные формы дополняются до scale. Факт независимой лексической выборки совпал, округления не было.
- MONEY-019/020/022/023: отдельно пересчитаны месячные суммы, високосные даты и epoch. 1793478600000 = 2026-10-31T20:30:00Z → UTC 2026-10-31, UTC+09/Tokyo 2026-11-01; 1793493000000 = 2026-11-01T00:30:00Z. Факт совпал.
- MONEY-027/028/029: 120+230=350 minor во всех категорийных разрезах; перенос даты даёт октябрь 0, ноябрь 350; отклонённое редактирование сохраняет последнюю дату/сумму. Факт совпал.
- MONEY-030/035..040: проверены раздельные валютные итоги, нулевой вклад link, 1000+1000=2000 при confirmDistinct, нулевое ожидание после удаления/replay и строковый JSON roundtrip большой суммы. Это проверка ожиданий, не запуск дедупликации/SQLite.
- Автор fixtures сообщил отдельный прогон 445 assertions, exit 0; это свидетельство автора, не мой прогон и не Java-тесты. Мои 437 assertions относятся к независимому oracle/review.
- Первичные [Java Currency](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Currency.html#getDefaultFractionDigits()), [BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html#setScale(int,java.math.RoundingMode)), [LocalDate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/LocalDate.html) открыты главой через web 2026-11-02; runtime-каталог устройства отдельно не проверялся. Законы, ставки, налоги и курсы для этой модели не нужны.

## Замечания и оставшиеся вопросы

- FIN-REF-01 / P2 — закрыто чтением MONEY v2 и MONEY-035..038: strong identity → linked, weak match → review до явного решения; прежнее противоречие не сохраняется.
- FIN-HEAD-02 / P2 — закрыто: MONEY v2 использует kind/date, eventId/sourceKeyToken/revisionToken; exact revision не перезаписывает правки, новый revision — отдельное наблюдение.
- FIN-HEAD-03 / P2 — закрыто: MONEY v2 явно ограничивает raw временной обработкой, persistence/JS — structured safe fields; временные media очищаются по контракту.
- FIN-HEAD-04 / P2 — закрыто: владелец fixtures исправил contractVersions на `[APP-005-MONEY v2, APP-005 CONTRACT v2]`; отдельный readback главы подтвердил metadata/SHA и неизменность остальных байтов. Финансовое расхождение не обнаружено.
- После ошибки `agent thread limit reached` начальник организовал очередь автора; правку выполнил original financial-reference-specialist, глава проверил отдельно. Открытых замечаний к этим двум финансовым материалам нет.
- Java/core/parser/SQLite/bridge/UI/OCR/APK/device/bank tests этой проверкой НЕ выполнялись; тестовые суммы/валютные metadata синтетические. Наличие сценариев не доказывает, что native-код их выполняет.

Следующее действие: MONEY v2 и проверенная версия эталонов передаются backend/frontend и независимому QA. Проверка соответствия native-кода, приёмка установленного/собранного APK и суд — отдельно по зафиксированной версии; этот отчёт их не заменяет.
