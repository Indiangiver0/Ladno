# APP-006 — независимая первоначальная позиция критика

Задача: адресный повтор A2–A5/C005-01/N1–N4. Я `/root/android_court_critic` не автор production, AndroidTest или нового QA evidence; новые позиции адвокатов не читал. Исторический вердикт APP-005 не изменяю.
Версия: `android-alpha-v2`, 215 файлов, digest `b92d7a8f600e73720c415ad18579aaf70ec2166e1ca4a3009314cce1aec6bc16`; production APK прежний `38c9d4490419eda3685e7187281d5f1ecfb958a95d77c57f7f59633febcfbd5a`.

## Выполненное и доказательства

- `Get-Content` нового дела, [QA-head](../reports/APP-006-qa-review.md), [N1](../../android-app/qa/android-calculation/REPORT.md), [N2/N3/N4](../../android-app/qa/android-scenario/REPORT.md), backend-triage/DIAGNOSIS и прежних условий повтора. Это чтение доказательств, не мой повтор Android-тестов или полного аудита.
- Собственные шаги Python json/pathlib/hashlib: сверить SHA каждого v2 файла; вычислить digest строк `sha256 + two spaces + path + LF`; проверить архив v1 по его snapshot; сравнить текущие `app/src/main` с v1. Ожидание: 215/108 совпадений и неизменный production. Факт: exit0, 215/215 SHA, digest совпал, архив108/108, 44 main-файла без изменений.
- Новые исполнения root, независимо разобранные специалистами/главой: 262 N1 +126 N2/N3 =388 assertions PASS в шести instrumentation-командах на API30/x86_64. Это не388 тестовых методов; чужое выполнение себе не присваиваю.

## Оценка изменения доказательств

- **N1 / подтверждено в native API области:** настоящий SQLite/LedgerStore CRUD, nullable категории, точные итоги, CAS/replay/edit/delete/tombstones/post/link/ignore и сохранение после force-stop/PID2343→2388. Прежний тезис об отсутствии native persistence/aggregate исполнения снят. A2/A5 существенно подтверждены, UI→AppBridge→Activity restart пока unknown.
- **N2 / подтверждено в описанной области:** AndroidKeyStore и actual NotificationManager→system callback→production NLS→SQLite после readiness; завершённые income/expense автопроведены, update/revoke/review проверены. Три прежних FAIL сохранены, финальный PASS их не переименовывает: setup-race обоснован, точная причинность каждого старого callback не доказана; stale testAPK — отдельная orchestration ошибка. Production P1 не установлен. A3/A5 усилены; реальный source picker/initial grant readiness/OEM и банки unknown.
- **N3 / подтверждено в API image pipeline:** настоящий JNI и private PNG→NativeRuntime→SQLite дали paid EN123.45/RU234.56 KZT, unpaid→review, same-byte replay без удвоения, malformed→безопасная ошибка. A4/A5 усилены; unregistered test jobId не подтверждает Activity jobs, внешнюю камеру/chooser/FileProvider grant.
- **N4 / подтверждена только ограниченная матрица:** synthetic own-package generic уведомления и шесть чистых PNG; два автоматических расхода, не шесть. Реальные банки/кассы/фото и другие OS/ABI не испытаны. A1 теперь включает реальную установку/native исполнение; A6 требует завершить этот суд и актуальный handoff.

## Замечания и следующий шаг

- **C006-01 / P2 / подтверждён остаточный пробел доказательств, не продуктовый отказ.** C005-01 частично закрыт N1–N3 в native API области; целиком не закрыт. [QA-head A2–A5](../reports/APP-006-qa-review.md) оставляет MainActivity/WebView/формы→bridge, camera/picker и зарегистрированный job lifecycle неисполненными. Для полной приёмки нужен адресный сквозной Android UI/media проход с точными суммами/сохранением/повторами; уже PASS native набор без причины повторять не требуется.
- **C006-02 / P2 / подтверждён предел покрытия:** IA005-02/USR-02 остаются открыты: N4 не доказывает «все банки». Нужны реальные обезличенные форматы/проверенная область и Android delivery; универсальный отказ также не установлен.
- **C006-03 / P2 / подтверждён единичный предел автопроведения, масштаб unknown:** paid KZ PNG распознан как «Теленген», получил UNSUPPORTED_EVENT/review при mean93. Безопасность результата подтверждена, KZ auto accuracy — нет; это конкретизирует IA005-03, но не доказывает массовый отказ или неверный финансовый итог. Нужен целевой размеченный корпус; отрицательные paid/unpaid регрессии сохраняются.

Рекомендация по полной исходной задаче: **insufficient_evidence**; открытых доказанных P0/P1 не предъявляю. Передача того же APK как экспериментальной debug-альфы допустима: прямо сообщить о реальной установке/native проверках на одном Android11/x86_64 эмуляторе, отдельно — о непроверенных Android UI/камере/выборе файла, реальных фото/банках и ARM/OEM. Старую общую оговорку «Android не запускался» использовать нельзя. Охват всех банков не подтверждён; экспорта, восстановления и синхронизации нет, две базы раздельны. Альфа не заменяет исходную цель; окончательный вердикт и область решения определяет отдельный судья после раскрытия и ответов.
