# APP-013: проверка архитектуры реализации v1

2026-11-04, Asia/Qyzylorda. Глава `/root/kaspi_compat_arch_lead`. Область: локальный QR subset, 0.3.0/code3; не вся APP-013 и не API-интеграция.

## Самостоятельная проверка главы

Прочитаны отчёт исполнителя v2, actual FiscalQrParser, ReceiptQrDecoder, ReceiptQrScanner, NativeRuntime, SourceTokens, manifest/build.gradle и diagnostic fixture. Исходники не менялись; новые тесты и Gradle главой не запускались. Команды: `Get-Content`, целевые `rg`, `Get-FileHash -Algorithm SHA256`.

- Parser: exact host/raw path, scheme/userinfo/port/fragment, четыре однократно decoded query-поля, duplicate rejection; positive canonical KZT2 без округления, строгие календарь/время. QR candidate всегда kind=null/uncertain с review reasons.
- Runtime: QR результат → только QR, без OCR; отсутствие decoded QR → прежний OCR/consensus. Ошибка не создаёт пустой candidate. No URL loader/INTERNET/network stub обнаружен в проверенной ветке; банковские экраны не читаются.
- Raw QR/locator не передаются в JS/SQLite/logs: native передаёт только canonical fields и purpose-separated HMAC. Ключ остаётся AndroidKeyStore; paymentRefToken/accountSurrogate null. Это receipt observation identity, не доказанный bank payment ID.
- Scanner: 20MiB/32M исходных pixels, sampling до 4096/8M decoded pixels, EXIF matrix, recycle Bitmap до QR buffers. Decoder ограничивает detected symbols до16 и payload до4096B; несколько distinct/mixed decoded QR отвергаются, identical payload сворачивается. Это статические наблюдения, не проверка памяти/EXIF на телефоне.

Кодовые SHA256: FiscalQrParser `81268416ab6a7530d6e3a045a5f58e74aee48150c2ee472ccf11accd7e3f775e`; ReceiptQrDecoder `ce6542817bf4df4e0a5cbf851b3a7650907f7889b11e2131c130e6a711fd046d`; ReceiptQrScanner `f031962ef9baf0c9420d93a1309d15fd59931a541c586fc69c4a9a8da6fd152f`; NativeRuntime `1dcf1979b90e81496f59e19ea4093d6ab1a2cea1004f96d69cf24b8b60c89d62`; build.gradle `add7af9c76218529890082c1510ae1ddd82273f32644f57f6adfc73c80a6aaba`.

## Доказательства и ограничения

**ARCH13I-01 / P2, известное ограничение:** decoder может не обнаружить повреждённый соседний QR. Fixture и assertion `readableQrNextToDamagedQrRequiresAnotherPhoto` сохранены с явным `@Ignore`; SHA теста `9d9d2413ae106b31ac1f87d7a796158bdd0738b2dbc362f2f4b15c8711bcc371`. Отказ этого diagnostic не исправлен и не считается pass; не обещать нахождение каждого визуально присутствующего QR. Читаемый QR по этой ветке всё равно требует ручного подтверждения.

Первоначальные FAIL (исполнитель:45tests/1failure; chief сообщил80tests/1failure) сохранены их авторами. Финальный общий Gradle результат этим отчётом не предвосхищается. Физические media/runtime/storage/dedup/UI проверки и независимые QA/суд координирует chief; статическое чтение не заменяет их.

## Проверка специалиста и решение

Реальный `/root/kaspi_compat_arch_lead/receipt_gate_security` выполнил отдельное чтение кода по полному профилю: [APP-013-IMPLEMENTATION-SECURITY.md](../../docs/android/APP-013-IMPLEMENTATION-SECURITY.md), SHA256 `9f0529a7e8b6d0d3c1b5e41a2499932dad237ad07e63b1048ec3e93e129548ad`. Глава прочитал его итоговый файл и проверил SHA; выводы совместимы, новых P0/P1 не найдено, IS13-01/P2 совпадает с ARCH13I-01. Статическая архитектурная проверка локального subset пройдена; передать независимому QA и суду, без объявления APK/всей задачи готовыми.

Внешний API остаётся `needs_information` (positive response schema/пример отсутствуют), отсутствие банковского OS notification не исправляется этой QR-веткой. Замороженный контракт2098..., первичные architecture/security отчёты и архив402 не менялись.
