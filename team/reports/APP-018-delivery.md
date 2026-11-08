# APP-018: фактическая запись доставки

2026-11-04, Asia/Qyzylorda. Исполнитель ADB — root. Статус: install Success и отдельный readback подтверждены; отдельная проверка специалиста и главы завершена, DEL18-01 закрыт для операционной доставки.

## Артефакт и границы

- Main `android-app/dist/ladno-0.4.0-debug.apk`, com.ladno.finance, 0.4.0/code4, 68393973B, SHA256 `f54733b034399ec0b147d0fa7e9b5b82a8589034ca5c8692b63c905a2aa37cd3`; root собственным Get-FileHash получил совпадение. Build-result соответствует frozen v5; совместимость подписи с 0.3 атрибутирована прежнему независимому QA, не новому извлечению сертификата root.
- APP-017-verdict: общий revise, отдельно допустима ограниченная development-передача exact bytes. Подтверждены только API для двух читаемых образцов; DEC-01/P1, UI и остальные указанные ограничения сохраняются. Установка не равна общей функциональной приёмке.
- Пользователь подтвердил подключение телефона в ответ на запрос USB-доставки. Обновление без очистки данных, снятия защиты и выдачи новых разрешений.

## Диагностика до установки

1. `adb devices -l` — exit0, список пуст. Никакой target не выбран, установка не выдана за успех.
2. Windows `Get-PnpDevice -PresentOnly`, фильтр Android/Samsung/ADB — OK для Samsung Mobile USB Composite Device, Modem, Android ADB Interface. Instance ID и serial в отчёт не копировались.
3. `adb server-status` — корректный SDK adb37.0.1, USBbackend NATIVE; используется прежний default ADB key path, ключи не читались/менялись. `host-features` и повтор devices-l — exit0, устройства нет.
4. Один bounded перезапуск только host ADB: kill-server → start-server → devices-l — exit0, daemon started, список пуст. Драйверы, USB backend, ключи/разрешения, защита телефона и данные не менялись.
5. Пользователю отправлена просьба разблокировать экран, переключить «Отладка по USB» и подтвердить RSA при появлении окна. Ожидается ответ/транспорт.

## Появление цели и фактическая установка

6. После просьбы переключить отладку root повторил `adb devices -l`: единственное устройство `<confirmed-main-phone>`, состояние `device`, model SM_S921B, product e1sxxx. Serial совпал с прежним основным телефоном APP-016, в отчёте скрыт. Пользователь затем ответил «Переключил, окна нет»; root сообщил, что телефон уже виден. Новое разрешение вручную/из ADB не выдавалось.
7. Явная единственная цель во всех дальнейших командах: `adb -s <confirmed-main-phone>`. Read-only `shell getprop ro.build.version.sdk` дал36; `shell pm list packages --show-versioncode com.ladno.finance` дал versionCode3; `shell dumpsys package com.ladno.finance` с host-фильтром только versionCode/versionName дал0.3.0/code3/min26/target36. Ни тексты уведомлений, ни БД не читались.
8. Фактическая команда root: `adb -s <confirmed-main-phone> install -r --no-incremental <absolute-path-to-android-app/dist/ladno-0.4.0-debug.apk>`; **exit0, Performing Streamed Install / Success**, длительность6.249s. Только exact main APK; без uninstall/clear/-g/test APK, изменения защиты/разрешений. Обычное обновление допускает сохранение данных, но каждую реальную денежную запись отдельным чтением не проверяли.
9. После Success отдельные команды readback `shell pm list packages --show-versioncode com.ladno.finance` и `shell dumpsys package com.ladno.finance` с фильтром versionCode/versionName: **exit0**, `package:com.ladno.finance versionCode:4`, `versionCode=4 minSdk=26 targetSdk=36`, `versionName=0.4.0`. Это подтверждение установленной версии, не только факт отправки APK.

## Результат и пределы

Операционная установка точной0.4.0 на подтверждённый основной Samsung выполнена, версия после установки подтверждена. Root отправляет эту запись главе для отдельной проверки с security-специалистом. Полный serial в сохраняемые материалы не внесён.

Новых build/финансовых тестов/ОФД-запросов и действий UI не было. Физический camera/gallery→журнал сценарий, получение реального банковского callback и сохранность каждой прежней денежной записи этим обновлением не доказаны. Старые pending сами не заполнятся; два исходных QR545/490 остаются нераспознанными. Общий APP-017 revise не меняется.
