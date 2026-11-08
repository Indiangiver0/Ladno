# APP-012: доставка на дополнительный телефон

Дата: 2026-11-03. Автор фактических ADB команд: chief `/root`.

## Цель и границы

Пользователь попросил установить ту же версию на ещё один подключённый телефон, затем подтвердил подключение и USB-отладку. Единственная авторизованная новая цель: модель V2443BA, API35. В командах ниже `<confirmed-additional-tester-phone>` заменяет фактически выбранный полный ADB serial; это явная редакция идентификатора для отчёта. Реальные вызовы использовали один и тот же явный serial, не автоматический выбор устройства.

Основной артефакт: `android-app/dist/ladno-0.2.0-debug.apk`, 68 092 906 байт, SHA256 `f73e04a0202418ee1dc59c1e669a57228988607de9fb1f60bd388b8e4a20beac`, пакет `com.ladno.finance`, версия 0.2.0/code2. Это ранее рассмотренная alpha, не новая реализация. APK/исходники/frozen402/старые вердикты не изменялись.

ADB: `C:\Users\04068\.cache\ladno-android\sdk\platform-tools\adb.exe`.

## Фактические проверки и установка

1. `adb devices -l`: единственная цель в состоянии `device`, модель V2443BA. `adb -s <confirmed-additional-tester-phone> shell getprop ro.build.version.sdk`: exit0, `35` (minSDK26 совместим).
2. `adb -s <confirmed-additional-tester-phone> shell pm list packages --show-versioncode com.ladno.finance`: exit0, пустой вывод до установки. `Get-FileHash -LiteralPath 'android-app/dist/ladno-0.2.0-debug.apk' -Algorithm SHA256`: совпал с указанным SHA.
3. Первая попытка `adb -s <confirmed-additional-tester-phone> install -r --no-incremental <absolute-main-apk-path>`: exit1, `Performing Streamed Install`, `Failure [INSTALL_FAILED_ABORTED: User rejected permissions]`. После неё повторный pm-запрос: exit0, пустой вывод. Пользователь сообщил, что окна на телефоне не было. Эта попытка не была успешной установкой; точная причина отказа не установлена.
4. Повторная стандартная установка того же файла с предварительной передачей: `adb -s <confirmed-additional-tester-phone> install -r --no-streaming --no-incremental <absolute-main-apk-path>`. Передано 68 092 906 байт, `Performing Push Install`, `Success`, exit0.
5. После успешной установки `adb -s <confirmed-additional-tester-phone> shell pm list packages --show-versioncode com.ladno.finance`: exit0, `package:com.ladno.finance versionCode:2`.
6. `adb -s <confirmed-additional-tester-phone> shell dumpsys package com.ladno.finance | Select-String -Pattern 'versionName='`: exit0, `versionName=0.2.0`.

Повторная команда оставила системные проверки установки включёнными. Очистка/удаление приложения, выдача разрешений, отключение защит, установка androidTest, чтение банковских/финансовых данных и UI действия не выполнялись. Не утверждается, что смена способа передачи сама по себе установила причину первого отказа.

## Результат и ограничения

Операционная доставка основного APK успешна, пакет и версия подтверждены чтением с выбранного телефона. Запуск и настройка выполняются пользователем. Нативный камерный сценарий и банковские операции на этом телефоне не тестировались; установка не закрывает C8-01/USR8-01 и не реализует автоматизацию исходящих Kaspi без уведомлений. APP-008 verdict не переписывается.

Отдельная проверка главы и реального security специалиста завершена: `team/reports/APP-012-delivery-review.md`, DEL12-01 закрыт. Это проверка операционной доставки неизменной alpha.
