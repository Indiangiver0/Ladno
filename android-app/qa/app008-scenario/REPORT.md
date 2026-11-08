# APP-008 — финальный независимый сценарный QA

2026-11-03, Asia/Qyzylorda. Исполнитель `/root/improve_qa_scenario`, scenario-test-engineer; глава `/root/improve_qa_lead`. Не автор продукта. Прочитаны AGENTS/PROTOCOL, полный профиль, план QA, computer-use skill и фактические свидетельства. Автор native test fixtures — этот QA; Android-команды исполнил root, их результаты независимо проверены мной.

## Вывод по проверенной версии

Финальный APK **f73e04a0202418ee1dc59c1e669a57228988607de9fb1f60bd388b8e4a20beac**, 68092906 bytes, package com.ladno.finance, versionName0.2.0/versionCode2. Source fingerprint **d20ab8f54d5aede6bf35af8b1b1b01caacd5bc890bece57d303023514b444fe8**.

В выполненном ограниченном объёме открытых продуктовых P0/P1/P2 не осталось. **QA008-OCR01 закрыт для исходного неизменённого контрпримера:** корректные поля BEST предзаполняются, конфликт с FAST отправляет чек на проверку, неверного авторасхода794.32 нет. Это не гарантия точности любого чека или отсутствия совместных ошибок двух моделей. Решение главы и суда остаётся отдельным этапом.

Результаты: **20 логических групп browser/CUA**, **71/71 APK-проверок**, **71/71 actual Android native assertions**, затем **389/389 моих audit checks** по факту Android-команд, типам expected/actual, пяти независимым денежным ожиданиям, хешам и provenance. Сборку и native исполнение выполнил root. 42 root JVM test methods/0failures/0errors подтверждены чтением XML последней staged-сборки.

## Browser/CUA — выполнено мной

Полные AX состояния, DOM-поля и геометрия: `ui-results.json`; сводка `ui-summary.json`; хеши шести проверенных assets `tested-ui-hashes.json`. Все UI действия только `mcp__cua_repl`. Read-only evaluate использовался для полей форм и геометрии. Исходники UI с тех пор не менялись: все шесть хешей совпали с финальным APK, поэтому повтор того же полного CUA-прохода не выполнялся.

- SC01–03: явная настройка KZT; 11 стартовых категорий расходов и4 доходов; отдельная кнопка/экран графиков, нулевое состояние.
- SC04–05: категория создаётся в незавершённом расходе без суммы/даты; заметка сохраняется, новая категория выбрана. Преждевременного проведения нет; последующее заполнение создаёт0.10KZT.
- SC06–09:0.10+0.20 дают ровно0.30 расходов, доли33.33%/66.67%, «Без категории» включена; доход0.90 исключён. USD и октябрь изолированы.393×852: clientWidth=scrollWidth, горизонтального переполнения нет. `charts-mobile.png` — снимок browser preview, не Android.
- SC10–14: отдельный явно синтетический harness `fixture.html`/`fixture-adapter.js` использует неизменённые production assets. Ошибка сохранения категории сохраняет review amount7.25/date2026-11-04/note/decisionpost/confirmDistinct/имя категории. Повторное создание выбирает категорию и сохраняет весь ввод; проведение даёт существующие5.00+новые7.25=12.25.
- SC15–18: видимы разные результаты review/posted/duplicate/cancel/error и ограничение QR. Отмена «Убрать» сохраняет3 pending; три подтверждённых reviewCandidate/ignore дают0pending при единственном исходном расходе5.00 без изменения суммы. `dismiss-mobile.png` явно подписан синтетическим QA.
- SC19–20: переход от posted outcome из октябрь/USD открывает ноябрь/KZT и нужную запись5.00. Console warn/error обеих вкладок0.

20 групп представлены23 raw наблюдениями:22успешных и один исправленный false тестового oracle SC08, который искал неизменённый текст в diff AX. Full AX повтор подтвердил нулевой октябрь; raw false сохранён. Неудачный первый localhost connect исправлен запуском root loopback server с разрешениями; это setup failure. Временные QA-вкладки закрыты, viewport сброшен, собственный fixture server остановлен.

## Финальный APK — выполнено мной

Команда `python -u android-app/qa/app008-scenario/final_apk_qa.py`, exit0, **71/71PASS**. Ожидания/факт/команды в `apk-results.json`, отдельные txt содержат stdout. Проверены hash/размер/build/source manifest, версия2, подписьv2 и тот же certificateSHA256, что у точного архивного0.1.0APK; шесть assets совпадают с CUA-tested SHA; прежние permissions/components, SQLite schema, FAST3модели, BEST3модели с точными hashes/bytes и лицензией по pinned lock, ZIP16KB/ELF64alignment.

