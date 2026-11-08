# APP-005 — независимый сценарный QA: интерфейс и окончательный APK

Исполнитель: `/root/android_qa_scenario`, scenario-test-engineer. Подчинение: `/root/android_qa_lead`. Автором приложения не являюсь. Дата: 2026-11-02, Asia/Qyzylorda.

## Версия и среда

Проверены шесть текущих Android assets через настоящий IAB/CUA: `http://127.0.0.1:8768/`. SHA-256 каждого файла — `tested-ui-hashes.json`. При открытии приложения отображалось предупреждение: просмотр в браузере использует память вкладки, native-функции доступны только в Android. Это не запуск APK или Android WebView.

Прочитаны AGENTS.md, team/PROTOCOL.md, полный профиль scenario-test-engineer и skill computer-use. Все действия интерфейса выполнялись через `mcp__cua_repl`: создание IAB tab, документированные Playwright locators, свежие AX состояния после действий, read-only DOM geometry, screenshot. Другие браузеры и обходные средства не использовались. Созданная тестовая вкладка закрыта, временный viewport сброшен.

## Реально выполнено

17 групп сценариев прошли; ожидания и фактические AX состояния находятся в `ui-results.json`.

- SC01–02: новая пустая сессия, валюта не подставлена; явные KZT/часовой пояс/правило даты; пустые итоги и история.
- SC03: сумма `0.100` KZT отклонена; дата, заметка и исходная строка суммы сохранились; сообщение прямо запрещает округление.
- SC04–05: расход `0,10` без категории → `−0,10`; доход `0.30` → точный итог `0,20` KZT при двух операциях.
- SC06–08: создание категории, назначение существующему расходу, удаление используемой категории; операция осталась без категории, итог `0,20` не изменился.
- SC09–10: USD отдельно дал нули и пустую историю, октябрь отдельно дал нули; возврат к ноябрю/KZT сохранил записи в памяти вкладки.
- SC11–12: изменение расхода на `0.20` изменило существующую запись и итог на `0,10`; подтверждённое удаление расхода оставило доход `0,30`, расходы `0,00`.
- SC13: viewport 393×852; screenshot `mobile-preview.jpg`; document clientWidth=378 и scrollWidth=378 (15px занимает вертикальная полоса прокрутки), height=852. Горизонтального переполнения нет. Первый QA assertion ошибочно требовал clientWidth==393; ложный FAIL и исправление QA-ожидания сохранены в JSON, продукт не менялся.
- SC14–15: камера/изображение и системный доступ выключены в browser preview, фиктивных OCR/банковских данных нет; ограничения локального учёта и отсутствия экспорта видимы.
- SC16: browser console errors/warnings за выполненный проход — 0.
- SC17: reload возвращает пустую первоначальную настройку, согласованно с памятью browser preview. Это не тест native persistence.

Подтверждённых продуктовых P0/P1/P2 в выполненном сценарном проходе не обнаружено. Исправление SC13 относится исключительно к тестовому ожиданию и не является дефектом приложения.

## Реально выполненная проверка окончательного APK

Команда: `python -u android-app/qa/scenario/final_apk_qa.py`, exit0, **55 checks / 55 PASS**. Runner выполняет только чтение, не устанавливает и не запускает APK. Точные команды, ожидаемые/фактические значения и stdout находятся в `apk-results.json` и отдельных `signature.txt`, `badging.txt`, `permissions.txt`, `manifest.txt`, `zipalign-16kb.txt`.

- Окончательный APK: SHA-256 `38c9d4490419eda3685e7187281d5f1ecfb958a95d77c57f7f59633febcfbd5a`, 37404606 bytes. Source fingerprint `b5b8d938295f9489124bec82ad0c07c0aa86fcde8f0d1c5bd8cafc343db57b03` совпал с build-result. Все текущие исходные файлы из `build-source-manifest.json` совпали с записанными SHA.
- `apksigner verify --verbose --print-certs` успешно подтвердил одну подпись v2, RSA2048; сертификат локальной разработки CN=Ladno Development. Это debug APK (`debuggable=true`), не production release.
- `aapt2 dump badging`: `com.ladno.finance`, versionCode1/versionName0.1.0, minSDK26/targetSDK36, launcher MainActivity. Первый QA oracle искал `sdkVersion:'26'` вместо фактического `minSdkVersion:'26'`; initial FAIL сохранён в `apk-results-initial-harness.json`, исправлен только runner, продукт не менялся.
- Merged permissions: отсутствуют INTERNET/READ_SMS/RECEIVE_SMS/CAMERA/BIND_ACCESSIBILITY_SERVICE. Есть AndroidX signature-permission для непубличных dynamic receivers. Merged component whitelist совпал: MainActivity exported; NLS exported с BIND_NOTIFICATION_LISTENER_SERVICE; FileProvider и Startup provider не exported; AndroidX ProfileInstallReceiver exported с DUMP. allowBackup=false, usesCleartextTraffic=false, NoBackupAgent присутствует.
- Шесть упакованных UI assets совпали с текущими исходниками. Отдельные 6 SHA readback checks (`ui-continuity.json`) подтвердили совпадение с `tested-ui-hashes.json` первоначального CUA-прохода. Три eng/rus/kaz модели совпали с `ocr-models.lock.json`; schema.sql совпал с исходником; Dex и оригинальные license notices присутствуют.
- ABI: arm64-v8a, armeabi-v7a, x86, x86_64. `zipalign -c -P 16 -v 4` — exit0. Для восьми ELF64 libraries в arm64/x86_64 все PT_LOAD p_align не меньше16384. Это проверки упаковки/ELF, не запуск на 16KB устройстве.
- Отдельно прочитаны существующие финальные Gradle XML: 24 test methods, failures0/errors0; lint errors0/warnings3. Это выполненные root тесты; сценарный QA их повторно не запускал. Разбор XML сохранён в `final-build-reports-summary.json`.

Предварительный APK `944381774b6462b35915961584166c21ebe230aa62776d74dd8e29858c0b246d` итоговым не считался. После финальной проверки новых продуктовых P0/P1/P2 не обнаружено.

## Невыполненные проверки и следующее действие

Не выполнялись установка/запуск APK, Android SQLite/restart/CAS, вызовы JNI OCR/камера/галерея, доставка и отзыв доступа NLS/Keystore, реальное переполнение очереди, реальные банки, OEM backup. Разбор post/link/ignore реальных native-кандидатов и native refresh во время грязной формы этим browser-проходом не проверены. Поэтому A2–A4 полностью пройденными не объявляются.

Результат передан root и главе QA для проверки отчёта/матрицы A1–A6, затем суду. Устанавливаемость на конкретном телефоне и полнота A2–A4 требуют Android execution. Самостоятельной приёмки приложения нет.
