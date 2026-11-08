# APP-017: независимые сценарные проверки ОФД

2026-11-04, Asia/Qyzylorda. Исполнитель `/root/qr_qa_lead/qr_scenario_checks`, scenario-test-engineer, не автор production реализации. Прочитаны AGENTS/PROTOCOL, полный профиль, APP-017, контракт v3 и security review. Реальные фото/QR/ответы пользователя не копировались в tests, отчёт или APK; внешних запросов специалист не выполнял.

## Подготовленные проверки

- `OfdIndependentTest`: восемь JVM методов с synthetic данными и независимыми ожиданиями. Проверяются точные деньги выше double-safe диапазона, сохранение исходных numeric lexemes, чрезмерная точность/exponent/sign/overflow, duplicate keys, required null/schema, number length64/65 и depth limits; sale/refund/cancel/unknown/currency contradiction; fiscal/rnm/total/calendar mismatch; exact payment total и итог после discount; metadata drift. Fake HTTPS проверяет фиксированный host/path/GET, отсутствие Cookie/Authorization, запрет redirects/auth/HTML/oversize и bounded caller deadline. HTTP не выполняется.
- `OfdIndependentRuntimeTest`: synthetic PNG → реальный scanner → production client с injected fake HTTPS connection → strict parser → **зарегистрированный внутренний Job** → SQLite. Ожидания: новый verified SALE создаёт один expense с точным decimal даже выше double-safe range; новое фото того же QR не создаёт второй. Legacy0.3 source/revision HMAC задаются буквальной старой формулой по независимым полям фикстуры, не вычисляются новым API decoder. Проверяются ignored, posted с изменёнными пользователем amount/date/note, linked и pending local fallback → API success, отсутствие promotion/overwrites/new rows; deletion/replay; synthetic POS/notification weak match без автоматического второго расхода. Это не фактическое уведомление банка или бумажная камера UI.
- Обязательно мигрирован прежний `QrImportRuntimeTest`: **до первого processMedia** в NativeRuntime.ofd установлен test-only Connections seam, который бросает IOException и не открывает соединение. Fictional IDs не передаются ОФД. Старая проверка отсутствия INTERNET заменена на явное наличие нужного разрешения и использование controlled offline seam; semantics local review остаётся прежней. Original0.3 evidence сохраняется в frozen v4 archive, не переписывается.
- Новый `run_native.py`: FIRST adb command exact AVD guard `Ladno_QA_API30`/`emulator-5580`; code4/name0.4.0, hashes текущих source/manifest и actual installed main/test APK; explicit три synthetic suite без all-tests, install/clear/UI. Файлы данных только synthetic. Отдельно проверяется наличие source `setBlockNetworkLoads(true)`; это static source evidence, не native UI запуск.

## Выполнение на промежуточном этапе

`python -m py_compile android-app/qa/app017/run_native.py`: exit0. Root сообщил о запущенной общей Gradle сборке; сами unit/native suite этим специалистом пока не выполнялись. Source объявлен стабильным root, во время его сборки не изменяется. Новый runtime использует test-only reflection внутреннего Job, **не** Activity.queueAction/камера/галерея или permission UI.

Production OfdReceiptClient на Android с реальными разрешёнными QR4/5 текущими synthetic тестами **не проверен**. Live fixture пока не был создан: это отдельный необходимый этап с explicit guard/opt-in/private transient input, без ingest/ledger; root уведомлён о пробеле. Нельзя выдавать исследовательский Python GET или mocked client за такой тест.

Ожидаются фактические build/unit/native результаты от root, затем независимое чтение свидетельств специалистом и отдельная проверка qa-lead. До них PASS, исправление всех ОФД/банков или готовность приложения не заявляются.

## Подготовка отдельного разрешённого live этапа

Chief отдельно назначил новый `OfdAuthorizedLiveRuntimeTest.java` и `run_authorized_live.py`, запретив создавать source до окончания текущей сборки. Сохранён только build-excluded `.java.template` в этой QA директории; source ещё не добавлен. Runner проверен `py_compile`, exit0; native/HTTP им не запускались.

