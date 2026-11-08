# APP-007: диагностика блокировки установки, v1

Задача: ограниченная read-only диагностика; дата 2026-11-03. APK 0.1.0/com.ladno.finance, SHA-256 `38c9d4490419eda3685e7187281d5f1ecfb958a95d77c57f7f59633febcfbd5a`.
Владелец этого отчёта: architecture-lead. Production, frozen215 и прежние отчёты не изменялись.

Выполненное: реально запущен один security-engineer `/root/install_architecture_lead/install_security`; получены выводы сообщением, без правок файлов.
Проверка главы отдельным этапом: прочитан результат специалиста; самостоятельно сверены три официальных источника, source manifest и SHA APK. Выводы специалиста подтверждены в указанной области; это не приёмка приложения.

Доказательства — ожидание → факт:
- `Get-FileHash -LiteralPath 'android-app/dist/ladno-0.1.0-debug.apk' -Algorithm SHA256`: прежний артефакт → exit0, полный SHA совпал с указанным выше.
- `Get-Content` и `Select-String` для `android-app/app/src/main/AndroidManifest.xml`: наличие NLS → `.BankNotificationService`, `BIND_NOTIFICATION_LISTENER_SERVICE` и action NotificationListenerService, строки 30–35; INTERNET/SMS/accessibility в этом source manifest отсутствуют.
- [Google Play Protect guidance](https://developers.google.com/android/play-protect/warning-dev-guidance?hl=en), §App blocked to protect your device: соответствие категории → текст предупреждения и сочетание интернет-источника с Notification Listener описаны прямо; защита действует в отдельных рынках.
- [Android device testing](https://developer.android.com/studio/run/device), §Set up a device / Connect using USB: официальный developer workflow → ADB-подключение и запуск на своём устройстве описаны; исключение проверок Play Protect не обещано.
- [Play testing](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en), §Internal testing: официальный тестовый канал → распространение через Play Store; стандартные policy/security reviews могут не проводиться.

Факт из задания chief: скриншот показывает соответствующее предупреждение Play Protect; на фоне мессенджер. Сам скриншот главе повторно не передавался.
Вероятная причина: установка APK из интернет-источника при объявленном Notification Listener. Мессенджер на фоне поддерживает гипотезу, но не устанавливает фактический источник; classifier log Google недоступен.
Предупреждение само по себе не доказывает вредоносность APK. Эта диагностика также не подтверждает отсутствие вредоносного кода.
Нет доказательств, что причина — debug/self-signed подпись или что release/переподпись устранит блокировку.

Варианты, без выполнения действий:
- USB/ADB developer test install на своём телефоне: сохранить нынешнюю функцию уведомлений и проверить фактическую установку; результат и отсутствие Play Protect checks не гарантированы.
- Developer appeal после сверки приложения с guidance: официальный путь пересмотра классификации; успех и сроки неизвестны. Отправка требует отдельной авторизации.
- Google Play internal/closed testing: подготовить официальный тестовый канал; нужны Play Console и загрузка артефакта. Не означает полного security review и не гарантирует отсутствие блокировок; публикация сейчас не выполняется.
- Сборка без NLS: только по явному выбору пользователя с реальным отключением соответствующего импорта уведомлений; не маскировка permission и не гарантия установки.

Замечания: APP007-S01 / P1 — по сообщению пользователя заблокирована установка; индивидуальная причина остаётся гипотезой. APP007-H02 / P2 — AOSP-тесты не проверяли Play Protect.
По прежним evidence APP-006 установке на AOSP Android11 сопутствовали 388 native API assertions PASS; здесь они не повторялись и не расширяются до физического телефона/Play Protect.
Оставшиеся вопросы: модель/ОС телефона, фактический источник APK, возможность USB и результат выбранного способа установки.
Рекомендуемое следующее действие: chief уточняет возможность USB; затем согласует конкретный developer test workflow с пользователем, сохраняя функции и действующие защиты.
Не выполнялись: подключение/ADB к телефону, установка, драйверы, изменение настроек, чтение личных данных, загрузка APK, appeal или публикация; новых QA/суда и полного аудита безопасности в этой диагностике не было.
