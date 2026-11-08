# APP-013 / APP-014: отдельная проверка главы backend-data

2026-11-04, Asia/Qyzylorda. Глава `/root/import_backend_lead`. Проверяемая рабочая версия: 0.3.0/code3, ещё до итоговой сборки/проверки APK на момент этого отчёта. Владение главы: только этот новый отчёт; исходники исполнителей не редактировались.

## Исполнители и границы

Chief от имени главы реально запустил двух разных специалистов: `/root/bank_capture_engineer` (backend-engineer, уведомления) и `/root/receipt_qr_data` (data-engineer, local QR pipeline). Полные профили/контекст переданы исполнителям chief; в первоначальном этапе плана глава специально не занимал слоты запуском. Материалы: `team/reports/APP-014-bank-implementation.md` и дочитанный отдельно `team/reports/APP-013-qr-implementation.md` v2, SHA256 `9fb785daa56f32a21720bbadc78fbf554d751d178b53a30e94587e062f729a3b`. Данный отчёт не приписывает главе написание кода или независимый QA.

Глава повторно прочитал AGENTS.md/PROTOCOL, полный собственный профиль, APP-014 план, контракты QR architecture/security и реальные изменённые Java файлы/тесты. Прежняя судебная версия сохранена chief в `team/snapshots/android-alpha-v3-files`; старые APK/вердикты не заменяются.

## Проверено главой по коду

- BankNotificationService сохраняет выбранный пакет/свой профиль/доступ/initialized проверки **до** Notification extras; group summary не превращается в одну операцию. Новый NotificationText объединяет все предоставленные представления, убирает лишь равные целые представления/совпадающие целые строки-подпоследовательности. Две разные строки с равными суммами и повтор внутри одного представления не схлопываются. Ограничены исходные и совокупный текст, а также число textLines. LIMIT сохраняется прежним unavailable transport, не выдуманной денежной записью. Raw text не передаётся в DTO и не логируется.
- BankMessageParser нормализует отдельные тг/тенге/теңге только локально, добавляет LOW_CONFIDENCE. Явные входящие/исходящие формулировки заполняют направление с UNSUPPORTED_EVENT/review; свои счета и конфликт направлений не дают kind. Старые guards отрицания/отмены/комиссии/баланса и согласие на postTime не ослаблены. Это предзаполнение, не неподтверждённый шаблон автоматического P2P.
- FiscalQrParser локально принимает только два точных известных host/path и четыре однократных поля i/f/s/t. URI authority/path проверяются до query decode; duplicate/unknown key, userinfo, port, fragment, поддомен, encoded path отвергаются. Не выполняется HTTP/HTML/WebView загрузка. Сумма KZT exact scale2, положительная, без float/округления; календарь и часы строгие, без UTC-пересчёта local timestamp. Вид, оплата, категория и банковская принадлежность не выводятся из QR; candidate всегда null kind/uncertain/review с пустым note/reference.
- ReceiptQrScanner ограничивает файл/исходные и декодируемые пиксели, учитывает EXIF и освобождает Bitmap до QR detection. ReceiptQrDecoder использует QR-only ZXing и явно отвергает несколько различных/смешанных **декодированных** payloads и structured append. Отсутствие декодированного QR сохраняет прежний OCR; unknown/multiple decoded QR возвращают ошибку. На поддерживаемом QR OCR не выполняется.
- NativeRuntime пишет только canonical candidate и purpose-separated HMAC locator/revision. Fiscal locator не объявлен strong bank payment reference. Повтор одного QR из разных фото имеет один канонический source/revision; изменённые поля меняют revision и остаются review. Raw locator/URL не попадает в jobs/JS/SQLite. QR и image источники разделены по namespace.
- LedgerStore ingest не auto-post кандидата с null kind/reasons. При ручном выборе вида review() заново проверяет weak matches по точной сумме/валюте/виду/соседним датам и требует confirmDistinct; QR null kind не обходит этот этап. Одинаковые слабые совпадения не склеиваются без согласия. HMAC/ignore/tombstone механика намеренно сохранена; новая миграция для этого объёма не нужна.

Глава сам сравнил SHA256 SourceTokens.java, LedgerStore.java, ParseSupport.java, ReceiptParser.java, ImportCandidate.java и прежнего ParserTest.java с manifest архивированной v3: **все шесть неизменны**. Это узкая проверка затронутых инвариантов, не повторный полный аудит 402 файлов.

## Фактически запущенная отдельная проверка главы

Настоящий JDK21.0.13+9: `javac --release 17 -encoding UTF-8`, реальные JUnit4.13.2/Hamcrest1.3/org.json20240303/ZXing core3.5.3, без Android stubs. Компиляция всех pure Java core классов плюс NotificationText/ReceiptQrDecoder и семи JUnit классов во временный каталог `TemporaryDirectory(prefix='ladno-app013-head-')`, удаляемый штатным контекстом. Запуск `org.junit.runner.JUnitCore`:

```text
com.ladno.finance.core.MoneyTest
com.ladno.finance.core.ParserTest
com.ladno.finance.core.ReceiptOcrConsensusTest
com.ladno.finance.core.BankMessageExpansionTest
com.ladno.finance.NotificationTextTest
com.ladno.finance.core.FiscalQrParserTest
com.ladno.finance.ReceiptQrDecoderTest
```

Ожидание: прежние денежные/OCR гарантии сохранены, новые aliases/extra views/направления не auto-post сомнительные операции, synthetic RGB QR даёт точные поля и multi/unknown не выбирает первый. Факт: **javac exit0; JUnit exit0, OK (59 tests), 0.266s**. Это отдельный запуск главы поверх 44 тестов bank специалиста, а не повторное цитирование его результата. ZXing использованный JAR SHA256 `8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82`.

Прочитаны новый bundled ZXing-NOTICE.txt и начало полного Apache2.0 LICENSE; Gradle закрепляет `com.google.zxing:core:3.5.3`. Итоговую упаковку лицензии/артефакта и dependency provenance должен подтвердить build/QA; просмотр текста не выдан за аудит upstream лицензирования.

## Идентификатор source bytes первого этапа проверки главы

```text
f642b386022efcf465cf347ba74ecae3c1abee3ba94cc0783b3de2b3988b393d BankNotificationService.java
85ef2f257e5881b78f98cbe55669ebf8ab367e433698a5de99ddcffaaf23bb26 NotificationText.java
2cc03a6c3c5f18f45126712f8d82220b45884d5279f950cd50170906b6fce440 core/BankMessageParser.java
d4139c7eb18e1d910db5107a7b3932ae159ae579911e84ac681737bc0ad53a78 ReceiptQrDecoder.java
f031962ef9baf0c9420d93a1309d15fd59931a541c586fc69c4a9a8da6fd152f ReceiptQrScanner.java
81268416ab6a7530d6e3a045a5f58e74aee48150c2ee472ccf11accd7e3f775e core/FiscalQrParser.java
1dcf1979b90e81496f59e19ea4093d6ab1a2cea1004f96d69cf24b8b60c89d62 NativeRuntime.java
```

## Замечания и следующее действие

- **BD13-01 / P2 / native граница:** на первом этапе head JVM запуск не компилировал Android service/scanner/runtime и не исполнял registered media job/SQLite на устройстве. Итоговые Android build и synthetic registered-job API свидетельства получены и проверены в последнем дополнении ниже. Полный camera/gallery UI и настоящий пользовательский чек остаются непроверенными.
- **BD13-02 / P2 / ограничение покрытия:** настоящий QR и пропущенные банковские payload пользователя не предоставлены. Полный API ОФД, товарные строки, тип/оплата и исходящие Kaspi без OS-event не реализованы. Сетевой adapter не включён; нельзя сообщать пользователю, что подключён универсальный API или полная автоматизация.
- **BD13-03 / P2 / значимое ограничение источника:** decoder теперь явно отвергает несколько detected symbols с читаемым payload и checksum/format failure другого символа. Но detector может вообще не найти повреждённый соседний QR. Тогда один читаемый QR может дать предзаполнение; отклонение всех визуально присутствующих, но недетектируемых QR не гарантируется. UI просит один QR, кандидат всегда unknown kind/review, неверная автоматическая денежная проводка не установлена. Ограничение не блокирует передачу к QA ограниченной alpha, но не должно называться исправленным или гарантией «никакие два QR не принимаются».

Подтверждённых новых P0/P1 при этой узкой проверке не найдено. Bank source/JVM этап отдельно проверен главой; QR source/JVM этап допускается к сборке/независимому native QA с указанными ограничениями. Build/runtime доказательства нужно дочитать отдельным дополнением, затем chief направляет зафиксированную новую версию QA/суду. Полная продуктовая готовность и установка на физические телефоны этим отчётом не объявляются.

## Дополнение: финальный QR отчёт и сохранённый диагностический FAIL

Глава отдельно прочитал финальный отчёт второго специалиста, `team/reports/APP-013-decoder-first-failure.xml` и новый guard/`@Ignore` в actual source. Исходная фикстура повреждённого соседа и её assertion сохранены; один диагностический метод `readableQrNextToDamagedQrRequiresAnotherPhoto` пропущен с объяснением отсутствия гарантии детекции. Первый фактический FAIL не удалён и не объявлен PASS. Из этого эксперимента нельзя выводить, что ZXing дал DetectorResult повреждённого соседа.

Финальные изменённые SHA256: ReceiptQrDecoder.java `ce6542817bf4df4e0a5cbf851b3a7650907f7889b11e2131c130e6a711fd046d`; ReceiptQrDecoderTest.java `9d9d2413ae106b31ac1f87d7a796158bdd0738b2dbc362f2f4b15c8711bcc371`. Остальные первоначально перечисленные source bytes не менялись по передаче специалиста; итоговая source/build связь проверяется root/QA.

