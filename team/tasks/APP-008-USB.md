# APP-008-USB: восстановление подключения для обновления

- Родитель APP-008, отдельная операционная доставка уже проверенного APK. Статус accepted (операционная доставка, не полная A1); root владеет карточкой/командами host, architecture/security ведёт диагностику.
- Пользователь сообщил «Окна сейчас нет» на вопрос о RSA-отладке. Ранее телефон был unauthorized; текущий adb devices -l пуст. Не считать это отзывом разрешения на установку; отсутствует подтверждённая OS-связь.
- Глава /root/usb_recovery_lead, один security-engineer от главы. Владелец нового отчёта team/reports/APP-008-usb-recovery.md — глава; специалист read-only.
- Критерии: отличить физическую USB/PnP видимость, ADB transport, RSA авторизацию; не сбрасывать ключи/доверие, не отключать защиты, не устанавливать драйверы/публиковать без причины; не читать финансовые данные/уведомления. Пользователь подтверждает OS-окно сам.
- После device на прежнем подтверждённом Samsung root проверяет metadata package/model + SHA основного APK f73e04a0202418ee1dc59c1e669a57228988607de9fb1f60bd388b8e4a20beac, устанавливает -r --no-incremental и проверяет versionCode2/versionName0.2.0. Не запускать androidTest, clear/uninstall/grants/UI на телефоне.
- 402 frozen файла, APK, старый вердикт недостаточности полного камерного пути не менять. Доставка может закрыть только USR8-02, не C8-01.
- Фактический результат root: после пользовательского off/on USB debugging и ручного RSA «Разрешить» телефон стал device; mainAPK f73e04… установлен -r --no-incremental, exit0/Success; versionCode1→2 и versionName0.2.0 подтверждены. Отчёт team/reports/APP-008-delivery.md. Глава проверяет свидетельство отдельно; полная A1 остаётся needs_information.

- Проверка главы /root/usb_recovery_lead завершена: свидетельство доставки, APK SHA/size и версии согласованы; USR8-02 закрыт согласно условиям существующего суда. Отчёт SHA e3e547fe01cfd451957c3e0fab53eb8fe4122f76ea3cfcf5e9f37afe9a142646. Root final integrity:402frozenфайла без изменений, вердикт SHA3f3bf29e… неизменен. Полная APP-008 сохраняет needs_information по C8-01.
