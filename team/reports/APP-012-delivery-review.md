# APP-012: проверка доставки, версия 1

2026-11-03, Asia/Qyzylorda. Глава `/root/tester_install_review_lead`, architecture-lead. APK `android-app/dist/ladno-0.2.0-debug.apk`, `com.ladno.finance`, 0.2.0/code2, 68 092 906B, SHA256 `f73e04a0202418ee1dc59c1e669a57228988607de9fb1f60bd388b8e4a20beac`.

**Доставка существующей ограниченной alpha на дополнительный V2443BA/API35 подтверждена по фактической записи chief и отдельной сверке артефакта.** Общая приёмка приложения не выносится.

## Выполненное

Повторно прочитаны текущие AGENTS.md, PROTOCOL.md, полные профили главы/специалиста и APP-012. Одному реальному security-engineer `/root/tester_install_review_lead/security_delivery_verify` через followup переданы полный профиль, новая цель и read-only границы. Он заново проверил provenance и отдельным followup прочитал новый delivery-report. Глава проверил оба результата самостоятельно. Для узкой операционной задачи второго специалиста не привлекали; изменён только этот новый отчёт.

## Доказательства: ожидание и факт

- Специалист и глава независимо выполнили `Get-Item`/`Get-FileHash -Algorithm SHA256` для основного APK и сравнили размер/hash с `team/snapshots/android-alpha-v3.json`. Ожидалась точная судебная версия; факт: 68 092 906B/SHA выше, `matchesSnapshot=true`. `build-result.json` также совпал со snapshot: 559B, SHA `7c907c08a1bbb2ac04bf5f12207034032833d64567a7779c09c296bfeb8bee38`. Глава прочитал metadata: 0.2.0/code2, sourceFingerprint `d20ab8f54d5aede6bf35af8b1b1b01caacd5bc890bece57d303023514b444fe8`; `metadataMatches=true` со snapshot и actual APK.
- Глава и специалист отдельно выполнили чтение [APP-012-delivery.md](APP-012-delivery.md). Ожидались однозначная разрешённая цель, Success и отдельный readback package/version. Факт chief: единственная авторизованная модель V2443BA, API35, пакет до установки отсутствовал; во всех фактических ADB командах один явный serial, в отчёте он намеренно заменён `<confirmed-additional-tester-phone>`.
- Первая `install -r --no-incremental` завершилась exit1, `INSTALL_FAILED_ABORTED: User rejected permissions`; последующий pm дал пустой вывод. Пользователь сообщил, что окна не было. Этот FAIL сохранён; точная причина не установлена.
- Повторная `adb -s <confirmed-additional-tester-phone> install -r --no-streaming --no-incremental <absolute-main-apk-path>` передала 68 092 906B, дала `Performing Push Install`, `Success`, exit0. Последующие отдельные `pm list packages --show-versioncode com.ladno.finance` и `dumpsys package com.ladno.finance` с фильтром `versionName=` дали exit0, `package:com.ladno.finance versionCode:2` и `versionName=0.2.0`. Противоречий с проверенным артефактом нет.

ADB команды исполнял chief. Глава/специалист проверили сохранённую запись, собственных ADB/UI команд не выполняли. По записи chief clear/uninstall, выдача разрешений, отключение защит и чтение финансовых данных не выполнялись. Фактическую сохранность журнала чтением телефона не проверяли. Сборка, тесты, запуск приложения и полный аудит 402 frozen файлов заново не выполнялись.

## Замечания, вопросы и следующее действие

- **DEL12-01 — закрыт, прежний P2-пробел свидетельства доставки:** новый отчёт получен и прочитан; Success/readback подтверждены документально. Первая неуспешная попытка не выдана за успех и не объявлена доказанным дефектом APK.
- **C8-01/USR8-01 — существующий P2, остаётся открытым:** полный камерный путь с реальным чеком и видимым результатом этим не подтверждён. Автоматические исходящие Kaspi без push и экранный reader не реализованы. APP-008 verdict остаётся `insufficient_evidence`.

Блокирующих вопросов по этой доставке нет. Chief рекомендуется сообщить установку 0.2.0 на дополнительный телефон и сохранить известные функциональные ограничения. Проверка нового реального камерного/банковского сценария остаётся отдельной задачей с независимой приёмкой; старые судебные решения этим отчётом не заменяются.