Планируемый live test явно Assume opt-in до любого HTTP и требует выделенный synthetic AVD. Runner FIRST проверяет AVD identity и exact installed main/test/source hashes; берёт только существующие private decoded samples4/5, не печатает значения QR и не передаёт их в аргументах shell. Private input передаётся stdin в фиксированный app-private файл; stdout/stderr upload подавлены даже при ошибке. Template валидирует оба разрешённых чека до lookup, затем делает ровно два вызова **default** production client без Runtime/processMedia/store/ingest; сохраняет только безопасные amounts/date/kind/confidence/verified booleans. Ledger.db/WAL byte hashes сравниваются до/после без открытия SQLite, input удаляется finally. Прямого live результата на этом этапе ещё нет.

## Первый фактический synthetic pass и активированный live source

Root сообщил об общей успешной сборке всех четырёх задач, 0.4.0/code4, main SHA256 `3067506ade998a7b1e659fa28cca9db8c7ba034282a816bf7801d44356fc8ef9`, 68 393 973B, sourceFingerprint `23e20d7cf6e591d8e8490f6818952a1a698be2153bd7096a7d90ff27636b2293`. Guarded AVD boot и установка обоих APK — Success. Root выполнил synthetic runner, специалист отдельно прочитал сохранённые JSON: **187 assertions pass** (75 independent pipeline + 83 migrated offline QR + 29 author replay), runner passed=true, ни одной failure. Тестовый APK этого запуска SHA256 `d84d359c1be2a38172441048214e0ac07d7e590e98432e8d104c7698f831ebfe`.

До следующего запуска сохранены неизменные `*-first-success.json`: native-run SHA `d04b2d1a6782e205aa0a6dfa58e2f9a9a31273182aa643551ff1f1040a68c8d7`; independent SHA `f975445c23ebb1db38b882a7bce0d3e7e8b359f3dd2358700955d95402901ed2`; migrated QR SHA `21ea7ca7fd1ca98264229dd1f3fa3ffb50dbe77731f6a4863d1048dd9b394c82`; author replay SHA `e7968131db2b2f47abaa1bc43217ff5ba4f1da6c5c11ef07d4b5e7c713070927`.

После отдельного сигнала chief live template перенесён в `OfdAuthorizedLiveRuntimeTest.java`, SHA256 `a5c104f39115e11edddd599eeab2ab9a5bfeefe64c8516f60c10338869502a57`. Другие source на этом этапе не менялись. Требуется новая сборка test APK; live method ещё не исполнялся, никакого положительного default Android HTTP результата пока нет. Synthetic state очищать не требуется: новые значимые суммы/locator зависят от nonce, проверки считают baseline, старый QR fixture работает только в controlled review/ignore.

Подготовлен (пока не запускался на final metadata) bounded `verify_artifacts.py`: actual main/source hashes, same archived0.3 certificate, binary manifest package/code4/min26/target36/INTERNET и наличие OFD types/fixed endpoint в DEX, отсутствие live test class/private `.local` paths в main APK. `py_compile` exit0.

## Первый live harness FAIL до HTTP, исправление только runner

Root выполнил вторую финальную сборку успешно, main APK остался тем же, sourceFingerprint `d3db33b66c96b8c42ee2930fbdb5b5e750d6d28ad42905c70ec0d5fa564d1e6c`, test APK `e0610a1f278c18d40d5b27753d571ae735e3133e29ac8ad428ae043f8dbe5e66`; synthetic повтор root снова дал187 pass. Затем первый explicit live runner завершился FAIL: Java `FileNotFoundException` при открытии фиксированного private input, results пуст, input removed=true, cleanupAttempted=true. По порядку Java кода это произошло **до любого lookup/HTTP**. Это ошибка моего upload harness, не результат product API.

Исходные безопасные JSON сохранены до исправления: `authorized-live-run-first-failure.json` SHA `2a3a67294c0bd75f28ea327a38b850fcf3f9b18fac9fb5fab52766b67c6e324b`; `authorized-live-evidence-first-failure.json` SHA `57eb560fa161a03b6fc3a8e902605829e579905719d757558bb67c9f1d2d8be5`.

