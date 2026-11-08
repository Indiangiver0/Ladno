# APP-006 — независимые Android scenario tests: готовность к запуску

Исторический план авторского этапа. После фактических запусков результаты и setup finding фиксируются в DIAGNOSIS.md и runtime evidence; формулировки «ещё не выполнено» ниже относятся к моменту первоначальной передачи исходников.

Исполнитель `/root/android_qa_scenario`, scenario-test-engineer; глава `/root/android_qa_lead`. Владение только тремя назначенными androidTest Java и этим новым evidence-каталогом. Production, old QA и frozen108 не изменялись. Исходный судебный insufficient_evidence не переписывается.

## Что подготовлено, но ещё НЕ выполнено

- NativeRuntimeFixtures.java: настоящие production JSON payloads, baseline state, приватные synthetic evidence files, ограниченный polling, read-only Android SQLite readback. Mock/reflection/подмены permission или NLS callback нет.
- NotificationRuntimeTest.java: три независимых instrumentation метода с различными реальными grant-условиями. Реальный AndroidKeyStore HMAC, production parser/sanitizer/storage, native exact replay/edit/tombstone/gap/raw-marker checks; actual system notifications для selected/unselected, дохода/расхода/повторного обновления/отрицательной операции; отдельный revoke/no capture.
- OcrRuntimeTest.java: один последовательный метод direct Tesseract JNI на шести generated private PNG EN/RU/KZ (paid/unpaid), затем release, затем production NativeRuntime image→OCR→sanitizer→HMAC→SQLite, same-byte replay и invalid-image cleanup/no-entry. Признаки оплаты и суммы заданы независимыми synthetic inputs. Posted запись обязана совпасть с amount/currency/date; unpaid обязана остаться review. Paid с низкой confidence/неоднозначностью не подменяется на posted: фактический статус и причины сохраняются.

Классы ограничены @SdkSuppress(minSdkVersion=30): согласованная тестовая среда AOSP API30, не заявление о проверке Android8/всех устройств. Успешное создание/компиляция исходников не является выполнением тестов.

## Root orchestration: только после AVD guard

Root проверяет `emulator-5580`, `Ladno_QA_API30`, package com.ladno.finance, API/ABI. Личные телефоны не выбирать. Build/testAPK/install/force-stop/clear и выдачу/отзыв системного доступа делает root своим разрешённым runtime tool. Никаких UI input/UIAutomator/Espresso/ActivityScenario, BIOS/driver/host security изменений нет.

К каждому guarded `am instrument -w -r` добавить `-e class <method>` и runner `com.ladno.finance.test/androidx.test.runner.AndroidJUnitRunner`:

1. `com.ladno.finance.NotificationRuntimeTest#keystoreParserStorage`: N2a; grant не нужен, собственные synthetic источники выбираются production storage API. Последующий NLS replay может пометить identity gap; это предусмотренная безопасная неопределённость.
2. Выдать настоящий OS NLS grant только тестовому компоненту com.ladno.finance/.BankNotificationService. Затем `com.ladno.finance.NotificationRuntimeTest#osGrantedDelivery`: N2b. Проверяется runtime.notificationAccess(), без подмены. Source com.ladno.finance выбран напрямую через LedgerStore как явно разрешённый chief synthetic fixture; native UI этот package исключает. Проверка не доказывает UI выбора собственного package или совместимость реального банка.

   После двух сохранённых initial/diagnostic FAIL chief согласовал test-only readiness setup: public NotificationManager посылает нефинансовый ping не чаще1Hz до60s. Нужен настоящий новый needs_review commit, иначе READY_TIMEOUT FAIL. После cancel и сериализованного deselect источника наблюдается quiet window, игнорируются только новые probe IDs (baseline ID difference + source/date/null kind/null amount/null currency), подтверждается отсутствие финансовой записи. Затем выполняются прежние unselected/финансовые проверки без изменения ожиданий. Реальный OS grant не приравнивается к готовности listener; callback вручную не вызывается.
3. Отозвать настоящий OS grant, дождаться отключения. Затем `com.ladno.finance.NotificationRuntimeTest#osRevokedNoCapture`: реальный notificationAccess=false и отсутствие commit в ограниченном 3s окне после публикации.
4. Оставить NLS grant отозванным; force-stop target package для нового instrumentation процесса; не запускать Activity. `com.ladno.finance.OcrRuntimeTest#realTesseractAndNativeMediaPipeline`: direct OcrEngine запускается ДО NativeRuntime.get и release перед singleton OCR. Это исключает параллельную запись моделей .copy.

Не запускать все NotificationRuntimeTest методы одной командой без изменения реального OS grant между ними: условия намеренно разные. При необходимости повторного запуска учитывать baseline: тесты не предполагают пустую БД, уникальные notification amounts и tags предотвращают смешение с предыдущими данными; фиксированные PNG сохраняют receipt byte identity для дедупликации.

## Evidence и ограничения

В app private `files/qa-android-scenario/` создаются notification-native.json, notification-os-granted.json, notification-os-revoked.json, ocr-runtime.json с API/ABI/PID, expected/actual каждого assertion, actual synthetic OCR text/confidence/candidate/outcome/reasons. Root извлекает только эти файлы guarded run-as/pull в новый evidence-каталог. Private synthetic PNG лежат cache/qa-receipt-fixtures/. Прогресс передаётся через instrumentation status2 (in progress), не имитирует завершение JUnit.

NotificationManager.notify при повторе меняет system postTime, поэтому N2b проверяет новый revision→review без второго расхода; exact stable revision отдельно проверяется N2a. Negative absence ограничено наблюдаемым 3s окном, не универсальное обещание отсутствия будущей доставки после нового grant.

processMedia получает test-only unregistered jobId: image cleanup и actual SQLite observation подтверждают data path; штатный Activity job lifecycle, внешняя камера, галерея, URI chooser/grants, WebView и клавиатура этим не проверяются. Не выдавать эти API-тесты за camera/gallery UI или реальный corpus всех банков/касс. Оценка реальных фотографий и OEM/других API остаётся отдельной.

Следующее действие: root собирает test APK и исполняет четыре guarded instrumentation команды; независимый специалист затем читает реальные результаты и оформляет findings/report, глава проверяет отдельно. Пока compile/run PASS не заявлен.