После этого изменения глава заново **сам** скомпилировал pure Java core/ReceiptQrDecoder и два QR JUnit класса настоящим `javac --release17`. Временный HeadCheck использовал JUnitCore Result.getRunCount/getIgnoreCount/getFailureCount: **javac exit0, JUnit exit0, run=15, ignored=1, failed=0**. Это 15 прошедших проверок и один пропущенный diagnostic, не 16 успешных тестов. Предыдущие 59 PASS выше относятся к первому этапу до нового diagnostic/guard; история сохранена.

Chief сообщил о следующем реальном общем Gradle этапе: unit tests завершились с одним skipped diagnostic, но `compileDebugAndroidTest` завершился ошибкой QA fixture (`QrImportRuntimeTest.java:106`, private `running(String)`). Глава не запускал эту сборку и не выдаёт её за успешную; исправление test harness поручено автору QA. Product visibility/QA source главой не менялись. Финальный native build/runtime допуск пока **pending**, глава освобождает слот до получения повторной сборки и фактических результатов.

## Финальная проверка главы по фактической Android сборке и native API доказательствам

На отдельном заключительном этапе глава дочитал `android-app/dist/build-result.json`, `build-source-manifest.json`, `team/reports/APP-013-build-final.log`, все итоговые runtime checks в `android-app/qa/app013/native-runtime-evidence.json` и их привязку в `native-run.json`. Самостоятельными read-only Python SHA256/JSON/XML проверками установлено:

- Итоговый основной APK: **ladno-0.3.0-debug.apk, 0.3.0/code3, 68 393 641B**, SHA256 **`a8ec70cd97674fb6f9c8d224e10139611c52a4ac25f69f6cd26143f7d88e5b76`**. Фактические байты совпадают с build-result, совпадает embedded в native-run build metadata.
- Source fingerprint **`85db77f1cbb5412032dfdc869c4c14faadc07def9992ab620d9a6e05ead03284`**. Все **94 manifest source файла** найдены; actual SHA256 каждого совпал с build-source-manifest, его fingerprint совпал с build-result. Глава не пересобирал APK и не изменял source.
- В финальном log сохранён **BUILD SUCCESSFUL in 1m21s**; четыре задачи assembleDebug/testDebugUnitTest/lintDebug/assembleDebugAndroidTest перечислены в build-result. Сам глава агрегировал 13 финальных JUnit XML: **80 listed, 79 passed, 0 failures/errors, 1 skipped**. Сам прочитал итоговый stage lint XML: **0 errors, 3 warnings**. Предыдущие build first/second FAIL и third SUCCESS не подменены финальным логом.
- Native-run относится к guarded synthetic AVD **Ladno_QA_API30**, API30, synthetic-only; **82 checks, все pass, runtimeFailures=[]**, uiExercised=false. На AVD прочитан SHA установленного main APK, совпадающий с указанным выше; test APK SHA `95da5b07020819f2e0fa83e34ac53374dda6e1973174609f9a4fde9efd9ca769`. Actual test source SHA **`5f044e5ef55f7aa4b9d95daaf1fccdb557d5b00e5a6a894b04f63b82c3632639`** глава сам пересчитал, он совпал с native-run.
- Прочитанные checks подтверждают synthetic PNG → QR decoder → NativeRuntime.processMedia → зарегистрированный test reflection job/state/outcome → SQLite: exact 1234.50KZT/2026-11-04/null kind/review, отсутствие автоматической ledger записи, канонический повтор из нового файла, отсутствие raw QR в DB, повтор ignored без нового кандидата/записи, invalid amount/date/unknown/payment/mixed decoded QR без пустого входящего. Проверены освобождение owned media/job слотов. NotificationText/BankMessageParser в Android test process дали 456.78KZT/income/review из textLines; это не доставка реального NotificationListener callback/Android extras.

Глава также дочитал сохранённые `native-run-first-failure.json` и `native-runtime-evidence-first-failure.json`: исходный FAIL ожидал job outcome `ignored`, получил `duplicate`. Проверил исправленную QA фикстуру: outcome `duplicate` соответствует точному source повтору, а persisted SQL отдельно утверждает **status ignored, revision2, тот же candidate ID, отсутствие нового кандидата и ledger записи**. Это исправление ожидания harness с усилением проверки состояния; предыдущий FAIL не объявляется успешным. Производственные storage/token контракты для него не менялись.

### Итог главы

**Работу обоих исполнителей принимаю в узком объёме для передачи независимому QA и суду по указанным APK/source bytes:** локальный поддерживаемый QR предзаполняет точные сумму/дату с ручным review; generic уведомления лучше сохраняют доступные поля и направления. BD13-01 закрыт только для Android compile и synthetic registered-job/SQLite API границы. Полный видимый camera/gallery сценарий, настоящие чеки/банковский корпус, невидимый повреждённый соседний QR (BD13-03 P2), сетевой API и автоматический исходящий Kaspi остаются ограничениями.

Подтверждённых P0/P1 на проверенных путях нет. Это отдельное заключение главы, не независимый QA/судебный вердикт, не объявление исходной цели API/all-banks выполненной и не разрешение на публикацию. Для ограниченной alpha допустима дальнейшая независимая приёмка с этими P2; неизменённые старые судебные решения не переписываются.