Исправлен **только Python runner**: вместо неоднозначной `sh -c` redirection используется direct `exec-in run-as ... tee files/qa-ofd-authorized.json`, private bytes остаются stdin. Echo tee подавляется и не сохраняется. До instrumentation runner теперь сверяет только stat size/SHA256 файла с in-memory input; mismatch останавливает выполнение до HTTP. Java/source/APK/входы/ожидания не менялись, повтор других passed tests не требуется. Новый live результат ещё ожидается от root.

## Final staged unit/native и ограниченная проверка APK

Самостоятельно прочитаны XML именно финального stage d3db33b66c96b8c4: **106 listed, 0 failures/0 errors, 1 skipped, 105 executed pass**. Независимый OfdIndependentTest: все8 методов pass. Прежний diagnostic skip с нераспознаваемым соседним QR не объявляется пройденным и не изменялся этой работой.

Повторные synthetic JSON root на этом же final build независимо прочитаны: runner passed=true, **187 assertions pass** (75+83+29), sourceFingerprint совпадает с d3db…d1e6c и installed test SHA e061…5e66. Это команды root, результат специалист проверил чтением свидетельств; собственный HTTP/ADB запуск не присваивается.

Специалист выполнил один bounded `verify_artifacts.py --expected-main-sha 3067506ade998a7b1e659fa28cca9db8c7ba034282a816bf7801d44356fc8ef9 --expected-source-fingerprint d3db33b66c96b8c42ee2930fbdb5b5e750d6d28ad42905c70ec0d5fa564d1e6c`: exit0, **123 identity assertions pass**, все103 source SHA соответствуют build manifest. Main SHA/размер/версия соответствуют final metadata. apksigner verify двух APK exit0, сертификат совпадает с archived0.3.0: `08f77eff74101bd48dc2cccb52403216620cc8e1bb3a86fd685c640c9a64ec27`. aapt2 binary badging: com.ladno.finance, 0.4.0/code4, min26/target36, INTERNET присутствует. В DEX есть OfdReceiptClient/OfdReceiptParser/OfdJson и fixed HTTPS endpoint; live instrumentation class и `.local` paths в main APK отсутствуют. Raw tool output сохранён в artifact-identity.json. Это ограниченная проверка identity/упаковки, не полный security audit.

## Второй live запуск: default client не дал verified результат

Root повторно исполнил live runner после исправления upload. Файл прошёл metadata guard, instrumentation дошла до lookup: оба разрешённых образца вернули известные local amount/date KZT, но kind=null/confidence=uncertain/verified=false; весь этап около0.84s, safe failure AssertionError, private input удалён. Это **FAIL default Android client**, не positive API acceptance. Причина transport/parser пока не установлена, synthetic187 не заменяют этот результат.

До следующего запуска сохранены exact `authorized-live-run-second-failure.json` SHA `027ae0f5fa9edc7cf16a83ffe25514decc6ef3f4889440bff233def55f16dc2e` и `authorized-live-evidence-second-failure.json` SHA `5f47dc03eaba236b016e77af70b6fe0c9c70d8a612b4b51dce3717f4f2a25131`. Исходный default live test source также сохранён build-excluded для истории.

По отдельному заданию chief добавлены safe `result.reasons` и explicit diagnostic mode в **test fixture**, production не меняется. Diagnostic использует тот же production parser/client с прозрачным REAL HttpsURLConnection delegate: все GET/timeouts/headers/redirect flags передаются платформе без замены TLS/trust/hostname/proxy. Трасса содержит только status, классифицированные MIME/encoding, length/count/close и exception class+phase (включая Error), без message/URL/body/IDs. Это не mock и не default constructor; evidence явно различает режимы. Приёмочный verified/expense/high критерий не ослаблен. Runner флаг `--transport-diagnostic` сам не исполнялся специалистом; после test-only rebuild root может выполнить ровно разрешённые два lookup. Повтор synthetic suite не назначается, пока production не изменился. API live готовность остаётся открытой.