Первое ожидание sourceFingerprint содержало лишнее `1b`; оно дало один oracleFAIL, сохранённый в `apk-results-fingerprint-oracle-typo.json`. Исправлена только опечатка QA, затем71/71PASS. Runner теперь возвращает nonzero при любом failed check. Проверка APK не является запуском Android UI.

## Actual native OCR — root исполнил, я проверил

Root: отдельный **Ladno_QA_API30/emulator-5580, Android11/API30/x86_64**; main APK f73e04..., test APK **bd7030dfef1ce979528e2936aa6a3b2fc90c405e5c6a3eec166503cd5bc109e3**. После установки выполнен clear только этой синтетической среды. Команда instrumentation запускает `com.ladno.finance.ReceiptLayoutRuntimeTest#syntheticMultilineAndFiscalImages`. Actual stdout: **OK(1test),18.374s**. Все5исходных/дополнительных изображений и оба direct/native этапа выполнены,71assertionsPASS.

- Неизменённый исходный754.32PNG: SHA **03d1e86729936c8be09a39af09eedba6f6d2aece07ff97196edce4a09ae7af2d**. BEST3правильно754.32/conf94; FAST3всё ещё ошибается794.32/conf94. Native сохраняет754.32KZT, needs_review/LOW_CONFIDENCE; ledger count0, неправильных794.32расходов0.
- Исходный RU-фискальный8765.43KZTPNG также сохранён. OCR портит обозначение валюты до кириллическихКАТ/КИТ. Эти строки не стали alias. Из другой явно распознаннойKZT сохраняются предзаполненные8765.43KZT с CURRENCY_UNKNOWN; native review/LOW_CONFIDENCE, ledger count0.
- Неоплаченный99.85: предзаполнен, needs_review/UNSUPPORTED_EVENT/LOW_CONFIDENCE, ledger count0.
- Дополнительный PAID123.45: обе модели правильно согласны, high/conf93и94, native posted123.45KZT; ledger count1.
- Дополнительный RU-фискальный6543.21тг: обе модели правильно согласны, high/conf94и94, native posted6543.21KZT; ledger count2. Это отдельный положительный пример, он не заменял исходный проблемный чек.

Последовательные full-image проходы измерены на этих синтетических картинках: BEST515/1328/750/510/940ms, FAST330/275/290/442/269ms. Это время распознавания после init в конкретном эмуляторе, не обещание скорости на телефоне и не замер peak memory.

Моя команда `python -u android-app/qa/app008-scenario/audit_native_evidence.py`, exit0, **389/389PASS**. Проверены все71expected/actual с JSON-типами и pass-флагами, их присутствие в actual stdout, независимые фиксированные amount/currency/date/kind/status/reasons всех5fixtures, количества0/0/0/1/2, отсутствие794.32расхода, оба фактических прохода, install/clear/command/private report chronology, exactAPK/testAPK/source fingerprints и XML42unit. Свидетельства скопированы в неизменяемую папку `final-native/`, hashes и полный audit — `native-evidence-audit.json`.

## История дефектов и границы

Первый кандидатdc6e4... ошибался754→794/conf94/high и был отклонён QA008-OCR01 P1. Следующийcdeeafe... исправил primary754, но strict fiscal expectation прервало тест на неподдержанной OCR-валюте. Оба отказа, исходники и команды сохранены в `before-ocr-fix/` и `before-unknown-currency-prefill/`; первоначальный отчёт сохранён в `REPORT-before-final.md`. Ожидание754.32 и исходный PNG/font/размеры не изменялись для PASS. Изменение fiscal oracle на safe review документировано observed unsupported currency и ограниченным контрактом; причиной автоуспеха его не объявляем. Final test собирает per-fixture failures и выполняет оба этапа всех случаев; любой failure оставляет итоговый JUnitFAIL.

Native тесты вызывают OcrEngine и NativeRuntime media API со своими незарегистрированными test job IDs; **MainActivity/WebView/native bridge/camera/gallery UI, permission chooser и registered Activity job lifecycle не проверены**. Browser preview/harness этого не доказывают. Исходного фото пользователя нет; реальные бумага/свет/перспектива, Kaspiуведомления и все банки не проверены. Две модели могут согласованно ошибаться; пять картинок не являются оценкой общей точности. QR-декодирования нет по объявленной границе. Миграции рассматривает отдельный расчётный QA; их исполнение мной не присвоено.
