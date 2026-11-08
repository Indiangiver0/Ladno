# APP-006 — backend triage NLS delivery

Версия: backend-triage-v2, 2026-11-02. Проверяющий: backend-data-lead. Область: адресное чтение результата повторного N2b после test-only readiness setup. OS-команды выполнял начальник, тесты написал независимый QA-сценарист; глава проверил сохранённые JSON. Production/frozen APP-005 не изменены.

## Повтор N2b: фактическое закрытие setup finding

Начальник выполнил тот же денежный сценарий после добавления QA-автором публичного readiness ping: NotificationManager → настоящий production listener → новый needs_review commit; без прямого вызова callback, reflection и подмены gates. TestAPK SHA-256 по начальнику: `AC23D80872E07B52A075249F94C39B7C710EDDE2E1954CE87A2FE342DA7E7602`. Основной APK `38c9d4490419eda3685e7187281d5f1ecfb958a95d77c57f7f59633febcfbd5a` остался побайтно прежним. Денежные assertions не ослаблены.

Глава прочитал текущие [runner JSON](../../android-app/qa/runtime-host/test-notifications-osGrantedDelivery.json) и [structured evidence](../../android-app/qa/runtime-host/pulled-notification-os-granted.json) через `Get-Content -Raw | ConvertFrom-Json`, выделил все18 checks и readiness/cleanup/financial observations; `Get-FileHash -Algorithm SHA256` выполнен для обоих. Ожидание: действительный callback→commit до baseline и полный исходный денежный сценарий. Факт: API30/PID3075, startedAt `2026-11-02T18:38:16.031Z`; runner exit0, `OK (1 test)`, 18/18 checks PASS.

- Readiness: безденежный body `QA readiness 2087-07-17`, maxWait60000ms/minInterval1000ms; commit получен `18:38:18.786Z`. Новый кандидат `a670783e-3ce0-455e-90f1-7bc4f8041a80`: needs_review, notification, kind/amount/currency/transactionId=null, UNKNOWN_KIND/AMOUNT_MISSING/CURRENCY_MISSING/LOW_CONFIDENCE. Финансовой записи probe не создал.
- Cleanup: только этот новый probe ID переведён в ignored, quiet window1500ms; перед baseline нет новых probe pending. Неиспользуемый источник затем не изменил records/imports за3s.
- Денежный путь: реальный OS expense `52703916.19 KZT` автоматически сохранён с source=notification, date=2026-11-02 и null category; record delta3→4. Обновление того же OS notification стало needs_review, второй расход не появился. Реальный OS income также проведён, count4→5; отрицательное сообщение ушло в review, count остался5. Это обновление с новым postTime, а не утверждение о точном callback replay.
- Текущие SHA-256: runner JSON `7B43D30CCC2339F28E81C7E31EE2B834887C98A4B365EB65BD2A1BBD4C87A994`; structured evidence `4B41BBDD2A16A7EE86FA7EC9A2FD1D4755198F58FDCD87EE815A287F60612CD9`.

BD6-H01 / setup finding / закрыто: фактическое ожидание readiness устранило воспроизводившийся отказ fixture без изменения production и без ослабления финансовых проверок. Это уточняет прежнюю timing-гипотезу; точная причина каждого недоставленного прежнего callback отдельно не инструментировалась. Production P1 не устанавливается. BD6-E01 / прежний пробел N2b / закрыт для данного синтетического API30 прогона: получен положительный публичный OS→listener→DB результат. Два первоначальных FAIL остаются историческими доказательствами; начальник сохранил предыдущие копии. Ниже пути/хеши v1 относятся к первоначальному просмотру; текущие одноимённые JSON теперь содержат успешный v2 прогон с хешами выше.

Дополнительный контекст по начальнику, без самостоятельного повторного аудита главы: N1 — 262 Android API assertions и persistence между PID2343→2388 PASS; N3 — 71 checks PASS, реальные NativeRuntime→Tesseract→SQLite EN/RU оплаченные входы создали расходы, KZ с ошибкой диакритики корректно остался review. Эти результаты не расширяют N2b до банковских форматов, устройств или языков, не испытанных в корпусе.

Следующее действие: независимый QA-head объединяет N1–N3 и ограничения по одной evidence-версии; затем адресный суд. Глава новых OS-прогонов не выполнял, production не правил, универсальную поддержку банков/чеков и итоговую приёмку APP-006 не утверждает.

## История первоначального triage v1

## Задача и версия

Разобрать два неуспешных N2b `osGrantedDelivery` на выделенном AOSP Android 11 / API30 x86_64 `Ladno_QA_API30`, serial `emulator-5580`. Основной APK остаётся `38c9d4490419eda3685e7187281d5f1ecfb958a95d77c57f7f59633febcfbd5a` по начальнику. N1 и N2a имеют отдельные успешные результаты по QA/начальнику; они не подтверждают фактическую OS-доставку N2b.

Прочитанные production файлы, SHA-256 вычислены главой:

- `android-app/app/src/main/java/com/ladno/finance/BankNotificationService.java`: `EF1D6DA43E4F1BE253D6371055DCD23F9BCF5D638A99AFF3AE9EA80A50B76C76`.
- `android-app/app/src/main/java/com/ladno/finance/NativeRuntime.java`: `55E1C1EB3A3C1467AAEC0694DB51D7C34925E6DE1E32408533DA62164B5B2705`.

## Выполненное и доказательства

