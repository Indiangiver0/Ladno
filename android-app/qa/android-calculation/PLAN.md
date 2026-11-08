# APP-006 N1 — native ledger instrumentation

Независимый calculation-test-engineer `/root/android_qa_calculation`. Владение: три `storage/LedgerRuntime*.java` и этот каталог. Production, прежние расчётные QA и frozen108 не изменяются.

Тесты подготовлены, но ещё не скомпилированы и не исполнены. Root собирает androidTest APK и проверяет AVD/serial перед установкой или командами. Только `Ladno_QA_API30`, `emulator-5580`, package `com.ladno.finance`; ни один тест не управляет UI, не выдаёт разрешений и не очищает БД.

Последовательность root:

1. Подтвердить AVD и чистые данные выделенного тестового приложения до первого запуска. Установить app/test APK, записать их SHA, API/ABI/AVD/serial.
2. Отдельная команда `am instrument -w -r -e class com.ladno.finance.storage.LedgerRuntimeWriteTest com.ladno.finance.test/androidx.test.runner.AndroidJUnitRunner`.
3. Сохранить stdout и private `files/qa-ledger-write.json`, `files/qa-ledger-control.json`. Убедиться в успешном завершении. Выполнить force-stop только этого package на проверенном эмуляторе, не очистку app data.
4. Без промежуточных N2/N3 записей отдельная команда для `com.ladno.finance.storage.LedgerRuntimeReadTest`. Сохранить stdout и `files/qa-ledger-read.json`. Отличающиеся PID обязательны; совпавший PID не считается restart.

Контрольный файл хранит generated IDs и фиксированные независимо рассчитанные ожидания, а не копию actual summary. Все проверки вызывают настоящие production `LedgerStore.get/state/execute/ingestCandidate`; SQLiteOpenHelper создаёт и открывает настоящую `ledger.db`. В test code нет mock, reflection, singleton reset, SQL-подмены или удаления живой БД.

Write-проход: setup, canonical/null category; 0.10+0.20 и общий net; USD отдельно; invalid money/date/kind-category без частичных записей; assign/rename/delete категории, CAS; перенос даты между октябрём/ноябрём; above2^53, 25-digit aggregate, годы9999, JPY/KWD; selected source; auto post, strong scoped-reference link, manual edit→exact replay, new revision review; weak match→explicit link; confirmDistinct post; ignore; uncertain post; no-identity review; unselected source rejection; delete→replay/tombstone.

Итоговые ожидания: октябрь KZT income0.30/expense2.30/net−2.00, USD expense7.89 отдельно; ноябрь KZTexpense1.20; сентябрь KZTnet0.04 при значениях выше2^53; августincome1999999999999999999999999.98; июль JPYexpense7 и KWDincome1.200 отдельно; 9999-12 KZTexpense0.01; июнь после удаленияexpense21.23=13.34+7.89. Два pending материала исключены из сумм. Read-проход проверяет эти же ожидания в новом процессе, ID/revision/date/note/category/sources, решения импорта и повтор после tombstone/ignore.

Tokens в N1 — явно синтетические SHA-256 labels допустимого wireформата, не проверка AndroidKeyStore. Настоящие KeyStore/NLS/OCR и системная камера/галерея относятся к другому независимому специалисту. Успешная компиляция тестов сама по себе не является исполнением N1.
