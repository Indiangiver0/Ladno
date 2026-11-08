# APP-004 — проверка главы архитектуры, попытка 1

Дата: 2026-11-02. Проверяющий: architecture-lead. Статус: `capacity_interrupted`; новый APP-005 стал приоритетом до завершения первой проверки. Это частичный head review, не приёмка, QA или судебный вердикт.

## Задача и проверенная версия

- `docs/architecture/AUTO-ENTRY.md`, APP-004-AUTO-ENTRY v1, 88 строк; SHA-256 `1AB0799B3CE216CEA4A030B17F826F2BFCA9A31EC25D86D16631DDB19CDF0D96`.
- `docs/architecture/AUTO-ENTRY-PERMISSIONS.md`, APP-004-PERMISSIONS v1, 55 строк; SHA-256 `84F721D5FFC323EF68858F2662738F8675ADC0202D0C4DB20766E997BB2F6125`.
- Реальные авторы: `/root/personal_mvp_system` и `/root/personal_mvp_security`; проверяющий не автор этих документов. Изменение любого файла требует повторной фиксации версии.

## Выполнено и доказательства

- Команда `Get-Content -LiteralPath` для обоих документов/APP-004 и нумерация строк: ожидалось прочитать оба результата; факт — прочитаны целиком, включая конец файла отдельным чтением после усечения общего вывода.
- Команда `Get-FileHash -Algorithm SHA256 -LiteralPath` для обоих документов: ожидались заявленные финальные хэши; факт — оба совпали с указанными выше.
- Команда `rg -n 'MoneyDTO|scale|amount|operationDate' docs/architecture/STRUCTURE.md docs/finance/MODEL.md`: факт — строковый MoneyDTO, целые минимальные единицы, запрет скрытого округления и совместного суммирования валют согласуются с предыдущим контрактом; KZT остаётся примером.
- Лично выполнены web open/find/search по официальным ссылкам 2026-11-02; ожидались основания технических границ, факт — подтверждены пункты ниже. Apple HTML даёт JS-заглушку; содержание проверено индексируемыми официальными страницами.
- Android NLS: API18, manifest binding, подключение, low-RAM Android10↓ и work profile ограничения подтверждены [API](https://developer.android.com/reference/android/service/notification/NotificationListenerService); getActiveNotifications не является архивом.
- Раздельность доступа listener и POST_NOTIFICATIONS подтверждена [Settings](https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS) и [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission); OTP redaction подтверждена [Android15](https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction).
- Наличие Restricted Settings для listener подтверждено [Android15 CDD](https://source.android.com/docs/compatibility/15/android-15-cdd#2_2_5_security_model); dangerous/hard-restricted READ_SMS/RECEIVE_SMS — [Manifest.permission](https://developer.android.com/reference/android/Manifest.permission#READ_SMS).
- Ограничение собственными уведомлениями UserNotifications/service extension подтверждено [Apple](https://developer.apple.com/documentation/usernotifications); отдельный accessory-only/EU путь — [AccessoryNotifications](https://developer.apple.com/documentation/accessorynotifications). Ограничение web своим origin/registration — [WHATWG](https://notifications.spec.whatwg.org/#dom-serviceworkerregistration-getnotifications).
- ML Kit не заявляет Russian/Kazakh в [списке языков](https://developers.google.com/ml-kit/vision/text-recognition/v2/languages); ожидание модели перед первым результатом — [Android OCR](https://developers.google.com/ml-kit/vision/text-recognition/v2/android). Tesseract содержит rus/kaz в [официальных данных](https://tesseract-ocr.github.io/tessdoc/Data-Files-in-different-versions.html); это не доказательство точности чеков.
- [StatusBarNotification](https://developer.android.com/reference/android/service/notification/StatusBarNotification) подтверждает key как ID записи уведомления, postTime как время публикации, tag как значение приложения; bank transaction ID из них не следует.
- Открыты официальные Photo Picker/minimize permissions/Keystore/internal storage/Auto Backup страницы; полный повторный разбор всех storage/backup утверждений главы до прерывания не завершён. Он не объявляется выполненным.

## Совместимость и критерии

- N3: основные API/OCR/platform утверждения проверены; точность OCR, конкретные банки/OEM и native работоспособность не доказаны документацией. Полная проверка storage/backup остаётся.
- N4: event → candidate → transaction → optional category согласованы; generic fallback не основание автопроведения без типа/суммы/валюты/даты; неизвестное идёт на разбор. Повтор event идемпотентен, изменение уведомления не стирает ручную правку.
- N4: чек+push связываются по надёжному reference либо решению пользователя; amount/time недостаточны, notification key не bank ID. Raw retention и невозможность повторного parsing после удаления источника согласованы; см. ARC-01.
- N5: Android и цель «все банки» отражены, push/SMS и конкретные форматы остаются вопросами; 100% покрытие и готовая реализация не заявлены. Прежний web-прототип не читает банковские уведомления.

## Замечания с ID и приоритетом

- ARC-01 / P2 / открыто, проектная неоднозначность: durable key/tag/id и namespace account не отделены явно от произвольных чувствительных строк стороннего приложения. Нужны transient raw ID и сохраняемые scoped keyed tokens/внутренние account surrogate; минимизация должна распространяться на метаданные. Это не установленная утечка отсутствующего приложения. Обоим авторам отправлено замечание; изменения ещё не проверены.
- ARC-02 / P2 / открытая проверка: после прерывания завершить чтение оснований permissions/storage/backup и перепроверить исправленную совместимость двух документов. Это пробел доказательств главы, не доказанный дефект продукта.
- SEC-01…SEC-05 авторского документа остаются сценариями будущих испытаний; не обозначаются найденными багами работающего APK.

## Оставшиеся вопросы и следующее действие

Push/SMS, устройства/версии/OEM, пакеты и шаблоны банков, языки/страны чеков, валюты/scale/лимиты, политика датирования, локальный режим/TTL/экспорт и финансовая классификация переводов/возвратов не согласованы полностью.
Сборка, установка SDK/APK/моделей, native/device/OCR/bank/backup испытания, выдача прав и чтение реальных данных не выполнялись. Состояние host toolchain в этом head review не проверялось.
Рекомендация: авторы закрывают ARC-01; глава завершает ARC-02 и фиксирует новые хэши, затем независимые QA и суд по той же версии. APP-005 требует отдельного контракта native/UI и проверки WebView boundary; данный отчёт не утверждает новый APK.