- Глава выполнил `Get-Content` BankNotificationService, NativeRuntime relevant sections, NotificationRuntimeTest и NativeRuntimeFixtures; JSON прочитан через `ConvertFrom-Json` с выделением checks, runtime snapshot, собственного синтетического SBN и service diagnostics. Ожидание: локализовать отказ до/после ingress без изменения gates. Факт: ниже перечислены наблюдения и границы вывода.
- [N2b runner result](../../android-app/qa/runtime-host/test-notifications-osGrantedDelivery.json), SHA `D87DD3568E37593727E9E2D46797F76D2641F642341FE23C9C6675EBB54858BE`: реальный тест завершился FAIL по deadline 30s, а не PASS.
- [N2b structured evidence](../../android-app/qa/runtime-host/pulled-notification-os-granted.json), SHA `2F1CC3CDAFE30A0879EF83568C6BB6EC5080F9CD9496AA2396AD58BC6E9E0110`: PID2737; actual OS NLS grant=true; initialized=true; sources включают `com.ladno.finance`; notificationError=null; notifications enabled=true, channel importance=3. Собственный синтетический SBN имеет UserHandle{0}, flags=8, title `Ladno synthetic QA`, body/bigText `Paid 52674934.19 KZT 2026-11-02`. Combined production parser в evidence даёт expense/52674934.19/KZT/date/high/reasons=[]. До deadline ожидаемая notification-операция не появилась. Null notificationError сам по себе не доказывает получение callback.
- [Диагностика во время polling](../../android-app/qa/runtime-host/diagnose.json), SHA `DEB70AC219E32D78372B5999E108CE1A26AF6FCD48167491E350637ADAD2C752`: OS показывает ServiceRecord и system binding `com.ladno.finance/.BankNotificationService`, процесс2737; это доказывает наличие сервиса к моменту диагностики, не его готовность к моменту более раннего поста.
- [Logcat собственного процесса2737](../../android-app/qa/runtime-host/logcat-2737.json), SHA `E768E7A6EE8B53D272CDA1C7EB52852F5D31492D74E067338BDFED392845272C`: тест/polling исполняется на instrumentation thread2759, main thread2737 отдельный. В прочитанном await обычный SystemClock.sleep на тестовом потоке; признака блокировки mainLooper самим 30s polling не найдено. Зафиксирован assertion deadline; диагностического подтверждения вызова production onNotificationPosted нет.

## Gates и timing

Production до ingress проверяет: nonnull SBN, current user, actual notification access, setup, selected package и отсутствие FLAG_GROUP_SUMMARY. Snapshot User0/flags8/grant/setup/sources соответствует этим требованиям; flags8 не является group-summary flag. Эти значения сняты после поста, поэтому они не доказывают значения каждого gate в момент гипотетического callback. Parser/storage исключение в capture обычно публикует notificationError; ранний return и отсутствие framework callback могут оставить error=null.

Ключевое сравнение из артефактов: SBN postTime=`1793643674952`, то есть `2026-11-02T18:21:14.952Z`. Диагностика помечена `2026-11-02T23:21:24.281856+05:00` (=18:21:24.281856Z); ServiceRecord имеет `createTime=-5s300ms`. При сопоставимости host/device часов это даёт приближённое создание около18:21:18.982Z — примерно через4s после финансового поста. ServiceRecord creation не является точной отметкой Java onListenerConnected; сравнение поддерживает setup-race hypothesis, но не доказывает её причинность.

Production `onListenerConnected` только фиксирует lifecycle/refresh, не импортирует активную историю. Проверенный [исходник Android11 NotificationListenerService](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r48/core/java/android/service/notification/NotificationListenerService.java) разделяет grant и подключение; framework устанавливает connected отдельно и пропускает handler dispatch, пока connected=false. Следовательно, более поздний grant/live snapshot недостаточен для доказательства готовности к раннему посту. Источник просмотрен главой через web; это анализ framework, не новый runtime test.

## Замечания и оставшиеся вопросы

- BD6-H01 / гипотеза, приоритет дефекта не назначен: fixture публикует финансовое событие до подтверждённой готовности listener после запуска instrumentation. Timing и отсутствие нового ingress совместимы с этим объяснением. Причина двух FAIL ещё не подтверждена повторным успешным прогоном.
- BD6-E01 / пробел доказательств N2b: два прежних OS delivery FAIL сохраняются. По ним нельзя объявить N2b пройденным или подтвердить production P1 без локализации причины. Положительные N1/N2a и parser-high не заменяют callback→commit.
- Производственный дефект gates/SQLite/parser в этом ограниченном triage не воспроизведён. Исправление native-автору не назначается; frozen production менять оснований пока нет.

## Рекомендуемое следующее действие

Начальник согласовал test-only setup QA-сценаристу `/root/android_qa_scenario`: после реального OS grant и выбора источника публиковать через публичный NotificationManager безденежный readiness ping; дождаться фактического нового needs_review commit по его безопасной уникальной идентичности, максимум60s с интервалом1s; игнорировать только собственные новые candidate IDs, отменить собственный ping, затем установить baseline и выполнять неизменные unselected/financial assertions. Это проверяет публичный OS→production callback→DB путь и не подменяет permission gates, callback или хранилище. Private framework reflection не требуется.

После фактического rerun сохранить прежние FAIL и новые expected/actual. Если readiness/денежные случаи снова не проходят, отдельно локализовать callback/gates и передать воспроизводимый дефект оригинальному native-автору через chief. Глава здесь не утверждает fixture fix, будущий PASS, готовность NLS всех банков либо приёмку APP-006; отдельные QA-head review и суд остаются обязательными.
