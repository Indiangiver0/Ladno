# APP-011: установка на новый телефон тестера

Дата 2026-11-03, Asia/Qyzylorda. Исполнитель команд /root. Новый операционный отчёт вне402frozenфайлов; приложение не пересобиралось.

- Пользователь явно подключил новый телефон тестера и поручил установить рабочую версию. `adb devices -l` обнаружил единственный авторизованный физический Samsung SM_S711B (SM-S711B), отличающийся от прежнего SM-S921B; explicit target использовался во всех командах. Серийный номер в файлах намеренно заменён `<confirmed-tester-phone>`.
- Preflight `adb -s <confirmed-tester-phone> shell getprop ro.build.version.sdk`: exit0, `36`.
- Preflight `pm list packages --show-versioncode com.ladno.finance`: exit0, пустой вывод, прежний пакет не обнаружен.
- SHA-256 основного `android-app/dist/ladno-0.2.0-debug.apk`: `f73e04a0202418ee1dc59c1e669a57228988607de9fb1f60bd388b8e4a20beac`, 68 092 906B, тот же alpha APK из android-alpha-v3/APP-008. AndroidTest/экспериментального reader APK нет в команде установки.
- Фактическая команда `adb -s <confirmed-tester-phone> install -r --no-incremental <project>/android-app/dist/ladno-0.2.0-debug.apk`: exit0, `Performing Streamed Install` / `Success`.
- Readback `pm list packages --show-versioncode com.ladno.finance`: exit0, `package:com.ladno.finance versionCode:2`.
- Readback `dumpsys package com.ladno.finance` с фильтром `versionName=`: exit0, `versionName=0.2.0`.

Не выполнялись clear/uninstall, выдача системных разрешений, чтение финансовых данных/уведомлений, UI/input-команды, изменение защит или драйверов. Приложение пользователь/тестер открывает сам и подтверждает начальную валюту/часовой пояс/доступ к уведомлениям при желании.

Это подтверждение установки на новом устройстве, не полного функционального прогона на нём. Передан существующий набор 0.2.0: ручной учёт, категории/графики, проверенные пути OCR и selected notifications. Полностью автоматические исходящие Kaspi без OS-уведомлений и экранный reader APP-010 не реализованы. Общий вердикт APP-008 и402файла не изменяются; полная приёмка реальной камеры остаётся отдельной.
