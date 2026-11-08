# APP-006 N1 — результат независимого native ledger QA

Дата исполнения: 2026-11-02 UTC; местная зона проекта Asia/Qyzylorda. Автор тестов и анализа: `/root/android_qa_calculation`, calculation-test-engineer, независимый от production-реализации. Реальные build/install/device/instrumentation команды исполнил начальник `/root`; я проверил сохранённые результаты, ожидаемые/фактические значения и соответствие версий. Передача qa-lead, самостоятельная приёмка всего приложения не выполняется.

**Результат N1 в исполненной области: PASS.** Два настоящих Android instrumentation-теста завершились успешно: write — 166 assertions, read — 96, всего **262 runtime assertions**, ошибок нет. Повторные запуски уже пройденных сценариев при этом анализе не выполнялись.

## Среда, версия и соответствие исходников

- Выделенный AOSP AVD `Ladno_QA_API30`, serial `emulator-5580`, Android11/API30, x86_64. Metadata и guard-результаты находятся в `qa/runtime-host/emulator-state.json`, `install.json`, `stop.json`. Проверка доступности WHPX прошла; в metadata указано отсутствие изменений host security settings.
- Основной APK: SHA-256 `38c9d4490419eda3685e7187281d5f1ecfb958a95d77c57f7f59633febcfbd5a`, 37404606 bytes. Фактически вычисленный SHA совпадает с `dist/build-result.json` и историческим frozen108 `android-alpha-v1.json`; production APK остался побайтно тем же.
- Тестовый APK: SHA-256 `94c418df927a16256aba740d733de8fa6afab56da0ade12b674ea05911cda334`, 412623 bytes. `install.json` содержит успешную установку app/test APK из этих путей; оба exit0/Success. SHA вычислены по локальным артефактам, отдельная device-side hash установленного test APK не снималась.
- Сборка APP-006: fingerprint `902e416a60e02ff092b8dc43f176c421de2d02660c6c0116f457508386c770d7`, stage `C:/Users/04068/.cache/ladno-android/work/ladno-902e416a60e02ff0`. Согласованы build-result и build-source-manifest.
- Все три тестовых source SHA совпали одновременно с заранее переданным `test-source-hashes.json`, текущими файлами, build-source-manifest и staged-копиями. Test Sources: Fixtures `6e4cff286bd8c05b5833dadb2ae93e2363f2c606a1f34413ccbe2869b077016b`; Write `005fc5622cfccaa0a8b6bdc3270ee1b549dd73ef082ec71dbb0634d5d9574903`; Read `576456bf2db96343bc4cd47e718329e2cb4cb6e2b467ad0a777315677e19ef36`.
- DDL SHA `4f09fb9958feebf818e9a51cdce1bd5b58e97afdb79fc77ca4974145cd96d49d` совпал у текущего файла, staged-копии, manifest, frozen108 и `assets/db/schema.sql`, извлечённого непосредственно из APK. LedgerStore SHA `bf58b0d8f6a9a4b6abba103a4fd050aa66e632cb5d04fff3a7041e530f70fdba` совпал у current/stage/build manifest/frozen108.

## Команды и доказательство перезапуска

Полные команды с `adb -s emulator-5580`, stdout, stderr, exit codes и метками времени сохранены в `qa/runtime-host/test-write.json`, `test-read.json`, `stop.json`.

1. Write: `am instrument -w -r -e class com.ladno.finance.storage.LedgerRuntimeWriteTest com.ladno.finance.test/androidx.test.runner.AndroidJUnitRunner` — exit0, `OK (1 test)`, `INSTRUMENTATION_CODE: -1`. Wrapper завершён 17:16:56 UTC.
2. Контрольный private fixture-файл извлечён до остановки. В нём writePID=2343, generated IDs и девять фиксированных независимо рассчитанных сводок.
3. Реальный `am force-stop com.ladno.finance` на том же проверенном serial — exit0, 17:17:35 UTC. Очистки app data между write/read нет в последовательности команд.
4. Read отдельной `am instrument` командой для `LedgerRuntimeReadTest` — exit0, `OK (1 test)`, `INSTRUMENTATION_CODE: -1`, 17:17:39 UTC. **readPID=2388**, отличается от writePID2343. Read использовал новый `LedgerStore.get(Context)` в новом процессе, без singleton reset/reflection/подмены SQLite.

