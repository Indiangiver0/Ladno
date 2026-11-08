# APP-008: фактическая доставка обновления на Samsung

Дата: 2026-11-03, Asia/Qyzylorda. Исполнитель команд: /root. Последующее свидетельство USR8-02, вне 402 frozen файлов; вердикт APP-008 не изменяется.

- Телефон: тот же ранее подтверждённый Samsung SM-S921B, единственный физический ADB target. Полный серийный номер не дублируется в репозитории: `<confirmed-Samsung>` ниже — явная редактированная замена фактической цели.
- Наш ADB37.0.1 переключён с LIBADBUSB на NATIVE через документированную process-local `ADB_USB_LEGACY=1`. Глобальные переменные Windows не менялись. Само переключение не дало сразу авторизации.
- Существующий host private key сохранён: `adb pubkey <existing-user-key>` exit0, валидная Android public часть вычислена; содержимое не выводилось/не сохранялось, private key до/после побайтно не изменился. Отсутствие `.pub` не объявлено причиной и не исправлялось новой парой.
- Пользователь сам выключил/включил USB-отладку и сообщил о появившемся RSA-окне, затем нажал «Разрешить». После этого `adb devices -l` показал подтверждённый Samsung в состоянии `device`, model SM_S921B. Вклад каждого предпринятого шага в восстановление отдельно не доказан.
- До обновления `adb -s <confirmed-Samsung> shell pm list packages --show-versioncode com.ladno.finance` — exit0, `package:com.ladno.finance versionCode:1`.
- Проверен SHA-256 основного `android-app/dist/ladno-0.2.0-debug.apk`: `f73e04a0202418ee1dc59c1e669a57228988607de9fb1f60bd388b8e4a20beac` (68 092 906B), тот же рассмотренный судом alpha APK.
- Фактическая установка `adb -s <confirmed-Samsung> install -r --no-incremental <project>/android-app/dist/ladno-0.2.0-debug.apk` — exit0, stdout `Performing Streamed Install` / `Success`.
- После обновления `pm list packages --show-versioncode com.ladno.finance` — exit0, `package:com.ladno.finance versionCode:2`; `dumpsys package com.ladno.finance` с фильтром `versionName=` — exit0, `versionName=0.2.0`.

Установка поверх прежней версии, без uninstall/clear, установки androidTest, выдачи разрешений приложения, чтения финансовых данных/уведомлений, запуска UI/input через ADB, изменения драйверов или отключения защит. Это подтверждение доставки, а не чтение и проверка пользовательских записей. Сохранность миграции подтверждена отдельно синтетическими тестами.

Критерий доставки USR8-02 выполнен по условиям неизменённого вердикта. C8-01/USR8-01 остаётся открыт: обычная камера/галерея, зарегистрированная job и видимый результат на Android, а также исходный чек пользователя этой доставкой не проверялись. Пользователь открывает приложение сам. Полная APP-008 не объявлена принятой.
