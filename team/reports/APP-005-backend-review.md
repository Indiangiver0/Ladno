# APP-005 — отдельная проверка главы backend/data

Версия отчёта: backend-review-v2, 2026-11-02. Проверяющий: backend-data-lead. Статус: адресные исправления проверены главой по коду; итоговая сборка и 24 JUnit methods прошли по фактическому результату начальника. Передано на отдельный повтор QA/security и суд. Это не независимый QA, судебное решение или проверка приложения на телефоне.

## Адресный повтор v2: задача, версия и решение

По поручению начальника выполнен ограниченный повтор чтения QA-CALC01, AND-SEC06/07, прежнего BD5-01 guard и исправления BD5-02. Полный аудит storage/native повторно не выполнялся. Временное владение ReceiptParser/ParserTest начальник передал оригинальному native-автору из-за недоступности data-автора; глава продуктовый код не правил.

Итог начальника для исправленной версии: `BUILD SUCCESSFUL` за 44s; 24 JUnit methods, 0 failures / 0 errors, в том числе ParserTest=10 и NotificationStatusTest=2; lint 0 errors / 3 warnings, прежнее EXIF-предупреждение исчезло. APK: 37404606 bytes, SHA-256 `38c9d4490419eda3685e7187281d5f1ecfb958a95d77c57f7f59633febcfbd5a`; sourceFingerprint `b5b8d938295f9489124bec82ad0c07c0aa86fcde8f0d1c5bd8cafc343db57b03`. Доказательства сборки сохраняет начальник в `android-app/dist/`: build-result, build-source-manifest, Gradle log и test XML. Новую сборку/тесты выполнял начальник; глава не приписывает их себе.

Решение главы: адресные изменения соответствуют указанным замечаниям по фактически прочитанным файлам; новых блокирующих дефектов в этих изменениях не найдено. Допускается передача указанной версии на независимый повтор QA/security и затем суд. Положительное независимое заключение и вердикт этим отчётом не заменяются.

## Адресные доказательства v2 и статусы замечаний

- QA-CALC01 / P1 / исправление принято главой по коду и переданным Gradle regression; независимый повтор QA ещё ожидается. Прежний `К оплате 123.45 KZT` мог стать high без оплаты. ReceiptParser теперь добавляет UNSUPPORTED_EVENT без явного line-start признака completed/paid, при вопросе/частичной оплате/отрицании; извлечённые amount/currency/date остаются структурированным кандидатом для review. Строка статуса без суммы не становится вторым total. ParserTest содержит bare-total/кассовый-чек/subtotal/сдача negative cases, положительные paid/completed варианты и отрицательные/частичные статусы. Наличие конкретных явных маркеров не доказывает поддержку любых чековых форматов; неподдержанный оплаченный чек может требовать review.
- BD5-01 / P1 / закрыто, добавлено непосредственное чтение исправления. В ParseSupport прочитан общий NON_FINAL/requiresReview guard с Unicode boundaries, отрицаниями, планированием/будущим и contractions; BankMessageParser вызывает requiresReview до confidence. Прочитаны отрицательные и завершённые regression cases ParserTest. Общая эвристика не объявляется проверенной для всех формулировок всех банков.
- BD5-02 / P1 / закрыто, добавлено непосредственное чтение исправления. MainActivity создаёт Intent с putExtra/addFlags, затем отдельным statement вызывает void setClipData; дальнейший startActivityForResult использует этот Intent. Итоговая компиляция прошла у начальника; реальная камера не испытывалась.
- AND-SEC06 / P2 / закрыто в адресном head review: OcrEngine импортирует `androidx.exifinterface.media.ExifInterface`, build.gradle включает `androidx.exifinterface:exifinterface:1.4.2`; обработка orientation сохранена. Начальник подтвердил успешную сборку и исчезновение EXIF lint warning. Это не доказательство отсутствия всех ошибок декодирования изображений.
- AND-SEC07 / P2 / исправление принято главой по коду и переданным двум JVM tests; отдельный security readback ещё ожидается. Rejection handler вызывает captureGap и публикует безопасный LIMIT_EXCEEDED через runtime.notificationError, который вызывает refresh. Общий NotificationStatus monitor охватывает sticky overflow flag и саму публикацию; success после overflow публикует предупреждение, а не null. В NotificationStatusTest прочитаны success-after-overflow и latch/actual BLOCKED-thread regression против check/clear race. JVM проверяет production monitor, а не реальное переполнение Android NLS. Android delivery и service lifecycle не объявляются проверенными.

