# APP-005 — адресная финальная проверка главы архитектуры v3

Дата: 2026-11-02. Проверяющий: architecture-lead. Решение: ограниченный head code/spec compatibility и build handoff pass, передать независимым QA/суду. Оба специалиста проверены отдельно; старые нормативные ARC закрыты ниже. Это не судебная приёмка и не доказательство Android runtime/OCR/NLS/device работоспособности.

## Задача, авторы и проверенная версия

- system-architect `/root/personal_mvp_system`: `docs/android/CONTRACT.md` v2 с адресными дополнениями, 102 строки, SHA-256 `0EFFD4E40379F3FEAB3B6000AB29EBCBD4A0E56FFE49540AFB0B3E93E9DB8E31`.
- security-engineer `/root/personal_mvp_security`: `docs/android/SECURITY-REVIEW.md` v4, 65 строк, SHA-256 `4C771B0AC1F635A0B7CA607AB32F3ED60CD46807BD682592F812FCD79D650D9A`.
- Смежный вход `docs/android/MONEY.md` v2, 66 строк, SHA-256 `ADD32DC37931AF0079236866B5248019AA59A3AE9C120B2E8030ED9A75C1CDCB`; финансовое утверждение принадлежит finance-lead. Исправленные kind/date/retention/identity формулировки перечитаны главой архитектуры.

## Выполнено и доказательства

