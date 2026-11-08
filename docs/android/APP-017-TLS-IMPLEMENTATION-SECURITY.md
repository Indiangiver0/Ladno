# APP-017 TLS implementation security review v1

2026-11-04. security-engineer → architecture-lead. Полные профиль, AGENTS.md и PROTOCOL.md прочитаны. Только source review относительно frozen TLS contract `651E4B11…` и security `F8DCB330…`; собственная приёмка не заявляется.

**Вывод:** в проверенных исходниках новых блокирующих нарушений TLS-контракта не найдено. Дополнение не создаёт нового trust anchor и не заменяет default hostname policy. **LIVE01/P1 остаётся открытым:** actual Android production path и независимые runtime результаты этим чтением не подтверждены.

## Проверенная версия и способ

- `OfdTls.java` SHA256 `001D2EA24B0A46EF01FA6486FAAF861262FCE6DDBB659CE55370555A608C8D1E`.
- `OfdReceiptClient.java` SHA256 `9688F0D21323C4EDFB31B50F786FF8EB25FF9C78606278129F00141F6D4F95CB`.
- `OfdTlsTest.java` SHA256 `943C71A33AB2ABF79C2508508AFD3C961A764517CFAF8A44F06852D3F3D4563C`; `OfdTlsRuntimeTest.java` SHA256 `D9625B7D2CC7EEEBC107361F087328E99BF2D1F27FA8E5B85C9E3E2C9AA43529`.
- Выполнены `Get-Content` с номерами строк, `rg` по TLS/default/hostname вызовам и `Get-FileHash`. Отдельный локальный Python base64 decode embedded asset дал 1499 bytes, SHA256 `43CAC31EF8E8BA1B4B16B8206E4C0A26C5BADB2FC3AA09E90170E41B66C2FD64`, совпадение с frozen contract=true. Сетевых/Gradle/device тестов этот специалист не запускал.

## Фактические guards кода

`OfdTls:36–47` допускает только HTTPS consumer.oofd.kz, порт default/443, без userinfo; фабрика назначается одной connection. Default HostnameVerifier не переопределяется. `OfdReceiptClient:25,34,118–122` использует фиксированный endpoint и production `OfdTls::open`; bridge не задаёт transport seam. Helper сам не ограничивает path, но реальный production caller строит единственный constant path.

`OfdTls:50–70`: отдельный SSLContext, DEFAULT TrustManagerFactory с `init((KeyStore)null)`, только исходный X509ExtendedTrustManager; неизвестная платформа — исключение. Нет custom KeyStore/anchors/user CA, глобального SSLContext/factory/verifier setter или TrustAll. `getAcceptedIssuers:156` возвращает исходный результат без добавления intermediate. Public base64 — сертификат CA, не private key.

`OfdTls:85–100` проверяет exact DER SHA256, единственный разобранный сертификат без trailing data, CA basicConstraints и keyCertSign. Неверный asset превращается в отсутствие repair, не в доверие. Срок проверяется при использовании; просроченный неиспользованный auxiliary не ломает успешную original validation.

`OfdTls:103–115`: repair только для одного nonnull leaf, совпавшего issuer, current validity CA и leaf и успешного `leaf.verify(intermediate.publicKey)`. Возвращается новый массив `[originalLeaf, intermediate]`, исходный массив/leaf не заменяются. Эти предварительные guards не являются самостоятельным PKIX success.

`OfdTls:121–142`: original chain/authType/context сначала проходят исходный delegate. Только CertificateException допускает попытку guard; известный handshake peer consumer.oofd.kz:443 обязателен. Повторная проверка использует **тот же delegate, тот же Socket либо SSLEngine и authType**. Guard failure возвращает исходную ошибку; второй delegate failure не перехватывается как success. Отсутствующий контекст или другой peer исключают repair.

`OfdTls:146–156`: двухаргументный server overload и все client overloads — прямое делегирование без repair. В изменённых файлах нет настройки hostname/SNI/SSLParameters, algorithm constraints, времени или system roots; host-aware policy остаётся на переданном platform context.

`OfdReceiptClient:48–59,67–109`: TLS exception даёт структурированный fallback; текст исключения не логируется/не передаётся JS. Redirects выключены, запрос GET, WebView credentials не копируются, deadlines/body limits сохранены. Helper не работает с raw финансовым содержимым. Main manifest содержит согласованный native INTERNET и cleartext=false, NSC trust anchors не назначены.

## Замечания и пределы доказательств

**TLSIMPL-01 / P1 / прежний LIVE01 открыт.** Owner сообщил 29 JVM PASS (18 прежних +11 control-flow), но это не результат данного специалиста и не actual Android. Здесь не запускались ни полный build, ни opt-in public TLS runtime, ни receipt HTTP. Закрытие LIVE01 требует независимого фактического результата целевого runtime/обычного production client; ожидать его внутри этого review не требуется.

**TLSIMPL-02 / P2 / тесты control-flow не являются crypto negatives.** `OfdTlsTest:75–107,110–167` действительно проверяет forwarded arguments, отсутствие repair при неизвестном context/issuer/asset, propagation второй ошибки, client/twoarg passthrough. Однако `Recording:191–210` и `Leaf:240–259` — spies со смоделированными `verify/checkValidity`; строки “untrusted root/expired/hostname” — injected exceptions. Это корректно подписано тестом, но нельзя выдавать за actual PKIX rejection реальных сертификатов. Нужные crypto/runtime negatives остаются независимому QA.

**TLSIMPL-03 / P2 / runtime coverage точно ограничена.** Прочитан `OfdTlsRuntimeTest:24–50`: explicit opt-in, synthetic emulator guard, public TLS-only positive и wrong logical peer/SNI, без HTTP/ledger. Wrong-peer может отказать до repair из-за provider guard или исходного PKIX; один такой PASS не доказывает отдельную completed-chain hostname-negative ветку. Сам код hostname verifier не ослабляет. Engine path пока проверяется forwarding spy, не реальным Engine handshake; revocation/OCSP/SCT результат также не заявляется.

Следующее действие: глава проверяет эту точную версию и передаёт QA/суду вместе с actual runtime доказательствами. Исходники, тесты, frozen документы, private proofs и trust store не менялись; создан только этот отчёт. Новых public/financial HTTP, банковских действий или чтения private чеков не было.