Дополнительный точечный диагностический флаг по заданию chief: InputStream наблюдает первые три unsigned bytes в памяти и сохраняет только `bodyStartsUtf8Bom` boolean. Поток не потребляется дополнительно, не меняется и передаётся production parser как есть; первые символы/hex/raw bytes не выводятся. Наличие BOM — пока гипотеза автора, а не установленный факт. Флаг ещё не запускался на сети этим специалистом.

## Третий live diagnostic FAIL и новая TLS реализация

Root исполнил diagnostic real-delegate mode; специалист независимо прочитал safe JSON. Оба lookup завершились `javax.net.ssl.SSLHandshakeException` в фазе `getResponseCode`, disconnect=true. HTTP status/body не получены; наблюдения BOM нет. Это TLS failure до HTTP, а не BOM/денежный parser результат и не PASS API. Сохранены exact `authorized-live-run-third-failure.json` SHA `a3a8dbf6b566e6238bf6e05707ecbd63f507932bad9a65f44eec6cc95d5ecacf` и `authorized-live-evidence-third-failure.json` SHA `1d50cd564d2ac2023ef8f97ca9ea87c16395f7f8e810c1605031160614b8c211`.

После этого architecture/security отдельно исследовали missing-intermediate цепочку; их JDK/public-TLS evidence не присваивается этому специалисту. Прочитаны TLS contract SHA651e4b11fc276251340c2ceb7a834df933bd49adcadd5cf2719a8dd3a0a19793 и TLS security review. Они разрешают только добавление публичного intermediate как **недоверенного chain элемента** тому же DEFAULT validator, а LIVE01/P1 сохраняют до actual Android результата.

По готовности автора отдельно прочитаны полный OfdTls и его11 unit methods/native TLS target. Самостоятельно сверены source SHA: OfdTls `001d2ea24b0a46ef01fa6486faaf861262fce6ddbb659ce55370555a608c8d1e`, client `9688f0d21323c4edfb31b50f786ff8eb25ff9c78606278129f00141f6d4f95cb`. Декодированы только public embedded DER:1499bytes, SHA совпадает с контрактным `43cac31ef8e8ba1b4b16b8206e4c0a26c5badb2fc3aa09e90170e41b66c2fd64`. Default client constructor теперь использует OfdTls::open.

Независимая проверка source guards: original DEFAULT вызывается первым; repair только single leaf/известный host+handshake context, issuer/signature/leaf+aux validity, затем тот же extended Socket/Engine overload и authType. Two-arg server не repair; client calls/accepted issuers pass-through; per-connection factory, глобальные defaults/hostname/trust anchors не меняются. Свежий source не запускался этим специалистом. Author negatives с expired/untrusted/подменой/context/pin — spy/control-flow tests, их нельзя объявлять настоящими отрицательными PKIX handshakes.

В QA runner добавлен explicit `--public-tls`: после трёх synthetic suite отдельный OfdTlsRuntimeTest с opt-in, два public TLS-only handshakes к provider TCP, positive и wrong-peer negative, zero HTTP/QR. Без флага остаются только прежние mock-тесты; all-suite запрещён. Live runner/source не менялись: итоговый required live должен идти **без --transport-diagnostic**, через default production constructor с новым factory. До этих actual Android public/default-client результатов LIVE01/P1 остаётся открытым. Другой independent framework не добавлялся, так как source forwarding + существующие focused negatives + обязательные actual probes покрывают назначенный минимум без дублирования.

## Итоговые actual результаты после TLS fix

Финальная проверяемая версия: **0.4.0/code4**, main APK SHA256 `f54733b034399ec0b147d0fa7e9b5b82a8589034ca5c8692b63c905a2aa37cd3`, 68 393 973B; sourceFingerprint `f999b1c52ece385b981f9cd459b9d0d23e89e9d1bf4e56ac5371fbd7c5b423af`; test APK SHA256 `2c3f1516d57f625ef76f5fb6e924fd7977b2fc328be2924563534550686b97fd`. Ранее проверенные306… bytes и pre-TLS artifact результаты не выдаются за эту версию.

