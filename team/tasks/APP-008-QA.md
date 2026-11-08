# APP-008-QA: независимая проверка обновления

- Родитель APP-008; статус qa; отдел qa; глава будет назначен реальным вызовом.
- Расчётный специалист, не автор: новые qa/app008-calculation/* и новый Android independent test class при необходимости. Проверить receipt supported layouts и refusals, no bank regression; seed/migration fresh/populated v1/reopen/tombstones; exact chart fixture model. Не менять продукт и старые QA свидетельства. Ожидания по запросу/spec/независимым числам, не копия реализации.
- Сценарный специалист, не автор: qa/app008-scenario/*; browser CUA сценарии inline category сохранение черновика/ошибки,5nav/charts/filters/zero/legend/mobile, убрать pending receipt не создаёт расход; подпись/version/asset continuity APK. Browser preview не доказывает Android UI/camera. Native OCR по синтетическим изображениям может выполняться root на отдельном AVD, не на физическом телефоне.
- Root: build/unit/lint + выполнение Android native API test commands, без UI-автоматизации через adb/CLI. Эмулятор строго Ladno_QA_API30/emulator-5580. Ни чтения данных, ни pm clear/uninstall/grants на телефоне пользователя.
- Отчёт должен различать свои запуски и прочитанные результаты root; фиксировать hashes/ошибки/ограничения. Глава отдельно проверяет обоих, затем общий суд APP-008.
