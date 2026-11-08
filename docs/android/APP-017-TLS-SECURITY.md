# APP-017 TLS security review v1

2026-11-04. security-engineer → architecture-lead. Проверен конкретный TLS contract v1 SHA256 `651E4B11FC276251340C2CEB7A834DF933BD49ADCADD5CF2719A8DD3A0A19793`. Полные профиль security-engineer, AGENTS.md, PROTOCOL.md и актуальная APP-017 прочитаны. Владение — только этот новый отчёт; самостоятельная приёмка не заявляется.

**Решение:** контракт допустим для реализации узкого auxiliary-chain helper. Дополнительного блокера к такому исправлению не найдено: intermediate остаётся недоверенным входом того же DEFAULT validator, новый trust anchor не создаётся. **LIVE01/P1 открыт** до проверки готового клиента на целевом Android; этот review не объявляет TLS/API/APK исправленными.

## Проверенные факты

- `Get-Content`/`Get-FileHash` использованы для контракта, public certificate metadata/DER и TLS-only probe. Private чековые ответы/QR в этой подзадаче не читались; финансовые HTTP не выполнялись.
- Local DER SHA256 intermediate совпал: `43CAC31EF8E8BA1B4B16B8206E4C0A26C5BADB2FC3AA09E90170E41B66C2FD64`. Архитектор независимо получил те же 1499 bytes через HTTPS из официального каталога GoGetSSL; источник и exact bytes зафиксированы контрактом. Это версия asset, не новый источник доверия.
- Независимо выполнен PowerShell/.NET X509Chain.Build: исходный leaf + intermediate в **ExtraStore**, системные roots не менялись, certificate downloads отключены. Факт: `true`, status=[]; путь leaf→GoGetSSL RSA DV CA→USERTrust RSA. В этом локальном тесте явно `RevocationMode=NoCheck`: он проверяет подписи/время/путь, не отзыв и не Android.
- Проверены CA=true, pathLen0, keyCertSign, subject GoGetSSL RSA DV CA, issuer USERTrust RSA; validity UTC `2018-09-06T00:00:00Z`–`2028-09-05T23:59:59Z`. Полученный root SHA256 `E793C9B02FD8AA13E21C31228ACCB08119643B749C898964B1746D46C3D4CBD2` взят штатным Windows builder, не импортирован.
- Public leaf SHA256 `6FE957F4C1E702065AB6A8772FF0A0CC954B0F542DAA43A2172096FED4138584`. Прочитан исследовательский extended probe: запись presented chain происходит перед original delegate, исключение не превращается в success. Его окончательный запуск/exit0 выполнен архитектором, не повторялся этим специалистом.
- Evidence архитектора: server presented chain length1; JDK21 original DEFAULT Socket-context reject; после добавления known intermediate тот же delegate/context accepted TLS1.2. Правильный negative peer+SNI `wrong.invalid` дал hostname CertificateException и handshake failure. Это доказывает недостаточность переданной цепочки на исследованном JDK пути; один PKIX exception без такого сравнения доказательством не являлся.

## Граница допустимого helper

Только fixed `consumer.oofd.kz:443` и отдельный OfdReceiptClient SSLContext/factory. DEFAULT TrustManagerFactory `init(null)`, тот же default trust/NSC, default HttpsURLConnection HostnameVerifier; не менять глобальные defaults, anchors, SNI, endpoint parameters, authType или исходный chain array.

Original check вызывается первым. Единственный repair — chain.length1, exact auxiliary hash/CA/keyCertSign/validity, issuer match и проверенная подпись leaf данным CA, известный provider/context. Новый `[originalLeaf, intermediate]` передаётся **тому же DEFAULT extended overload с тем же Socket/SSLEngine**; только его успех разрешает продолжение. Нет поддержки контекста/подписи/asset или повторный default reject — TLS error без alternative request.

Клиентские overloads/getAcceptedIssuers делегируются без расширения. Двухаргументная server-проверка не используется для repair. Не допускаются TrustAll, импорт intermediate как root, custom trust-store, permissive hostname verifier, NSC user-CA override, reflection downgrade или runtime AIA download. Certificate pin/hash, CA signature precheck и Windows success по отдельности не заменяют PKIX.

## Замечания и обязательные проверки

**TLSSEC-01 / P1 / LIVE01 остаётся открытым.** JDK helper proof не является результатом Android production client. До закрытия нужны actual Android DEFAULT delegate/context/host policy, public TLS-only positive, затем согласованный normal provider path; текущий известный SSLHandshake до HTTP не объявляется устранённым.

**TLSSEC-02 / P2 / extended context обязателен.** Oracle X509ExtendedTrustManager связывает Socket/Engine overloads с endpoint/algorithm constraints. AOSP RootTrustManager и Conscrypt TrustManagerImpl наследуют extended interface; RootTrustManager выбирает per-domain policy по hostname. Это primary support дизайна, а не гарантия фактического OEM runtime. Неextended delegate/отсутствующий handshake context → fail closed. В контракте требование есть; код helper ещё не проверялся.

**TLSSEC-03 / P2 / negative evidence ещё требуется на реализации.** Wrong host/SNI должен fail после completed validation; expired leaf/aux, untrusted/self-signed или missing system root, подмена auxiliary bytes, issuer mismatch/forged signature, malformed/multiple chain/context absence тоже fail. Полная корректная server chain должна пройти original path без repair. Tests должны проверить сохранение фактических Socket/Engine аргументов, default accepted issuers и отсутствие глобальных изменений. JDK live проверил Socket и wrong-host; остальное пока не заявляется пройденным.

**TLSSEC-04 / P2 / не обещать несуществующую проверку отзыва.** Сохранять ту же platform policy и handshake context, включая доступные OCSP/SCT сведения; не выключать baseline revocation/algorithm constraints ради успеха. Схема не гарантирует отклонение каждого отозванного сертификата, если default platform не проверяет соответствующий статус. Независимого revoked/OCSP/SCT результата сейчас нет; локальный Windows NoCheck не может его заменить.

Первичные основания: [Oracle ExtendedTrustManager](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/X509ExtendedTrustManager.html), [Android HttpsURLConnection](https://developer.android.com/reference/javax/net/ssl/HttpsURLConnection), [AOSP RootTrustManager](https://android.googlesource.com/platform/frameworks/base/%2B/0f5d386/core/java/android/security/net/config/RootTrustManager.java), [AOSP Conscrypt](https://android.googlesource.com/platform/external/conscrypt/%2B/f441db2e8612448c1f27530b706bf3e7d48a940c/repackaged/common/src/main/java/com/android/org/conscrypt/TrustManagerImpl.java). Проверены primary API/source; более широкого поиска/интеграций не было.

Не выполнялись новые receipt HTTP, банковские действия/ADB, product build/Gradle/Android tests, изменение исходников/trust store/старых отчётов. Попытка локального Python certificate-check не запустилась из-за отсутствия cryptography; вместо неё выполнен описанный .NET path-check с явным ограничением. Следующее действие: глава проверяет этот контрактный review, backend реализует helper, независимый QA проверяет positive/negative и LIVE01 по точной версии.
