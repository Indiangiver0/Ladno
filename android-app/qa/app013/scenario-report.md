# APP-013/014: независимые сценарные проверки

2026-11-04, Asia/Qyzylorda. Исполнитель: `/root/qr_qa_lead/qr_scenario_checks`, scenario-test-engineer, не автор реализации. Прочитаны AGENTS.md, PROTOCOL, полный профиль, QR contract/security, backend plan/implementation и frontend review. Это результат специалиста для отдельной проверки qa-lead.

## Подготовлено

- Новый `QrIndependentTest.java`: семь JVM методов с независимо заданными ожидаемыми суммами KZT scale2, строгими календарными датами, отказом от нуля/лишней точности/экспоненты/неверного URI, выбором одного QR и сохранением неоднозначности. Примеры основаны на контракте известной маски КГД, синтетические i/f; HTTP не выполняется. Проверяются также полнота notification views, `теңге/тенге/тг`, явные направления и отрицание/конфликт сумм. Входы/ожидания заданы до вызова production parser, не вычисляются его результатом.
- Новый `QrImportRuntimeTest.java`: один составной Android API тест. Генерирует PNG через настоящий ZXing encoder, передаёт private owned file реальному QR scanner → `processMedia` → SQLite и проверяет реальные `state.jobs`/outcome. Проверяет точные `1234.50 KZT`/`2026-11-04`, неизвестный kind/review, отсутствие autoledger/raw URL, повтор того же QR из нового файла, сохранение ignore, ошибки точности/даты/payment/multiple/mixed QR без пустых incoming. Проверяет release active media/camera file, отсутствие INTERNET и notification assembler в Android process.
- Зарегистрирован **настоящий внутренний `NativeRuntime.Job` через test-only reflection**. Это не вызов `queueAction`, поскольку тот требует Activity и открывает системный UI. Нельзя называть такой тест проверкой камеры/галереи или Activity workflow.
- Новый runner `run_native.py`: до любого тестового действия сверяет exact AVD `Ladno_QA_API30` на explicit serial `emulator-5580`; затем build version/hash, staged test source, SHA установленных main/test APK. Запускает только `am instrument` с данным native test class и обязательным аргументом синтетической AVD. Не содержит install/clear/UI commands. Отдельно требует `OK (1 test)` и все записанные assertions pass.

## Выполнение на этом этапе

`python -m py_compile android-app/qa/app013/run_native.py`: запущено, exit0. JVM/Gradle и native suite ещё **не выполнялись этим специалистом**. Root сообщил о запущенной общей сборке с четырьмя задачами; результат на момент этого промежуточного отчёта неизвестен. Root запускает сборку/установку на выделенный AVD и runner, затем передаёт фактический результат для разбора. Это разделение исполнителя команд должно сохраняться в итоговом QA.

## Ограничения

Настоящие QR пользователя, фотографирование бумаги, Activity/queueAction, камера/галерея UI, реальный OS notification delivery/permission lifecycle и all-bank corpus здесь не испытаны. Прямой native assembler вызов не означает проверку Android NotificationListenerService. Сетевой ответ ОФД и универсальный API не реализованы и не проверяются. Физические телефоны и личные данные не использовались. Старая камера P2 и отсутствие системного события исходящего Kaspi этим не закрываются.

Следующее действие: root завершает build/install/`python android-app/qa/app013/run_native.py`, возвращает stdout + `native-run.json`/`native-runtime-evidence.json`; специалист проверяет фактические результаты и передаёт их qa-lead. До этого PASS новой версии не заявляется.

## Исправление тестового harness после фактического compile FAIL

Root выполнил общую сборку, сохранённую в `team/reports/APP-013-build-second.log`. Я прочитал её ошибку: `:app:compileDebugAndroidTestJavaWithJavac`, QrImportRuntimeTest.java:106, `running(String) has private access in NativeRuntime`; BUILD FAILED. Это ошибка моего тестового вызова, не подтверждённый дефект QR продукта. Root отдельно сообщил о 80 перечисленных JVM тестах (79 pass, 1 диагностический skip), этот счёт на данном этапе самостоятельно не перепроверялся.

Исправлен только harness: реальный private `running(String)` вызывается через test-only reflection, так же как регистрация внутреннего Job. Ожидания/фикстуры/входы не изменялись, production visibility не расширялась. Остальные вызванные NativeRuntime методы проверены по объявлениям: state/mediaJob/cameraOutput/processMedia/newCameraFile доступны этому package. Повторная компиляция и native запуск ожидаются от root; предыдущий FAIL не скрывается.

## Первый native запуск: неверное ожидание outcome, сохранённый FAIL

Root сообщил о третьей успешной Gradle сборке (80 listed/79 pass/1 diagnostic skip), установке обоих APK на synthetic AVD и первом фактическом запуске runner. Я прочитал raw native evidence: 27 записанных assertions, 26 pass и один FAIL — после ignore ожидался job outcome `ignored`, фактически `duplicate`. До него прошли точные QR поля/null kind/noledger, повтор QR из нового файла и cleanup. Дальнейшие ошибки/multiple кейсы из-за fail ещё не выполнялись. Этот запуск не был PASS.

Точные первые файлы сохранены до повторного запуска:

- `native-run-first-failure.json`, SHA256 `bd107eee04163457f4e38370bf3da2ac7150e227f70106fd418e1f9ac70d633b`.
- `native-runtime-evidence-first-failure.json`, SHA256 `8e92ba6472ca8a905fbc56d305e5f16b6b893de8a7eb7330ef0ccc523f13364e`.

