# APP-005 — независимая первоначальная позиция адвоката реализации

Задача: оценить A1–A6 и допустимость передачи альфы отдельно от полной приёмки. Автором/главой проверяемой реализации не являюсь; чужие судебные позиции не читал и не согласовывал выводы.
Версия: android-alpha-v1, 108 файлов; manifest digest `87b8a045ac6e104e802cca11e6bbae40cceb5d688e13e681e654a990f4f99864`; APK `38c9d4490419eda3685e7187281d5f1ecfb958a95d77c57f7f59633febcfbd5a`.

## Выполненное и доказательства

- Прочитаны AGENTS.md, PROTOCOL.md, полный профиль роли, [единый пакет](APP-005.md), карточка A1–A6, нормативные материалы и head/QA evidence; адресно просмотрены Money, LedgerStore, parser/native/NLS/OCR участки. Продукт не менялся; Android execution и новые полные аудиты не выполнялись.
- Собственная команда PowerShell: `ConvertFrom-Json` manifest → для каждого из108 путей `Get-FileHash -Algorithm SHA256` → сравнение с manifest; ожидание108 совпадений, факт108 файлов и0 MISMATCH, exit0. Отдельный Get-FileHash APK дал указанный выше SHA.
- Собственная Python stdlib сверка: сортировка manifest.files по path, строки `sha256 + "  " + path + LF`, SHA-256 UTF-8; ожидание manifestSha256, факт `87b8a045…f99864`, match=True, exit0. Сам snapshot-скрипт не запускал и пакет не перезаписывал.
- По [build-result](../../android-app/dist/build-result.json) и [QA главы](../reports/APP-005-qa-review.md): реальные assembleDebug/testDebugUnitTest/lintDebug, 24 JUnit methods без failures/errors, lint0errors/3warnings. Это выполнение начальника, не мой повтор сборки; побитовая воспроизводимость повторной сборкой мной не проверена.
- По [calculation QA](../../android-app/qa/calculation/REPORT.md): реальные production core/JVM211 assertions и host SQLite39 assertions PASS. По [scenario QA](../../android-app/qa/scenario/REPORT.md):17 CUA browser groups,55 read-only APK checks и6 continuity hashes PASS. Число проверок не превращает preview/host DDL в Android integration.

## Обоснованные инженерные решения

- Точные деньги обоснованы требованиями: [Money.java:48–85](../../android-app/app/src/main/java/com/ladno/finance/core/Money.java) использует lexical scale guard и BigInteger minor; [LedgerStore.java:127–138](../../android-app/app/src/main/java/com/ladno/finance/storage/LedgerStore.java) складывает minor отдельно по month/currency. Это сильнее форматирования float; core исполнен, Android aggregate ещё нет.
- Реальный Android слой присутствует: NotificationListenerService, SQLite, TessBaseAPI с eng+rus+kaz и локальные assets/bridge. В [OcrEngine.java:43,101–102](../../android-app/app/src/main/java/com/ladno/finance/OcrEngine.java) есть init/setImage/getUTF8Text; в [BankNotificationService.java:56–66,95](../../android-app/app/src/main/java/com/ladno/finance/BankNotificationService.java) — allowlist до чтения текста и native ingest. Это не старое demo, но присутствие кода не доказывает запуск.
- Автопост не заменён обязательным подтверждением каждого события: [LedgerStore.java:401–406](../../android-app/app/src/main/java/com/ladno/finance/storage/LedgerStore.java) создаёт запись с categoryId=null для полного high-кандидата без причин review. Неоднозначная identity/возможный дубль требует разбора; слабое совпадение не сливает две покупки. Такой компромисс соответствует A3–A5, однако переходы надо исполнить на Android.
- [ReceiptParser.java:12–27](../../android-app/app/src/main/java/com/ladno/finance/core/ReceiptParser.java) требует явную завершённую оплату. QA-CALC01/P1 закрыт независимым JVM повтором для парсера: «К оплате123.45KZT» теперь uncertain/UNSUPPORTED_EVENT с сохранением суммы/даты. Это обоснованное предотвращение ложного расхода; поддержку обычных реальных чеков такой повтор не устанавливает.
- Debug APK для личной установки, отсутствие INTERNET, отдельные базы двух телефонов и отсутствие синхронизации описаны в [README](../../android-app/README.md). Публичный релиз/общий семейный учёт не запрошены; их отсутствие само по себе не дефект A1–A6. Установка на конкретное устройство пока не доказана.