Команды главы этого повтора: `Get-Content -LiteralPath <ReceiptParser.java, ParseSupport.java, ParserTest.java, NotificationStatus.java, NotificationStatusTest.java, BankNotificationService.java>`; `rg -n -C 3 <NotificationStatus/notificationError> NativeRuntime.java`, `rg -n -C 4 <setClipData/ExifInterface/exifinterface> MainActivity.java OcrEngine.java app/build.gradle`; `rg -n 'requiresReview|BLOCKED' BankMessageParser.java`; `Get-FileHash -LiteralPath <перечисленные ниже файлы> -Algorithm SHA256`. Ожидание: исправления присутствуют и рассмотрена точная версия. Факт: адресные изменения прочитаны, SHA-256 вычислены успешно; два переданных контрольных хеша ReceiptParser/ParserTest совпали. Новых runtime-команд глава не запускал.

Проверенные SHA-256 (пути относительно `android-app/app/`):

- `src/main/java/com/ladno/finance/core/ReceiptParser.java`: `C7290550715BF853D52304C4BF87700C4E14D734B46D0105D062580091527C43`.
- `src/test/java/com/ladno/finance/core/ParserTest.java`: `79FD47F1620A0B5162D2D80143651C2F88BA8363DA89FF659C9D9DA7D3341C58`.
- `src/main/java/com/ladno/finance/core/ParseSupport.java`: `55A9CC3221D0D71D67C3012A9A562521861117935796ED65B880CB0C603CEA11`.
- `src/main/java/com/ladno/finance/core/BankMessageParser.java`: `7BB3CA3E3A0714DF7317B8E03A55CDDC5D3551F45429BB10AF8E13DDE949CC3A`.
- `src/main/java/com/ladno/finance/MainActivity.java`: `FE161AEA8C84CBF03A9F1DA136159702D3FC1A0F77D62A27B499411600B6DC4F`.
- `src/main/java/com/ladno/finance/OcrEngine.java`: `8351A1FD4939AFA31587D2D1B874E10DC5C8E6EAE25B6E9995E7CE52DFC81BCB`.
- `src/main/java/com/ladno/finance/NotificationStatus.java`: `BC3CEE982DE669D3FBBCDA31F17A6FB0CF7F7A4D00B085C0E628BC31A00BFACA`.
- `src/main/java/com/ladno/finance/BankNotificationService.java`: `EF1D6DA43E4F1BE253D6371055DCD23F9BCF5D638A99AFF3AE9EA80A50B76C76`.
- `src/main/java/com/ladno/finance/NativeRuntime.java`: `55E1C1EB3A3C1467AAEC0694DB51D7C34925E6DE1E32408533DA62164B5B2705` (в повторе прочитан изменённый интеграционный участок).
- `src/test/java/com/ladno/finance/nativebridge/NotificationStatusTest.java`: `07D51A8B20EE8C4DA0C2ACEBAC81A51477FA911BC9E4FE2B50456918089E11D3`.
- `build.gradle`: `57F29E5E42F78BD5DE0C8957F18001C16150AC78EF8D0AA4E3AACCDD367E36F9` (владение начальника; проверена EXIF dependency).