- `Get-Content -LiteralPath` для CONTRACT/SECURITY/MONEY: ожидалось независимо прочитать оба результата и денежные зависимости; факт — прочитаны, усечённый общий вывод дополнен отдельным чтением конца CONTRACT и всего SECURITY.
- `Get-FileHash -Algorithm SHA256 -LiteralPath` для трёх файлов: факт — все заявленные выше хэши совпали.
- `rg -n 'snapshot|paymentReference|transient|cross-platform|raw|Чек и банковское'` по трём документам: факт — выявлены конкретные расхождения retention/sanitization, переданные владельцам.
- При повторном review выполнены Get-Content/Get-FileHash CONTRACT/MONEY v2 и целевой rg ARC5-01/04/revisionToken/Summary: факт — хэши совпали, исправления ниже подтверждены чтением. MONEY прочитан отдельно целиком после усечения общего вывода.
- Финальная адресная проверка: Get-Content SECURITY v4 целиком и CONTRACT v2 с notificationError/completed-paid дополнениями; Get-FileHash подтвердил все три текущих SHA выше. Нормативные условия и границы static/runtime доказательств согласованы.
- Лично просмотрены 2026-11-02 официальные [WebView native bridges](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges), [in-app content](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content), [WebSettings](https://developer.android.com/reference/android/webkit/WebSettings), [WebViewClient](https://developer.android.com/reference/android/webkit/WebViewClient), [Auto Backup](https://developer.android.com/identity/data/autobackup). Факт — all-frame bridge/origin ограничения, неполное покрытие interception/navigation, настройки доступа и отдельные transfer policies подтверждены.
- Android/iOS/web/OCR широкий повторный ресёрч не выполнялся: у APP-005 конкретная Android-граница; предыдущая частичная проверка APP-004 не становится проверкой нового кода.

## Отдельная проверка обоих специалистов и build handoff

- System: Java/wire signatures сохранены; nullable optional permissions.notificationError и callout в Settings/Imports согласованы с NativeRuntime.state/view.js; strict paymentReference:null ingress прочитан в safeCandidate. ReceiptParser теперь требует completed-paid marker, bare total сохраняет candidate/review; универсальная RU/EN/KZ/bank точность не заявлена. Это адресное сопоставление, не новый полный аудит parsers.
- Security: перечитан actual static review, затем независимо прочитаны source/merged Manifest, backup XML/NoBackupAgent, MainActivity/LocalAssets/AppBridge, SourceTokens и NotificationStatus+NLS callback. No INTERNET/READ_SMS/CAMERA в merged manifest; listener защищён BIND, FileProvider не exported, WebView debugging отключён. Debug APK остаётся android:debuggable=true, подписан development key, как указано в README.
- WebView/bridge: exact GET шесть assets, неизвестное → непустой403; CSP запрещает inline/external scripts/frames/connect; внешние URL/file/content/popups/SSL bypass отключены, native refresh фиксирован. Bridge имеет 64KiB UTF8/schema/action whitelist и envelope; native хранение/валидация остаются authoritative. Android WebView исполнение этих условий не выполнялось.
- SEC06 CLOSED STATIC подтверждён OcrEngine SHA `8351A1FD4939AFA31587D2D1B874E10DC5C8E6EAE25B6E9995E7CE52DFC81BCB`, AndroidX import/dependency1.4.2. SEC07 CLOSED STATIC: NLS SHA `EF1D6DA43E4F1BE253D6371055DCD23F9BCF5D638A99AFF3AE9EA80A50B76C76`, NotificationStatus SHA `BC3CEE982DE669D3FBBCDA31F17A6FB0CF7F7A4D00B085C0E628BC31A00BFACA`; один synchronized monitor защищает sticky overflow и success publication.
- Get-FileHash APK: фактический `38c9d4490419eda3685e7187281d5f1ecfb958a95d77c57f7f59633febcfbd5a` совпал с android-app/dist/build-result.json. Fingerprint build-result/build-source-manifest одинаков: `b5b8d938295f9489124bec82ad0c07c0aa86fcde8f0d1c5bd8cafc343db57b03`; PowerShell foreach/Get-FileHash всех61 manifest files дал0 mismatch с текущими исходниками.
- Прочитаны android-app/dist/gradle-build.log (BUILD SUCCESSFUL,52tasks executed), TEST-*.xml (6suites/24tests/0failures/0errors/0skipped) и lint-results-debug.txt (0errors/3warnings). Это фактическое выполнение root, лично подтверждённое чтением артефактов; глава сборку/тесты не перезапускал. Остались предупреждения ApplySharedPref/SetJavaScriptEnabled/StaticFieldLeak, не EXIF.
- README/build.gradle проверены:0.1.0/com.ladno.finance/min26/targetcompile36/Java17/debug sign, private separate databases, no bank-universal/device proof promise. Путь dist относителен android-app; фактический source inventory называется build-source-manifest.json. Первоначальные попытки чтения root dist/build-manifest.json дали not found и исправлены на эти реальные пути.

## Стабильные контракты для исполнения

- `AndroidApp.request(action,payloadJson):String` с `{ok,data,error}`, строгим whitelist/JSON schema и безопасными ошибками; native остаётся единственным владельцем валидации/денежных итогов.
- `StorageApi.state/execute/ingestCandidate`, `LedgerStore.get`, `ImportCandidate.toJson/fromJson`, `BankMessageParser.parse`, `ReceiptParser.parse`, `ContractException` согласованы по назначению. Уточнения sanitization ниже не требуют изменения сигнатур.
- `window.NativeBridge.request():Promise<data>`, `AppView.render(state,ui)`, `toast`, `PreviewAdapter.request` и фиксированный `NativeEvents.onRefresh()` согласованы; refresh перечитывает state и сохраняет dirty forms.
- Storage содержит реальные операции/категории/settings/sources/imports/tombstones, single-commit mutations/revision CAS; source capture не пишет БД напрямую. Native отвечает за NLS/OCR/пикер/камеру. WebView отображает эти данные; demo/preview не заменяют phone functions.
- Money передаётся строками, вычисляется exact BigInteger/BigDecimal, SQLite TEXT; каталог/0..6 scale/24 цифры входа и отказ лишней точности согласуются с MONEY. Агрегаты не обрезаются до лимита отдельного ввода; KZT не default.
- Необязательная категория не меняет сумму; review не входит в итоги. Уведомление key/postTime не bank ID, amount/time недостаточны для слияния; надёжный scoped reference и конфликтующие источники проходят правила CONTRACT/финансовое согласование.
- Hybrid направление архитектурно пригодно при фактической реализации native функций и persistence; наличие страницы в WebView само по себе этим критериям не соответствует. Java/SDK/OCR версии заданы root; их сборочная совместимость в этом review не испытана.

## Замечания и обязательные уточнения владельцам

- ARC5-01 / P2 / закрыто в CONTRACT v2: raw paymentReference → source.paymentRefToken → paymentReference:null → typed fromJson → ingest; storage запрещает nonnull raw и whole-blob persistence. Observation eventId/revisionToken обязательны; IDENTITY_UNCERTAIN сохраняет review и блокирует автопост. Это закрытие спецификации, не испытание кода.
- ARC5-02 / P2 / закрыто по совместимости MONEY v2: raw transient, persistent/JS только structured candidate/provenance; kind/date/revisionToken и trusted-reference/manual-review согласованы с CONTRACT. Финансовые эталоны самостоятельно не утверждаю.
- ARC5-03 / P2 / CLOSED NORM в SECURITY v4: OCR/notification text transient-only, review содержит structured candidate; ≤24ч только private temporary image. Actual cleanup paths прочитаны специалистом; device crash/TTL проверка не выполнена.
- ARC5-04 / P2 / закрыто в CONTRACT v2: DB-поля state читаются согласованным snapshot/общей сериализацией с execute/ingest. Фактическая атомарность проверяется на реализации.
- ARC5-05 / P2 / CLOSED NORM+STATIC в ограниченной области: SECURITY v4 отделяет transports, фактические XML/NoBackupAgent согласуются с API36 решением ниже. OEM/36.1 execution не проверено; универсальный запрет не доказан и не обещается.
- UI-01 / P2 / закрыто в CONTRACT v2: ui.tab=records/categories/imports/settings, native actions/DTO неизменны; фактический UI проверяется отдельно.
- APP-004 ARC-01 в APP-005 проектно учтён: raw key/tag/account/reference не durable, используются purpose/namespace-scoped HMAC/surrogate; фактическое отсутствие raw в БД/логах ещё не проверено.
- Исторический возврат CONTRACT через followup_task завершился agent thread limit reached; chief выполнил авторские исправления очередью. Итоговые результаты обоих реальных специалистов проверены; глава чужие документы/код не редактировал.

## Точечное решение native backup/camera gate

По [Auto Backup](https://developer.android.com/identity/data/autobackup#cross-platform-transfer) cross-platform XML/API36.1 требует platform=ios и настоящие bundleId/teamId/contentVersion. При текущем compile36 и отсутствии iOS-приложения фиктивные параметры/opt-in не добавлять; отсутствие opt-in само по себе не объявляется доказанным запретом всех переносов.
Для API36 согласованы allowBackup=false, all-domain fullBackupContent/cloud/D2D exclusions, NoBackupAgent с пустым onFullBackup без super/fullBackupFile и без записи key-value backup. [BackupAgent](https://developer.android.com/reference/android/app/backup/BackupAgent#onFullBackup(android.app.backup.FullBackupDataOutput)) даёт приложению выбор выводимых файлов. Source XML/agent и staged merged manifest лично прочитаны: restore override ограниченно потребляет bytes, не открывает destination. Это static defense, transport execution не проверен.
External ACTION_IMAGE_CAPTURE без собственного CAMERA совместим с контрактом; cameraGranted=false при undeclared permission честно отражает permission, не наличие/работоспособность внешней камеры. Отмена/отсутствие обработчика/grants остаются будущими проверками.

## Оставшиеся проверки и следующее действие

AND-SEC-01…05 имеют описанное static/build покрытие, Android execution остаётся открытым: реальное OCR/camera/URI, SQLite persistence после гибели процесса, NLS/permissions/burst, Keystore failures/cleanup/backup/OEM36.1. Keystore unavailable в actual code даёт безопасный error без fallback/raw и без гарантии сохранения пропущенного источника; queue overflow может терять события с явным sticky warning. Личные данные/устройство не читались и не менялись.
В рассмотренной области новых blocking P0/P1/code-spec противоречий не обнаружено; runtime gaps и три lint warnings сохранены как ограничения доказательств. Передать эту версию/APK независимому QA и суду; их решения не подменяются head pass. Готовность всех банков/качество OCR на пользовательских чеках и универсальный backup запрет не утверждаются.