## Матрица A1–A6

- A1: артефакт, identity0.1.0/com.ladno.finance/min26/target36, сборка и сохранённые проверки подтверждены. Сборочный процесс закреплён; фактическая устанавливаемость/старт на телефоне отдельно не проверены.
- A2: частично — UI CRUD/категории и host DDL проверены; native bridge→LedgerStore→restart persistence не исполнены.
- A3: частично — production parser исполнен, NLS/permissions упакованы; delivery/revoke/Keystore, реальные банковские форматы и «все банки» не подтверждены.
- A4: частично — parser finality и комплект моделей подтверждены; camera/gallery→URI→JNI OCR→расход на Android не исполнены.
- A5: частично — core точность/даты, UI строки и DDL доказаны; native aggregate/CAS/replay после edit/delete, receipt+notification и post/link/ignore требуют integration.
- A6: отдельные исполнители/главы и независимый QA по этой версии документированы; судебный обмен и вердикт ещё должны завершиться. Собственная позиция не заменяет судью.

## Замечания с ID и приоритетом

- IA005-01 / P2 / подтверждённый пробел доказательств, не воспроизведённая продуктовая ошибка: A2–A5 не закрыты полностью. Доказательства: [матрица QA](../reports/APP-005-qa-review.md), разделы ограничений обоих QA. Действие: независимый Android-проход пустой установки/setup/CRUD/restart, NLS позитив/негатив/replay/revoke, OCR оплаченного/неясного чека, дубль, review и сохранение ручной правки по тому же APK.
- IA005-02 / P2 / подтверждённое ограничение покрытия: общий parser и выбор источника не доказывают требуемые «все банки»; неизвестные форматы остаются review. Доказательства: [PERSONAL-MVP R5/U5](../../docs/product/PERSONAL-MVP.md), README, calculation QA. Действие: сохранить исходную цель, получить обезличенный корпус/границы форматов и вести проверяемую матрицу покрытия; не объявлять требование выполненным.
- IA005-03 / P2 / подтверждённое ограничение, масштаб не установлен: strict paid marker может отправлять настоящие оплаченные чеки без такого текста в review. Это не доказанная ошибка финансового результата. Действие: прогнать размеченный RU/KZ/EN корпус через настоящий OCR и parser; расширять признаки только с отрицательными unpaid/pending регрессиями, сохраняя обязательный ясный автопост R4.
- Незакрытых подтверждённых P0/P1 в предъявленных выполненных проверках нет; это не утверждение отсутствия дефектов в неисполненных native-сценариях. Исторические BD5-01/02, FE01, SEC06/07 и QA-CALC01 имеют адресные closure evidence в head/QA пакете; их device-ветви не объявляю PASS.

## Оставшиеся вопросы и рекомендуемое действие

Не установлены пригодность на конкретном телефоне, точность реального OCR и фактическое банковское покрытие. Отсутствие телефона/реальных данных объясняет границу проверки, но не закрывает обязательные сценарии.
Рекомендую `insufficient_evidence` для полной функциональной приёмки APP-005. Передачу проверенного файла APK как экспериментальной debug-альфы для личной установки и следующего Android-прохода считаю допустимой отдельно: прямо назвать невыполненные A2–A5/«все банки», отсутствие восстановления/синхронизации и не объявлять всю задачу accepted. Это передача результата этапа, не уменьшение исходного запроса и не обещание работоспособности на телефоне.