Оставшиеся вопросы v2: дождаться независимого QA-CALC01 rerun и security readback AND-SEC07 по этим же хешам; Android SQLite persistence/concurrency, реальное NLS/burst/revoke, камера/URI и JNI/OCR/device corpus остаются невыполненными в проверке главы. «Все банки» — цель покрытия. Следующее действие: сопоставить результаты независимых проверок с указанным fingerprint и передать ту же версию суду; при новых обязательных findings вернуть их владельцу. Ниже сохранена история v1; прежние открытые статусы AND-SEC06/07 и ожидание повторного чтения заменены результатами v2 выше.

## История первоначального прохода v1

## Задача и проверенная версия

Основания: AGENTS.md, team/PROTOCOL.md, полный профиль главы, APP-005, продуктовые и архитектурные материалы APP-004, Android CONTRACT v2 и MONEY v2. Реальные авторы, запущенные начальником от имени главы: `/root/android_data` и `/root/android_native`.

Глава прочитал фактические core/storage/schema и native bridge/listener/OCR/Activity/Manifest первого авторского этапа. После этого data-автор исправил BD5-01, native-автор исправил ошибку камеры. Начальник сообщил успешную Gradle-сборку исправленного этапа: источник сборки `.cache/work/ladno-b06771953d4ad6dd`, лог `android-app/dist/gradle-build.log`, 19 JUnit methods, включая 7 методов ParserTest, lint без ошибок с 4 предупреждениями. Эти результаты выполнены начальником, а не главой. Полного повторного чтения исправленной версии и фиксации SHA-256 в этом ограниченном проходе не было; после сборки продолжаются изменения UI/native, поэтому это не единая замороженная версия для приёмки.

## Проверка каждого исполнителя и совместимости

- Data: Money использует canonical decimal strings и BigInteger minor; лишняя дробная точность и неверные календарные даты отклоняются. LedgerStore суммирует выбранные currency/month в Java без SQL SUM/float; categoryId=null не исключается из итогов. `state`, `execute` и `ingestCandidate` сериализованы общим монитором; state дополнительно читает SQLite snapshot. Проверены CAS/revision, атомарное удаление категории с переводом ссылок в null, soft-delete и сохранение source/dedup связей. Для первой схемы v1 неизвестные upgrade/downgrade завершаются ошибкой без удаления данных; фактической миграции старой версии здесь нет.
- Data ingress: проверяются source и typed candidate; raw paymentReference запрещён в storage, HMAC-токены ограничены hex64. Exact source revision идемпотентен; слабое совпадение суммы/соседних дат оставляет review; trusted references сравниваются в provider/account namespace. Код допускает null category и сохраняет ID/source при ручной правке.
- Native: AppBridge имеет один `request`, whitelist действий, строгие ключи/типы и безопасный envelope. Native вызывает общий LedgerStore, не создаёт вторую БД. Listener проверяет текущий профиль и выбранный package до чтения extras, исключает group summary, не читает SMS inbox и не исполняет notification actions. При недоступности Keystore просмотренная исправленная ветка прекращает ingress с UNAVAILABLE; случайного revision fallback в прочитанном SourceTokens уже нет.
- Native OCR: камера/выбор документа реально направлены в приватный временный файл и TessBaseAPI; предусмотрены ограничения bytes/pixels, ошибки, очистка временных копий и безопасная передача нормализованного кандидата. LocalAssets/MainActivity ограничивают WebView шестью точными локальными URL; callback не вставляет пользовательские поля в JavaScript. Наличие веток кода не подтверждает их работу на устройстве.
- Межмодульные API state/execute/ingestCandidate и envelope согласованы с CONTRACT v2 по прочитанному коду. Аддитивное `permissions.notificationError` передано начальником для UI; его окончательное отображение и последующие изменения входят в следующий точечный проход.

## Доказательства: шаг, ожидание, факт

