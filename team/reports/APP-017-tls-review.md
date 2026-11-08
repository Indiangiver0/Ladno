# APP-017 TLS: проверка главы v1

2026-11-04, Asia/Qyzylorda. `/root/kaspi_compat_arch_lead`, architecture-lead. **Исходники допустимы к независимой проверке QA; LIVE01/P1 остаётся открытым.** Успех готового Android production client этим чтением не установлен. Старые успешные synthetic tests не отменяют сохранённый actual SSLHandshake failure до HTTP.

## Реальные исполнители и версия

- System-architect `/root/kaspi_compat_arch_lead/receipt_gate_architect`: [TLS contract](../../docs/android/APP-017-TLS-CONTRACT.md), SHA256 `651E4B11FC276251340C2CEB7A834DF933BD49ADCADD5CF2719A8DD3A0A19793`. Проверил primary GoGetSSL bytes и собственным public TLS-only JDK probe — один leaf в server chain, original DEFAULT reject, completed chain accepted тем же delegate/context; negative peer/SNI отвергнут. Engine/Android/receipt HTTP автором не проверялись.
- Security-engineer `/root/kaspi_compat_arch_lead/receipt_gate_security`: [contract security](../../docs/android/APP-017-TLS-SECURITY.md), SHA `F8DCB330BEB1FA7207D77334F4E8F3DBE68BA2D7D49A5000B5C8CBE6023E5A33`, затем реально возобновлён для отдельного чтения готового кода: [implementation security](../../docs/android/APP-017-TLS-IMPLEMENTATION-SECURITY.md), SHA `120D871E7EE8DC4F4AFEFF85870912EB1D19394666AA393C2750E1A5952879CB`. Новых нарушений, блокирующих передачу кода QA, не выявил; runtime/LIVE01 не объявлен исправленным.
- Проверенные исходники: `OfdTls.java` SHA `001D2EA24B0A46EF01FA6486FAAF861262FCE6DDBB659CE55370555A608C8D1E`; `OfdReceiptClient.java` SHA `9688F0D21323C4EDFB31B50F786FF8EB25FF9C78606278129F00141F6D4F95CB`. Авторы приложения и глава не занимают судебные роли по этой версии.

## Самостоятельная проверка главы

`Get-Content`, целевые `rg`, `Get-FileHash`: глава прочитал оба контрактных результата, новый source security, production helper/client и соответствующие JVM/runtime tests. Код, сертификаты, системные trust stores и прежние отчёты главой не менялись; новых handshakes/HTTP/Gradle/Android/ledger действий не выполнялось.