Private evidence извлечены в `pulled-qa-ledger-write.json` и `pulled-qa-ledger-read.json`: оба `completed=true`, API30, x86_64. Проверены все 262 пары expected/actual: значения и JSON-типы совпадают, все pass=true, ID уникальны. Число assertion-событий в настоящем instrumentation stdout соответствует privateJSON:166/96, pass=false отсутствует. Исходные логи и private evidence не изменялись анализатором.

## Что фактически прошло

- Настоящий `LedgerStore.get/state/execute/ingestCandidate` через Android Context и SQLiteOpenHelper: чистая установка, SETUP_REQUIRED до настройки, явные KZT/Asia/Qyzylorda/POST_TIME_IN_ZONE, canonical денежные строки и nullable категория.
- Native summary: `0.10+0.20=0.30`; расходы1.20+2.30=3.50, net−3.20; USD7.89 отдельно. Недопустимые ноль/знак/экспонента/ведущие нули/лишняя дробная точность/25 цифр отдельного ввода и 2026-02-29 дают VALIDATION; неправильный вид категории — REFERENCE_CONFLICT. После отказов native список и итог прежние.
- Назначение категории сохраняет ID; stale revision даёт CONFLICT и не меняет сумму. Переименование/удаление используемой категории не удаляет операции, categoryId становится null. Перенос одной операции октябрь→ноябрь даёт октябрь KZT income0.30/expense2.30/net−2.00 и ноябрь expense1.20. Ошибочная правка даты сохраняет запись и revision.
- Реальный агрегатор/хранение: выше2^53 income9007199254740993.03, expense9007199254740992.99, net0.04; агрегат длиннее24 целых цифр `1999999999999999999999999.98` без обрезки. JPY scale0 и KWD scale3 учитываются отдельно, JPY `1.0` отклонён. 9999-12-31 остаётся в терминальном месяце с expense0.01.
- Selected-source проверка на уровне StorageApi; полный high-кандидат автоматически проведён. Синтетическая согласованная provider/account/reference identity связывает receipt с прежним ID без второй суммы. Ручная правка суммы12.34→13.34 сохраняется при exact replay; новый revision остаётся review.
- Слабое совпадение требует решения: post без confirmDistinct отклонён, link сохраняет одну сумму, stale решение — CONFLICT; confirmDistinct создаёт отдельную запись. Ignore не создаёт запись. Явный post uncertain-кандидата разрешён после проверки. Отсутствующая безопасная source identity остаётся review с IDENTITY_UNCERTAIN. Не выбранный notification source отклонён без новой pending-записи.
- Удаление проведённой операции с последующим exact replay не воскрешает её; record=null. Итог июня после удаления21.23=13.34+7.89, два pending-материала в сумму не входят.
- В новом процессе повторно прошли девять периодных/валютных сводок, amount/date/revision/note/nullable category перенесённой записи, оставшаяся категория, selected sources, оба pending решения и проведённые ID. Exact replay tombstone и ignored-source после process restart не изменили итог/не создали расход.

Девять ожидаемых сводок в контрольном файле дополнительно сверены с буквальными независимыми ожидаемыми значениями из тестового плана, а не только с флагом test PASS. Запись всех SHA и результаты пассивной сверки находятся в `result-summary.json`; её analysisCheckCount — число проверок доказательств, не дополнительные Android runtime-тесты.

## Замечания и границы

Подтверждённых дефектов N1 в исполненном наборе нет. Прежний судебный insufficient_evidence и APP-005 frozen-материалы сохраняются как история; этот результат закрывает нативную область N1 для последующего review главы и нового судебного рассмотрения.

Покрыт один AOSP эмулятор Android11/API30 x86_64. Тесты не управляют Activity/WebView, внешней камерой, галереей или клавиатурой; не проверяют OEM ограничения, power-loss/crash во время commit, обновление схемы и все устройства Android8+. Синтетические N1-токены только представляют валидный StorageApi wireformat: они не являются доказательством AndroidKeyStore, actual NLS доставки, форматов реальных банков или OCR. N2/N3 и интерфейсные границы проверяются отдельно. На личные телефоны эти тесты не направлялись.

Следующее действие: qa-lead проверяет реальные evidence и дизайн тестов, объединяет N1 с отдельными N2/N3 и передаёт новую зафиксированную версию суду. Полную готовность приложения этот специалист самостоятельно не утверждает.