- Глава: `Get-Content` перечисленных core/storage/native Java, schema.sql, ParserTest и Manifest; сопоставление с CONTRACT v2/MONEY v2. Ожидание: совместимые типы/API, точные деньги, атомарность и безопасные границы. Факт: перечисленные выше свойства подтверждены чтением; найден BD5-01 и возвращён автору через начальника. Это статическая проверка, не Android runtime.
- Глава: локальный `javac -encoding UTF-8 -cp <android-36/android.jar> -d <TEMP/ladno-head-review-APP005> <core/*.java>`. Ожидание: core компилируется. Факт: javac завершился без ошибок; это только core, не APK и не выполнение org.json методов.
- Глава: попытка JShell-прогона синтетических контрпримеров. Факт: стандартный JShell не запустил execution engine из-за sandbox socket/Windows preferences ограничений; повторный запрос был прерван. Успешное выполнение parser cases главой не заявляется.
- Data-автор: `python android-app/app/src/test/java/com/ladno/finance/storage/schema_contract_test.py`. По переданному результату: 46 assertions на реальном SQLite, включая закрытие/повторное открытие файла; это SQL/schema test, не исполнение Android LedgerStore.
- Data-автор BD5-01: отдельный JVM reproduce до исправления дал `high` для двух незавершённых сообщений, после guard — `uncertain` с UNSUPPORTED_EVENT; завершённые положительные примеры остались `high`. Автор сообщил успешный javac `--release 17` и добавленные regression cases. Эти шаги выполнены автором.
- Начальник: реальный Gradle assemble + unit tests + lint, лог `android-app/dist/gradle-build.log`. По сообщению начальника: BUILD SUCCESS, APK собран, 19 JUnit methods прошли, 7 ParserTest methods прошли, lint 0 errors / 4 warnings. Команда сборки в этом проходе главой не запускалась; контроль APK на телефоне не выполнялся.

## Замечания с ID и приоритетом

- BD5-01 / P1 / закрыто по авторскому reproduce и переданному Gradle regression: широкий русский маркер «покупка/оплата» позволял `Запланирована покупка 10.00 RUB 2026-11-02` и `Оплата 10.00 RUB 2026-11-02 не произведена` достичь high. Data-автор добавил общий NON_FINAL/requiresReview guard для bank/receipt и отрицательные/завершённые regression cases; начальник подтвердил 7 ParserTest methods PASS. Исправленный текст guard в этом ограниченном проходе повторно не читался.
- BD5-02 / P1 / закрыто по реальной компиляции: в MainActivity цепочка после `Intent.setClipData`, возвращающего void, блокировала сборку (`void cannot be dereferenced`). Ошибка обнаружена компилятором начальника, исправлена native-автором; последующий Gradle BUILD SUCCESS подтверждён начальником. Это не проверка камеры на устройстве.
- AND-SEC06 / P2 / открыто, внешний security finding: OcrEngine использовал android.media.ExifInterface; lint предупредил о известных проблемах старых Android реализаций. Начальник добавил AndroidX dependency 1.4.2; замена import оригинальным native-автором ещё ожидается. Исправление и последующая сборка здесь не подтверждены.
- AND-SEC07 / P2 / открыто, внешний статический security finding: rejection handler очереди listener вызывает captureGap, но не выставляет notificationError/refresh; потерянное при переполнении событие может остаться невидимым. Подтверждённый кодовый путь, без runtime reproduce. Рекомендуется авторская безопасная ошибка через существующее поле и адресная проверка.

## Оставшиеся вопросы и следующее действие

Android SQLite/runtime/restart/CAS concurrency, NLS delivery/revoke/regrant/lifecycle, Keystore failure, переполнение очереди, камера/document URI, JNI/Tesseract OCR accuracy и Cyrillic corpus на устройстве в этом проходе не выполнялись. JVM и SQL результаты не заменяют их. Универсальная совместимость банков не доказана; «все банки» остаётся целью покрытия. Личные уведомления/изображения не читались, права пользователю не выдавались, установки APK на его телефон не было.

Начальнику: вернуть AND-SEC06/07 исходному native-автору; после исправлений выполнить сборку/затронутые проверки и короткое повторное чтение главы по изменённым файлам. Затем передать зафиксированную версию независимому QA и суду. Итоговая готовность APP-005 этим отчётом не утверждается.