Локальный Python извлёк embedded DER из production source: **1499 bytes**, SHA256 **`43CAC31EF8E8BA1B4B16B8206E4C0A26C5BADB2FC3AA09E90170E41B66C2FD64`**, equality с root-validated public DER=true и published primary SHA=true. Первичный HTTPS источник проверен архитектором: [каталог GoGetSSL](https://www.gogetssl.com/wiki/intermediate-certificates/gogetssl-intermediate-root-certificates/) → Legacy RSA DV CA. Сертификат действителен до `2028-09-05T23:59:59Z`; это public intermediate, не private key, trust anchor или leaf pin.

По production code подтверждено:

- DEFAULT TrustManagerFactory `init((KeyStore)null)`, требуется исходный X509ExtendedTrustManager; неизвестная платформа завершается ошибкой. Собственный KeyStore/новые anchors/user CA/NSC override не создаются.
- Сначала original delegate получает original chain/authType/Socket либо Engine. Только CertificateException допускает одну guarded попытку: ровно один nonnull leaf, известный peer consumer.oofd.kz:443, exact auxiliary hash/CA/keyCertSign, действующие сроки, issuer match и проверенная подпись leaf.
- Создаётся новый `[originalLeaf, intermediate]`; тот же DEFAULT extended overload с тем же Socket/Engine/authType обязан принять дополненную цепочку. Повторный отказ не превращается в success. Исходный chain не меняется; client/two-argument server вызовы и accepted issuers делегируются без repair/расширения.
- Фабрика назначается только отдельной HTTPS connection; global SSLContext/factory/verifier не меняются. Default HttpsURLConnection HostnameVerifier, SNI/SSLParameters/algorithm constraints не переопределяются. Helper ограничивает host/443; единственный receipt path задаёт сам фиксированный OfdReceiptClient.
- Redirects, time/body limits, no-cookie/no-raw-log границы клиента сохранены. TLS exception возвращает структурированный review fallback без текста URL-bearing exception. Автоматической загрузки AIA/новых CA или HTTP downgrade нет.

## Замечания и фактические пределы

**TLSHEAD-01 / P1 = LIVE01: открыт.** Контракт и код поддерживают безопасную цепочку проверки, но actual Android default delegate/host context и успешный обычный provider client должны быть подтверждены root/QA. До этого не объявлять API/APK работающим на устройстве и не заменять прежний FAIL новым synthetic PASS.

**TLSHEAD-02 / P2: охват тестов разделён.** Owner сообщил 29 JVM PASS (18 прежних +11 TLS control-flow). Глава прочитал tests: Recording/Leaf являются spies, поэтому смоделированные root/expiry/signature/hostname exceptions доказывают forwarding/guard/error propagation, а не реальные PKIX negatives. Этот запуск не выполнялся главой и не заменяет независимый QA.

**TLSHEAD-03 / P2: negative runtime ограничен.** Opt-in OfdTlsRuntimeTest содержит synthetic-emulator guard, public TLS positive и wrong logical peer/SNI без HTTP. Wrong peer может отвергаться provider guard до дополнения; отдельная completed-chain hostname-negative ветка этим одним PASS не доказывается. Engine path пока проверяется forwarding test; revoked/OCSP/SCT результата нет. Baseline platform policy сохраняется, дополнительная гарантия отзыва не обещается.

Предпочтительное внешнее исправление — полная цепочка на сервере. Дополнение здесь лишь подаёт известный intermediate как недоверенный chain element тому же системному validator; оно не разрешает неизвестный root/истёкший сертификат/чужой hostname. После смены issuer или истечения auxiliary неизвестная цепочка должна остаться ошибкой, без silent replacement.

## Передача

Root/независимый QA фиксируют точную сборку, public TLS-only positive/negative и разрешённый actual production path, затем суд оценивает ту же версию. Результаты текущего общего build и будущих Android запросов в этот отчёт заранее не включаются; после фактического evidence допустимо отдельное дополнение с путями/числами и статусом LIVE01. Финансовые parser/identity guards и ограничения фото1/2 остаются прежними; эта TLS-подзадача их не переписывает.

## Дополнение v2: фактический Android transport результат

2026-11-04. Глава независимо прочитал сохранённые [native-run.json](../../android-app/qa/app017/native-run.json), [authorized-live-run.json](../../android-app/qa/app017/authorized-live-run.json) и [authorized-live-evidence.json](../../android-app/qa/app017/authorized-live-evidence.json), сопоставил их с кодом opt-in теста и повторно вычислил SHA APK и всех **106** файлов build manifest: расхождений0, fingerprint `f999b1c52ece385b981f9cd459b9d0d23e89e9d1bf4e56ac5371fbd7c5b423af`. Main **0.4.0/code4**, **68 393 973 bytes**, SHA `f54733b034399ec0b147d0fa7e9b5b82a8589034ca5c8692b63c905a2aa37cd3`; test APK SHA `2c3f1516d57f625ef76f5fb6e924fd7977b2fc328be2924563534550686b97fd` совпал с staged APK и обоими аудитами. Прежние OfdTls/Client001D…/9688… не изменены. Сохранённый native run:187 synthetic checks (75+83+29), failures0; public TLS positive/wrong-peer run passed=true; команды обоих аудитов exit0. Новые тесты/HTTP/установку глава не запускал.

Разрешённый actual запуск на **Ladno_QA_API30** использовал обычный `new OfdReceiptClient()` (`nativeDefaultClient=true`, diagnostic transport=false), не fake connection. Оба ответа4/5 дали **4395.00/3547.00 KZT**, **2026-11-03**, `expense/high`, reasons0, verifiedIdentityAndFinalSale=true, passed=true. `noLedgerWrite=true`, privateInputRemoved=true, UI не упражнялся. SHA безопасных evidence: live-run `f8a48acbd4e5d188b9de73ef33aad9a8d2a2b771831146d172782934bf07d632`, live-evidence `90b81721ea4332f7636c24fe08ded835c8d971ac14972951335676482b621611`, native-run `780f71ba75c056180bc174df10608305be3d680e51bf208ba5f180c2f5980a19`. Секретные входные реквизиты и raw response сюда не копировались.

**LIVE01/P1 закрыт в узком scope Android API30/default OFD transport для образцов4/5 на этих байтах.** Ранее описанный SSLHandshake/неполная server chain и первые FAIL остаются историческими доказательствами; это дополнение фиксирует последующий успешный runtime, не удаляя их. Закрытие не означает проверку физического телефона, камеры/UI, фото1/2, всех четырёх фискальных чеков, всех ОФД либо полного набора crypto/Engine/revocation negatives. Остальные P2 и дальнейшая независимая QA/судебная приёмка сохраняются; actual lookup не записывал расходы в ledger.
