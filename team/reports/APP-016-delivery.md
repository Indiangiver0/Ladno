# APP-016: фактическая доставка на основной SM-S921B

2026-11-04, Asia/Qyzylorda. Исполнитель ADB: chief `/root`. Это операционная доставка экспериментальной версии, не полная продуктовая приёмка.

## Артефакт и границы

`android-app/dist/ladno-0.3.0-debug.apk`, 68 393 641B, SHA256 `a8ec70cd97674fb6f9c8d224e10139611c52a4ac25f69f6cd26143f7d88e5b76`, packagecom.ladno.finance/code3/name0.3.0, sourceFingerprint85db77f1cbb5412032dfdc869c4c14faadc07def9992ab620d9a6e05ead03284. Подпись совпадает с0.2.0 по независимому QA.

APP-013-verdict не переписывается: общий revise (APIнеподключён), отдельно approve ограниченной экспериментальной передачи точного APK. Пользователю перед установкой раскрыты APIabsent, Freedom/Halykfixunproven, damagedneighbor и отсутствие реального чекового испытания. Установка в рамках продолжающегося запроса исправить приложение и ранее разрешённой доставки обновлений, не «разрешение суда на телефон».

ADB бинарник `C:\Users\04068\.cache\ladno-android\sdk\platform-tools\adb.exe`. Ниже `<confirmed-main-phone>` — явная редакция полного serial для отчёта, все реальные вызовы использовали один и тот же подтверждённый полный serial. Serial/UID/PID/private device paths здесь намеренно не сохраняются.

## До установки

После ответа пользователя «подключил» inventory показал единственный authorized `device`, модель SM-S921B/API36. `adb -s <confirmed-main-phone> shell pm list packages --show-versioncode com.ladno.finance`: exit0, `package:com.ladno.finance versionCode:2`. SDKgetprop:36. Targeted secure setting фильтр подтвердил выданный компонент NLS; service metadata показывала systembinding requested/received/hasBound=true. Targeted packagefilter показал kz.kkb.homebank/ffinbank.MyFreedom. Текст/extras/банк/DB не читались.

## Установка и раздельное чтение результата

1. `adb -s <confirmed-main-phone> install -r --no-incremental <absolute-path-to-main-0.3.0-debug.apk>`: `Performing Streamed Install`, `Success`, exit0 (4.714s).
2. `adb -s <confirmed-main-phone> shell pm list packages --show-versioncode com.ladno.finance`: exit0, `package:com.ladno.finance versionCode:3`.
3. `adb -s <confirmed-main-phone> shell dumpsys package com.ladno.finance | Select-String -Pattern 'versionName='`: exit0, `versionName=0.3.0`.
4. `adb -s <confirmed-main-phone> shell settings get secure enabled_notification_listeners` с фильтром только com.ladno.finance: exit0, `com.ladno.finance/com.ladno.finance.BankNotificationService`. Существующий доступ сохранён; root его не выдавал.
5. `adb -s <confirmed-main-phone> shell dumpsys activity services com.ladno.finance/.BankNotificationService` с фильтром service/binding: exit0, user0 active service, `requested=true received=true hasBound=true doRebind=false`.

Ни clear/uninstall/-g/AndroidTest, ни UI черезADB, ни settings mutations, ни банковских действий/чтения уведомлений/частной DB не было. Ожидаемое сохранение данных install-r не выдаётся за чтением подтверждённую сохранность каждой финансовой записи. Успешная привязка службы не доказывает доставку/распознавание следующего конкретного банковского callback.

## Предел результата

Версия установлена и readback подтверждён. Реальный новый Freedom/Halyk event и пользовательский QR ещё не испытаны; старые пустые входящие не переразбираются из rawtext, поскольку он не хранится. APIОФД/all-bankautomation/damagedneighbor остаются открытыми. Нужен реальный пример QR и разрешённое наблюдение нового уведомления для дальнейшего APP-015, не новый вывод «всё работает».

Head проверяет новую доставку отдельно в APP-016-delivery-review.md; functionalQA/judge неизменного APK ранее выполнены и сохраняют свои ограничения.