Причина тестовой ошибки установлена чтением **существующего** `CONTRACT.md` (разные Import.status и ingest.outcome, exact revision идемпотентен, ignore не проводится повторно), `LedgerStore.ingest` (exact source/revision сразу возвращает outcome duplicate с существующим кандидатом) и `ScanOutcome` (проецирует ingest outcome, а не candidate.status). Мой предыдущий assert смешал результат повторного наблюдения с сохранённым решением.

Исправление не меняет production, входы QR или критерий ignore: ожидается `duplicate` для exact-repeat job outcome, а вместо косвенного предположения добавлены прямые read-only SQL assertions сохранённого `status=ignored`, неизменной revision после ignore, того же candidateId, отсутствия новой candidate строки и отсутствия ledger записи. Эти новые проверки ожидают выполнения в следующей сборке. Полный PASS не заявляется по первому частичному запуску.

## Финальные фактические результаты 0.3.0/code3

Проверяемая версия: main APK SHA256 `a8ec70cd97674fb6f9c8d224e10139611c52a4ac25f69f6cd26143f7d88e5b76`, 68 393 641B; sourceFingerprint `85db77f1cbb5412032dfdc869c4c14faadc07def9992ab620d9a6e05ead03284`; тестовый APK SHA256 `95da5b07020819f2e0fa83e34ac53374dda6e1973174609f9a4fde9efd9ca769`. Финальный stage `ladno-85db77f1cbb54120`.

- **Команды root:** четвёртая Gradle сборка всех четырёх задач завершилась BUILD SUCCESS; установка main/test на guarded synthetic AVD — Success. Root выполнил `python android-app/qa/app013/run_native.py`, второй native запуск. Эти команды специалист не присваивает себе.
- **Самостоятельно прочитано специалистом:** финальные `native-run.json` и `native-runtime-evidence.json`, 82 assertions, все pass, failure отсутствует, runner `passed=true`. Проверены конкретные прямые SQL результаты: после повторного QR кандидат остаётся `ignored`, revision2, нового кандидата/ledger нет; пять error cases excess precision/invalid date/payment/two fiscal/mixed имеют ожидаемые видимые ошибки. Runner transcript включает exact AVD identity и actual installed APK hashes до native запуска. Это настоящий runtime результат, не вывод по одному чтению кода.
- **Самостоятельно сверены final staged JUnit XML:** 80 listed, 0 failures/0 errors, 1 skipped, следовательно 79 исполненных pass. Независимый `QrIndependentTest`: все 7 методов pass, 0 skip. Пропущен именно `ReceiptQrDecoderTest.readableQrNextToDamagedQrRequiresAnotherPhoto`: диагностический сценарий с нераспознаваемым соседним QR, отмеченный автором как ограничение ZXing. Его нельзя считать пройденным. Успешные multiple/mixed проверки относятся к распознаваемым QR.
- **Команды самого специалиста:** `python android-app/qa/app013/verify_artifacts.py`, финальный exit0: 108 identity assertions pass, в том числе actual SHA/размер/versionName/code3, все **94** текущих source bytes совпали с build-source-manifest, sourceFingerprint совпал с metadata. `apksigner verify --print-certs` для нового main и archived0.2.0 завершился exit0; сертификат SHA256 у обоих `08f77eff74101bd48dc2cccb52403216620cc8e1bb3a86fd685c640c9a64ec27`. `aapt2 dump badging`: package `com.ladno.finance`, version0.3.0/code3, minimumSDK26, targetSDK36, INTERNET permission отсутствует. Raw tool output и проверки сохранены в `artifact-identity.json`.

Первая попытка identity script завершилась FAIL только в моём парсере имени поля minimum SDK: этот aapt2 печатает `minSdkVersion:'26'`, а helper искал legacy `sdkVersion:'26'`. Ожидаемое значение26 не менялось; parser принимает оба названия и явно сравнивает значение. Первая запись сохранена `artifact-identity-first-failure.json`, SHA256 `1426a556ba259e11cfe72a8364347460effe63d5c0539e98462c7fee3ea50862`. Это ошибка проверяющего harness; повторный запуск обоснован исправлением parsing, production/источники APK не менялись.

В проверенном локальном QR/notification-prefill объёме новых подтверждённых P0/P1 не обнаружено. Сохранены все прежние ограничения: исходная цель сетевого API ОФД ещё не выполнена; реальные QR и тексты уведомлений пользователя отсутствуют; Freedom/Halyk известны только по названиям и не являются проверенными шаблонами. Отсутствующий OS event не восстанавливается. Native internalJob registration не закрывает Activity/queueAction/бумажную камеру P2. Прямой assembler тест не является actual NotificationListenerService OS delivery.

Следующее действие: qa-lead независимо читает эти свидетельства и делает своё заключение для суда по конкретной версии; собственная приёмка специалистом не объявляется. Повторять уже прошедшие тесты или менять source без новой ошибки не требуется.

Уточнение отображения доказательств для главы: raw `native-runtime-evidence.json` прочитан как UTF-8 и проверен через escaped Unicode codepoints; во всех пяти error.message корректный русский текст, 0 U+FFFD и 0 вопросительных знаков. В raw bytes также 0 последовательностей EF BF BD. Возможные знаки замены при выводе терминала не содержатся в сохранённых данных приложения. Повтор native теста для этого не выполнялся.
