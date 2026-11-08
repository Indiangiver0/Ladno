package com.ladno.finance.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** One-time schema-2 seed. This must not run on normal state reads or reopen. */
final class StarterCategories {
    private static final String[] EXPENSES = {
        "Продукты", "Кафе и рестораны", "Транспорт", "Дом", "Коммунальные услуги",
        "Здоровье", "Покупки", "Развлечения", "Подписки", "Образование", "Прочее"
    };
    private static final String[] INCOMES = {"Зарплата", "Подработка", "Подарки", "Прочие доходы"};

    private StarterCategories() { }

    static void insertMissing(SQLiteDatabase db) {
        Set<String> names = new HashSet<>();
        Set<String> ids = new HashSet<>();
        // Deleted names count too: upgrading must not undo a user's earlier deletion.
        try (Cursor cursor = db.rawQuery("SELECT id,name,kind FROM categories", null)) {
            while (cursor.moveToNext()) {
                ids.add(cursor.getString(0));
                names.add(key(cursor.getString(2), cursor.getString(1)));
            }
        }
        insert(db, "expense", EXPENSES, names, ids);
        insert(db, "income", INCOMES, names, ids);
    }

    private static void insert(SQLiteDatabase db, String kind, String[] labels, Set<String> names, Set<String> ids) {
        for (String label : labels) {
            String key = key(kind, label);
            String id = UUID.nameUUIDFromBytes(("ladno:starter-category:v1:" + key).getBytes(StandardCharsets.UTF_8)).toString();
            if (names.contains(key) || ids.contains(id)) continue;
            ContentValues values = new ContentValues();
            values.put("id", id); values.put("revision", 1); values.put("name", label);
            values.put("kind", kind); values.put("deleted", 0);
            db.insertOrThrow("categories", null, values);
            names.add(key); ids.add(id);
        }
    }

    private static String key(String kind, String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFKC)
            .replaceAll("[\\s\\p{Zs}]+", " ").trim().toLowerCase(Locale.ROOT);
        return kind + ":" + normalized;
    }
}