Root исполнил all4 Gradle tasks с BUILD SUCCESS43s и guarded install main/test. Специалист самостоятельно прочитал **именно final staged XML**:117 listed,0 failures/0 errors,1 прежний diagnostic skip, **116 executed pass**; OfdTlsTest11/11 pass. Эти compile/test команды выполнял root, не этот специалист.

Финальные `native-run.json` и три synthetic evidence JSON прочитаны отдельно: passed=true, **187 assertions pass** (75 independent PNG→client→registered job→SQLite,83 migrated controlled-offline QR,29 author replay). Они привязаны к f999…423af и test APK2c3…97fd. Отдельный `OfdTlsRuntimeTest` actual Android API30 прошёл: public TLS-only positive и expected wrong-peer reject, original accepted issuers/default hostname verifier сохранены. Это1 author-written runtime testcase, исполненный root и проверенный QA по transcript; не считать его11 spy unit methods настоящими expired/untrusted PKIX handshakes.

**Required DEFAULT client live теперь прошёл.** Root выполнил `run_authorized_live.py --run-own-receipts-4-5` **без diagnostic флага**. Самостоятельно проверены safe JSON: `nativeDefaultClient=true`, `realDelegatingTransport=false`, runner passed=true, privateInputVerified=true. Sample4 дал `4395.00 KZT`, sample5 `3547.00 KZT`; оба source date`2026-11-03`, kind`expense`, confidence`high`, reasons[], verifiedIdentityAndFinalSale=true. `noLedgerWrite=true` (сравнение ledger.db/WAL bytes), privateInputRemoved=true, cleanupAttempted=true. Это реальные два разрешённых Android production-client lookup, не mock и не data из исходной Python research. Сами HTTP/ADB команды исполнял root; специалист новые HTTP не запускал.

Специалист исполнил **одну final post-TLS** bounded artifact проверку с exact f547…37cd3/f999…423af: exit0, **129 assertions pass**, все106 source SHA совпали с final build manifest. Signature/apksigner verify exit0, same archived0.3 signing certificate `08f77eff74101bd48dc2cccb52403216620cc8e1bb3a86fd685c640c9a64ec27`. Binary manifest com.ladno.finance/code4/name0.4.0/min26/target36/INTERNET; DEX содержит OfdReceiptClient/OfdReceiptParser/OfdJson/**OfdTls** и фиксированный endpoint. Live instrumentation class и `.local` paths отсутствуют в main APK. Ограниченная exact-marker проверка двух разрешённых private QR в packaged DEX/UI даёт отсутствие; значения не печатались/не записывались в evidence. Это не полный privacy audit всех возможных данных. Final `artifact-identity.json` относится к f547… bytes; прежний123-pass относился к старой версии и остаётся только историей.

Три live FAIL сохранены с первоначальными файлами/SHA; ни один не переименован в успешный. Первый synthetic success также сохранён. Доказательства после fix позволяют главе оценить закрытие **LIVE01/P1 только для протестированных samples4/5 и текущего API30 adapter**. Окончательную приёмку специалист не выносит.

## Оставшиеся ограничения и передача

- Samples1/2 по текущим исходным фотографиям остаются unreadable; весь сценарий четырёх фискальных чеков не объявляется пройденным. POSsample3 относится к fiscal490, не пятый расход; developer samples не заносились в пользовательскую ledger.
- Фактические Activity/queueAction/бумажная камера UI/галерея permissions/physical phones остаются непроверенными. Registered internal jobs в synthetic тестах не заменяют эти пути.
- Freedom/Halyk/Kaspi real notification corpus и отсутствие outgoing OS events не исправлены этим API этапом. Подключён один consumer provider, не все ОФД/банки/OEM версии Android.
- Expired/untrusted/context/pin negative paths проверены focused unit spies и source review; actual Android negative — wrong peer. Revoked/OCSP/SCT или все trust-store/NSC конфигурации отдельно не испытаны; сохраняется baseline system validator, не выдуманная гарантия отзыва.

Следующее действие: отдельная проверка qa-lead и суд по финальному snapshot. Дополнительные HTTP/build/test повторы или изменения source этим специалистом не требуются.
