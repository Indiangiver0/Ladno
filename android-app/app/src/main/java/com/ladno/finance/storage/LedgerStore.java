package com.ladno.finance.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.ImportCandidate;
import com.ladno.finance.core.JsonChecks;
import com.ladno.finance.core.Money;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Single owner of the private ledger. All public reads and writes share this monitor;
 * state additionally uses a DB transaction for one records/imports/summary snapshot.
 * SQLite commit is the success boundary. No raw capture payload is stored.
 */
public final class LedgerStore implements StorageApi {
    static final int SCHEMA_VERSION = 2;
    private static volatile LedgerStore instance;
    private final Database helper;
    private LedgerStore(Context context) { helper = new Database(context.getApplicationContext()); }
    public static LedgerStore get(Context context) {
        if (instance == null) {
            synchronized (LedgerStore.class) {
                if (instance == null) instance = new LedgerStore(context);
            }
        }
        return instance;
    }
    static final class Database extends SQLiteOpenHelper {
        private final Context context;
        Database(Context context) {
            this(context, "ledger.db");
        }
        // A named database lets instrumentation exercise the real migration in isolation.
        Database(Context context, String name) {
            super(context, name, null, SCHEMA_VERSION);
            this.context = context;
            setWriteAheadLoggingEnabled(true);
        }
        @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
        @Override public void onCreate(SQLiteDatabase db) {
            // Explicit delimiters also keep CREATE TRIGGER bodies intact.
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    context.getAssets().open("db/schema.sql"), StandardCharsets.UTF_8))) {
                StringBuilder statement = new StringBuilder();
                String line;
                while ((line = input.readLine()) != null) {
                    if (line.equals("-- @statement")) {
                        runStatement(db, statement);
                        statement.setLength(0);
                    } else if (!line.trim().startsWith("--")) statement.append(line).append('\n');
                }
                runStatement(db, statement);
                StarterCategories.insertMissing(db);
            } catch (IOException ex) {
                throw new ContractException("STORAGE_FAILED", null, "Не удалось создать хранилище.");
            }
        }
        private void runStatement(SQLiteDatabase db, StringBuilder sql) {
            if (!sql.toString().trim().isEmpty()) db.execSQL(sql.toString());
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // SQLiteOpenHelper wraps this upgrade in a transaction. The existing ledger,
            // revisions, links and source tombstones are never recreated or rewritten.
            if (oldVersion == 1 && newVersion == SCHEMA_VERSION) {
                StarterCategories.insertMissing(db);
                return;
            }
            throw new ContractException("STORAGE_FAILED", null, "Версия хранилища не поддерживается.");
        }
        @Override public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            throw new ContractException("STORAGE_FAILED", null, "Версия хранилища не поддерживается.");
        }
    }
    private interface Mutation { JSONObject run(SQLiteDatabase db); }
    private JSONObject atomic(Mutation mutation) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            JSONObject result = mutation.run(db);
            db.setTransactionSuccessful();
            return result;
        } finally { db.endTransaction(); }
    }
    @Override public synchronized JSONObject execute(String action, JSONObject payload) {
        if (!Arrays.asList("settings", "saveTransaction", "deleteTransaction", "saveCategory", "deleteCategory", "saveSources", "reviewCandidate").contains(action)) {
            throw new ContractException("UNKNOWN_ACTION", null, "Действие не поддерживается.");
        }
        return atomic(db -> {
            if (!action.equals("settings")) requireSetup(db);
            switch (action) {
                case "settings": return saveSettings(db, payload);
                case "saveTransaction": return saveTransaction(db, payload);
                case "deleteTransaction": return deleteTransaction(db, payload);
                case "saveCategory": return saveCategory(db, payload);
                case "deleteCategory": return deleteCategory(db, payload);
                case "saveSources": return saveSources(db, payload);
                case "reviewCandidate": return review(db, payload);
                default: throw new ContractException("UNKNOWN_ACTION", null, "Действие не поддерживается.");
            }
        });
    }
    @Override public synchronized JSONObject state(String requestedMonth, String requestedCurrency) {
        SQLiteDatabase db = helper.getReadableDatabase();
        db.beginTransactionNonExclusive();
        try {
            JSONObject settings = settings(db);
            boolean initialized = (Boolean) JsonChecks.value(settings, "initialized");
            ZoneId zone = initialized ? Money.zone(JsonChecks.string(settings, "zoneId", 100)) : ZoneId.systemDefault();
            YearMonth month = requestedMonth == null ? YearMonth.now(zone) : Money.month(requestedMonth);
            if (requestedCurrency != null) Money.scale(requestedCurrency);
            String currency = initialized ? requestedCurrency == null ? JsonChecks.string(settings, "primaryCurrency", 3) : requestedCurrency : null;
            JSONObject result = new JSONObject();
            JsonChecks.put(result, "schemaVersion", SCHEMA_VERSION);
            JsonChecks.put(result, "settings", settings);
            JsonChecks.put(result, "currencies", Money.currencies());
            JsonChecks.put(result, "selection", obj("month", month.toString(), "currency", currency));
            JSONArray records = new JSONArray();
            BigInteger income = BigInteger.ZERO, expense = BigInteger.ZERO;
            if (currency != null) {
                // Inclusive last ISO day is equivalent to [first,next first), and keeps
                // lexical TEXT ordering correct for 9999-12 (next year is +10000).
                try (Cursor cursor = db.rawQuery("SELECT * FROM transactions WHERE deleted=0 AND currency=? AND operation_date>=? AND operation_date<=? ORDER BY operation_date DESC,created_at DESC,id",
                        new String[]{currency, month.atDay(1).toString(), month.atEndOfMonth().toString()})) {
                    while (cursor.moveToNext()) {
                        JSONObject record = record(cursor);
                        records.put(record);
                        Money money = Money.parseCanonical(text(cursor, "amount"), currency, integer(cursor, "scale"));
                        if (text(cursor, "kind").equals("income")) income = income.add(money.minor);
                        else expense = expense.add(money.minor);
                    }
                }
            }
            JsonChecks.put(result, "records", records);
            JSONArray categories = new JSONArray();
            try (Cursor cursor = db.rawQuery("SELECT * FROM categories WHERE deleted=0 ORDER BY name COLLATE NOCASE,id", null)) {
                while (cursor.moveToNext()) categories.put(category(cursor));
            }
            JsonChecks.put(result, "categories", categories);
            JSONArray imports = new JSONArray();
            try (Cursor cursor = db.rawQuery("SELECT id FROM import_candidates WHERE status='needs_review' ORDER BY received_at DESC,id", null)) {
                while (cursor.moveToNext()) imports.put(importDto(db, text(cursor, "id")));
            }
            JsonChecks.put(result, "imports", imports);
            JsonChecks.put(result, "sources", sources(db));
            JSONObject summary = null;
            if (currency != null) {
                int scale = Money.scale(currency);
                summary = obj("month", month.toString(), "currency", currency, "scale", scale,
                        "income", Money.formatMinor(income, scale), "expense", Money.formatMinor(expense, scale),
                        "net", Money.formatMinor(income.subtract(expense), scale));
            }
            JsonChecks.put(result, "summary", summary);
            db.setTransactionSuccessful();
            return result;
        } finally { db.endTransaction(); }
    }
    private JSONObject settings(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT * FROM settings WHERE singleton=1", null)) {
            if (!cursor.moveToFirst()) throw storageFailure();
            return obj("initialized", integer(cursor, "initialized") == 1,
                    "primaryCurrency", text(cursor, "primary_currency"), "zoneId", text(cursor, "zone_id"),
                    "datePolicy", text(cursor, "date_policy"));
        }
    }
    private boolean initialized(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT initialized FROM settings WHERE singleton=1", null)) {
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }
    private void requireSetup(SQLiteDatabase db) {
        if (!initialized(db)) throw new ContractException("SETUP_REQUIRED", null, "Сначала выберите валюту и правило даты.");
    }
    private JSONObject saveSettings(SQLiteDatabase db, JSONObject payload) {
        JsonChecks.keys(payload, "primaryCurrency", "zoneId", "datePolicy");
        String currency = JsonChecks.string(payload, "primaryCurrency", 3); Money.scale(currency);
        String zone = JsonChecks.string(payload, "zoneId", 100); Money.zone(zone);
        String policy = JsonChecks.choice(payload, "datePolicy", "POST_TIME_IN_ZONE");
        ContentValues value = values("initialized", 1, "primary_currency", currency, "zone_id", zone, "date_policy", policy);
        if (db.update("settings", value, "singleton=1", null) != 1) throw storageFailure();
        return obj("settings", settings(db));
    }
    private static long revision(JSONObject payload, boolean creating) {
        if (creating) {
            if (!JsonChecks.isNull(payload, "expectedRevision")) throw JsonChecks.invalid("expectedRevision");
            return 0;
        }
        long revision = JsonChecks.integer(payload, "expectedRevision");
        if (revision < 1 || revision >= Long.MAX_VALUE) throw JsonChecks.invalid("expectedRevision");
        return revision;
    }
    private static final class TransactionInput {
        final String kind, date, categoryId, note;
        final Money money;
        TransactionInput(JSONObject payload) {
            kind = JsonChecks.choice(payload, "kind", "income", "expense");
            date = Money.date(JsonChecks.string(payload, "date", 10));
            String currency = JsonChecks.string(payload, "currency", 3);
            money = Money.parseManual(JsonChecks.string(payload, "amount", 64), currency);
            categoryId = JsonChecks.uuid(payload, "categoryId", true);
            note = JsonChecks.text(JsonChecks.string(payload, "note", 2000), 500, false, "note");
        }
    }
    private void checkCategory(SQLiteDatabase db, String id, String kind) {
        if (id == null) return;
        try (Cursor cursor = db.rawQuery("SELECT kind FROM categories WHERE id=? AND deleted=0", new String[]{id})) {
            if (!cursor.moveToFirst() || !kind.equals(cursor.getString(0))) {
                throw new ContractException("REFERENCE_CONFLICT", "categoryId", "Категория недоступна для этой операции.");
            }
        }
    }
    private JSONObject saveTransaction(SQLiteDatabase db, JSONObject payload) {
        JsonChecks.keys(payload, "id", "expectedRevision", "kind", "date", "amount", "currency", "categoryId", "note");
        String id = JsonChecks.uuid(payload, "id", true);
        long revision = revision(payload, id == null);
        TransactionInput input = new TransactionInput(payload);
        checkCategory(db, input.categoryId, input.kind);
        if (id == null) id = insertTransaction(db, input, "manual");
        else {
            ContentValues value = transactionValues(input);
            value.put("revision", revision + 1);
            value.put("updated_at", now());
            if (db.update("transactions", value, "id=? AND revision=? AND deleted=0", new String[]{id, Long.toString(revision)}) != 1) throw conflict();
        }
        return obj("record", loadRecord(db, id, false));
    }
    private static ContentValues transactionValues(TransactionInput input) {
        return values("kind", input.kind, "operation_date", input.date, "amount", input.money.amount,
                "currency", input.money.currency, "scale", input.money.scale, "category_id", input.categoryId, "note", input.note);
    }
    private String insertTransaction(SQLiteDatabase db, TransactionInput input, String source) {
        checkCategory(db, input.categoryId, input.kind);
        String id = uuid(), now = now();
        ContentValues value = transactionValues(input);
        value.put("id", id); value.put("revision", 1); value.put("source", source);
        value.put("created_at", now); value.put("updated_at", now); value.put("deleted", 0);
        db.insertOrThrow("transactions", null, value);
        return id;
    }
    private JSONObject deleteTransaction(SQLiteDatabase db, JSONObject payload) {
        JsonChecks.keys(payload, "id", "expectedRevision");
        String id = JsonChecks.uuid(payload, "id", false);
        long revision = revision(payload, false);
        if (db.update("transactions", values("deleted", 1, "revision", revision + 1, "updated_at", now()),
                "id=? AND revision=? AND deleted=0", new String[]{id, Long.toString(revision)}) != 1) throw conflict();
        return obj("id", id, "deleted", true);
    }
    private JSONObject saveCategory(SQLiteDatabase db, JSONObject payload) {
        JsonChecks.keys(payload, "id", "expectedRevision", "name", "kind");
        String id = JsonChecks.uuid(payload, "id", true);
        long revision = revision(payload, id == null);
        String name = JsonChecks.text(JsonChecks.string(payload, "name", 320), 80, true, "name");
        String kind = JsonChecks.choice(payload, "kind", "income", "expense");
        if (id == null) {
            id = uuid();
            db.insertOrThrow("categories", null, values("id", id, "revision", 1, "name", name, "kind", kind, "deleted", 0));
        } else {
            try (Cursor cursor = db.rawQuery("SELECT kind,revision FROM categories WHERE id=? AND deleted=0", new String[]{id})) {
                if (!cursor.moveToFirst() || cursor.getLong(1) != revision) throw conflict();
                if (!kind.equals(cursor.getString(0)) && exists(db, "SELECT 1 FROM transactions WHERE category_id=? LIMIT 1", id)) {
                    throw new ContractException("REFERENCE_CONFLICT", "kind", "Вид используемой категории изменить нельзя.");
                }
            }
            if (db.update("categories", values("revision", revision + 1, "name", name, "kind", kind),
                    "id=? AND revision=? AND deleted=0", new String[]{id, Long.toString(revision)}) != 1) throw conflict();
        }
        try (Cursor cursor = db.rawQuery("SELECT * FROM categories WHERE id=?", new String[]{id})) {
            if (!cursor.moveToFirst()) throw storageFailure();
            return obj("category", category(cursor));
        }
    }
    private JSONObject deleteCategory(SQLiteDatabase db, JSONObject payload) {
        JsonChecks.keys(payload, "id", "expectedRevision");
        String id = JsonChecks.uuid(payload, "id", false);
        long revision = revision(payload, false);
        if (!exists(db, "SELECT 1 FROM categories WHERE id=? AND revision=? AND deleted=0", id, Long.toString(revision))) throw conflict();
        // Includes tombstones: no dangling category reference may remain after deletion.
        db.execSQL("UPDATE transactions SET category_id=NULL,revision=revision+1,updated_at=? WHERE category_id=?", new Object[]{now(), id});
        if (db.update("categories", values("deleted", 1, "revision", revision + 1), "id=? AND revision=? AND deleted=0",
                new String[]{id, Long.toString(revision)}) != 1) throw conflict();
        return obj("id", id, "deleted", true);
    }
    private JSONArray sources(SQLiteDatabase db) {
        JSONArray sources = new JSONArray();
        try (Cursor cursor = db.rawQuery("SELECT package_name FROM selected_sources ORDER BY package_name", null)) {
            while (cursor.moveToNext()) sources.put(cursor.getString(0));
        }
        return sources;
    }
    private JSONObject saveSources(SQLiteDatabase db, JSONObject payload) {
        JsonChecks.keys(payload, "packages");
        JSONArray input = JsonChecks.array(payload, "packages", 1000);
        Set<String> selected = new LinkedHashSet<>();
        for (int i = 0; i < input.length(); i++) {
            Object value = input.opt(i);
            if (!(value instanceof String)) throw JsonChecks.invalid("packages");
            selected.add(IngestSource.packageName((String) value));
        }
        // Package availability is verified by native before entering this API.
        db.delete("selected_sources", null, null);
        for (String packageName : selected) db.insertOrThrow("selected_sources", null, values("package_name", packageName));
        return obj("sources", sources(db));
    }
    @Override public synchronized JSONObject ingestCandidate(JSONObject rawSource, ImportCandidate input) {
        IngestSource source = IngestSource.fromJson(rawSource);
        if (input == null) throw JsonChecks.invalid("candidate");
        ImportCandidate candidate = ImportCandidate.fromJson(input.toJson());
        if (candidate.paymentReference != null) throw JsonChecks.invalid("paymentReference");
        return atomic(db -> ingest(db, source, candidate));
    }
    private JSONObject ingest(SQLiteDatabase db, IngestSource source, ImportCandidate candidate) {
        if (source.kind.equals("notification") && !exists(db, "SELECT 1 FROM selected_sources WHERE package_name=?", source.packageName)) {
            throw new ContractException("VALIDATION", "source", "Источник не выбран.");
        }
        String provider = source.packageName == null ? "" : source.packageName;
        if (source.hasIdentity()) {
            try (Cursor cursor = db.rawQuery("SELECT candidate_id FROM source_observations WHERE source_kind=? AND provider_package=? AND source_key_token=? AND revision_token=?",
                    new String[]{source.kind, provider, source.sourceKeyToken, source.revisionToken})) {
                if (cursor.moveToFirst()) return ingestResult(db, cursor.getString(0), "duplicate");
            }
        } else {
            try (Cursor cursor = db.rawQuery("SELECT candidate_id FROM source_observations WHERE source_kind=? AND provider_package=? AND source_key_token IS NULL AND revision_token=?",
                    new String[]{source.kind, provider, source.revisionToken})) {
                if (cursor.moveToFirst()) return ingestResult(db, cursor.getString(0), "duplicate");
            }
        }
        String id = uuid();
        ContentValues value = values("id", id, "revision", 1, "status", "needs_review", "source", source.kind,
                "source_label", source.label, "received_at", source.capturedAt, "kind", candidate.kind,
                "amount", candidate.amount, "currency", candidate.currency,
                "scale", candidate.currency == null ? null : Money.scale(candidate.currency),
                "operation_date", candidate.date, "date_origin", candidate.dateOrigin, "note", candidate.note,
                "confidence", candidate.confidence, "transaction_id", null);
        db.insertOrThrow("import_candidates", null, value);
        db.insertOrThrow("source_observations", null, values("id", uuid(), "candidate_id", id,
                "source_kind", source.kind, "event_id", source.eventId, "provider_package", provider,
                "source_key_token", source.sourceKeyToken, "revision_token", source.revisionToken,
                "account_surrogate", source.accountSurrogate, "payment_ref_token", source.paymentRefToken,
                "captured_at", source.capturedAt, "post_time", source.postTime));
        List<String> reasons = new ArrayList<>(candidate.reasons);
        boolean setup = initialized(db);
        if (!setup) addReason(reasons, "SETUP_REQUIRED");
        if (!source.hasIdentity()) addReason(reasons, "IDENTITY_UNCERTAIN");
        boolean complete = candidate.kind != null && candidate.amount != null && candidate.currency != null && candidate.date != null;
        if (!complete) addReason(reasons, "LOW_CONFIDENCE");
        boolean high = candidate.confidence.equals("high") && reasons.isEmpty();
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        if (source.kind.equals("notification") && source.hasIdentity() && !source.hasStrongReference()
                && exists(db, "SELECT 1 FROM source_observations WHERE source_kind=? AND provider_package=? AND source_key_token=? AND revision_token!=? LIMIT 1",
                source.kind, provider, source.sourceKeyToken, source.revisionToken)) {
            // A reused OS notification key is not proof of another purchase. Preserve
            // the new observation and let the user distinguish an update from a new one.
            addReason(reasons, "IDENTITY_UNCERTAIN");
            try (Cursor cursor = db.rawQuery("SELECT DISTINCT c.transaction_id FROM source_observations s JOIN import_candidates c ON c.id=s.candidate_id JOIN transactions t ON t.id=c.transaction_id WHERE s.source_kind=? AND s.provider_package=? AND s.source_key_token=? AND s.revision_token!=? AND t.deleted=0",
                    new String[]{source.kind, provider, source.sourceKeyToken, source.revisionToken})) {
                while (cursor.moveToNext()) matches.add(cursor.getString(0));
            }
        }
        String linkedId = null;
        if (source.hasStrongReference()) {
            LinkedHashSet<String> strongIds = new LinkedHashSet<>();
            try (Cursor cursor = db.rawQuery("SELECT DISTINCT c.transaction_id FROM source_observations s JOIN import_candidates c ON c.id=s.candidate_id WHERE s.provider_package=? AND s.account_surrogate=? AND s.payment_ref_token=? AND c.transaction_id IS NOT NULL",
                    new String[]{provider, source.accountSurrogate, source.paymentRefToken})) {
                while (cursor.moveToNext()) strongIds.add(cursor.getString(0));
            }
            if (strongIds.size() == 1) {
                String target = strongIds.iterator().next();
                JSONObject record = loadRecord(db, target, true);
                if (record == null) addReason(reasons, "REFERENCE_CONFLICT");
                else if (consistent(candidate, record) && high && complete) linkedId = target;
                else { matches.add(target); addReason(reasons, "REFERENCE_CONFLICT"); }
            } else if (strongIds.size() > 1) {
                addReason(reasons, "REFERENCE_CONFLICT");
                for (String target : strongIds) if (loadRecord(db, target, true) != null) matches.add(target);
            }
        }
        String outcome;
        if (linkedId != null) {
            resolve(db, id, 1, "linked", linkedId);
            outcome = "linked";
        } else {
            if (complete) {
                for (String target : weakMatches(db, candidate.kind, candidate.amount, candidate.currency, candidate.date)) {
                    // A different trusted reference in the same account/provider namespace
                    // proves distinct purchases; mere equal amount/time cannot override it.
                    boolean provenDistinct = source.hasStrongReference() && exists(db,
                            "SELECT 1 FROM source_observations s JOIN import_candidates c ON c.id=s.candidate_id WHERE c.transaction_id=? AND s.provider_package=? AND s.account_surrogate=? AND s.payment_ref_token IS NOT NULL AND s.payment_ref_token!=? LIMIT 1",
                            target, provider, source.accountSurrogate, source.paymentRefToken);
                    if (!provenDistinct) matches.add(target);
                }
            }
            if (!matches.isEmpty()) addReason(reasons, "POSSIBLE_DUPLICATE");
            if (complete && setup && source.hasIdentity() && reasons.isEmpty() && candidate.confidence.equals("high")) {
                TransactionInput transaction = new TransactionInput(obj("kind", candidate.kind, "date", candidate.date,
                        "amount", candidate.amount, "currency", candidate.currency, "categoryId", null, "note", candidate.note));
                String recordId = insertTransaction(db, transaction, source.kind);
                resolve(db, id, 1, "posted", recordId);
                outcome = "posted";
            } else outcome = "review";
        }
        putReasons(db, id, reasons);
        for (String target : matches) db.insertOrThrow("candidate_matches", null, values("candidate_id", id, "transaction_id", target));
        return ingestResult(db, id, outcome);
    }
    private static boolean consistent(ImportCandidate candidate, JSONObject record) {
        return candidate.kind != null && candidate.kind.equals(JsonChecks.string(record, "kind", 7))
                && candidate.amount != null && candidate.amount.equals(JsonChecks.string(record, "amount", 64))
                && candidate.currency != null && candidate.currency.equals(JsonChecks.string(record, "currency", 3))
                && candidate.date != null && candidate.date.equals(JsonChecks.string(record, "date", 10));
    }
    private Set<String> weakMatches(SQLiteDatabase db, String kind, String amount, String currency, String date) {
        // Date-neighbour window is conservative: a weak match only asks the user, never merges.
        LocalDate day = LocalDate.parse(date);
        Set<String> matches = new LinkedHashSet<>();
        String previous = day.getYear() == 1 && day.getDayOfYear() == 1 ? date : day.minusDays(1).toString();
        String next = day.getYear() == 9999 && day.getDayOfYear() == 365 ? date : day.plusDays(1).toString();
        try (Cursor cursor = db.rawQuery("SELECT id FROM transactions WHERE deleted=0 AND kind=? AND amount=? AND currency=? AND operation_date IN (?,?,?) ORDER BY id",
                new String[]{kind, amount, currency, previous, date, next})) {
            while (cursor.moveToNext()) matches.add(cursor.getString(0));
        }
        return matches;
    }
    private JSONObject review(SQLiteDatabase db, JSONObject payload) {
        String decision = JsonChecks.choice(payload, "decision", "post", "link", "ignore");
        if (decision.equals("post")) JsonChecks.keys(payload, "id", "expectedRevision", "decision", "transaction", "confirmDistinct");
        else if (decision.equals("link")) JsonChecks.keys(payload, "id", "expectedRevision", "decision", "transactionId");
        else JsonChecks.keys(payload, "id", "expectedRevision", "decision");
        String id = JsonChecks.uuid(payload, "id", false);
        long revision = revision(payload, false);
        if (!exists(db, "SELECT 1 FROM import_candidates WHERE id=? AND revision=? AND status='needs_review'", id, Long.toString(revision))) throw conflict();
        String transactionId = null;
        if (decision.equals("post")) {
            JSONObject transaction = JsonChecks.object(payload, "transaction");
            JsonChecks.keys(transaction, "kind", "date", "amount", "currency", "categoryId", "note");
            TransactionInput input = new TransactionInput(transaction);
            boolean confirmDistinct = JsonChecks.bool(payload, "confirmDistinct");
            boolean possible = exists(db, "SELECT 1 FROM candidate_matches WHERE candidate_id=? LIMIT 1", id)
                    || !weakMatches(db, input.kind, input.money.amount, input.money.currency, input.date).isEmpty();
            if (possible && !confirmDistinct) throw new ContractException("REFERENCE_CONFLICT", "confirmDistinct", "Проверьте похожие операции и подтвердите отдельную покупку.");
            String source;
            try (Cursor cursor = db.rawQuery("SELECT source FROM import_candidates WHERE id=?", new String[]{id})) {
                if (!cursor.moveToFirst()) throw conflict(); source = cursor.getString(0);
            }
            transactionId = insertTransaction(db, input, source);
            ContentValues update = values("kind", input.kind, "amount", input.money.amount, "currency", input.money.currency,
                    "scale", input.money.scale, "operation_date", input.date, "date_origin", "user", "note", input.note);
            if (db.update("import_candidates", update, "id=?", new String[]{id}) != 1) throw conflict();
        } else if (decision.equals("link")) {
            transactionId = JsonChecks.uuid(payload, "transactionId", false);
            if (loadRecord(db, transactionId, true) == null) throw new ContractException("REFERENCE_CONFLICT", "transactionId", "Операция недоступна для связи.");
        }
        resolve(db, id, revision, decision.equals("post") ? "posted" : decision.equals("link") ? "linked" : "ignored", transactionId);
        return obj("candidate", importDto(db, id), "record", transactionId == null ? null : loadRecord(db, transactionId, false));
    }
    private void resolve(SQLiteDatabase db, String id, long revision, String status, String transactionId) {
        if (db.update("import_candidates", values("status", status, "transaction_id", transactionId, "revision", revision + 1),
                "id=? AND revision=? AND status='needs_review'", new String[]{id, Long.toString(revision)}) != 1) throw conflict();
    }
    private void putReasons(SQLiteDatabase db, String id, List<String> reasons) {
        int position = 0;
        for (String reason : new LinkedHashSet<>(reasons)) {
            db.insertOrThrow("candidate_reasons", null, values("candidate_id", id, "position", position++, "reason", reason));
        }
    }
    private JSONObject ingestResult(SQLiteDatabase db, String id, String outcome) {
        JSONObject candidate = importDto(db, id);
        String transactionId = JsonChecks.nullableString(candidate, "transactionId", 36);
        return obj("outcome", outcome, "candidate", candidate,
                "record", transactionId == null ? null : loadRecord(db, transactionId, true));
    }
    private JSONObject importDto(SQLiteDatabase db, String id) {
        try (Cursor cursor = db.rawQuery("SELECT * FROM import_candidates WHERE id=?", new String[]{id})) {
            if (!cursor.moveToFirst()) throw storageFailure();
            JSONArray reasons = new JSONArray(), matches = new JSONArray();
            try (Cursor reason = db.rawQuery("SELECT reason FROM candidate_reasons WHERE candidate_id=? ORDER BY position", new String[]{id})) {
                while (reason.moveToNext()) reasons.put(reason.getString(0));
            }
            try (Cursor match = db.rawQuery("SELECT transaction_id FROM candidate_matches WHERE candidate_id=? ORDER BY transaction_id", new String[]{id})) {
                while (match.moveToNext()) matches.put(match.getString(0));
            }
            return obj("id", text(cursor, "id"), "revision", longValue(cursor, "revision"), "status", text(cursor, "status"),
                    "source", text(cursor, "source"), "sourceLabel", text(cursor, "source_label"), "receivedAt", text(cursor, "received_at"),
                    "kind", text(cursor, "kind"), "amount", text(cursor, "amount"), "currency", text(cursor, "currency"),
                    "scale", nullableInt(cursor, "scale"), "date", text(cursor, "operation_date"), "dateOrigin", text(cursor, "date_origin"),
                    "note", text(cursor, "note"), "reasons", reasons, "confidence", text(cursor, "confidence"),
                    "transactionId", text(cursor, "transaction_id"), "possibleMatchIds", matches);
        }
    }
    private JSONObject loadRecord(SQLiteDatabase db, String id, boolean nullable) {
        try (Cursor cursor = db.rawQuery("SELECT * FROM transactions WHERE id=? AND deleted=0", new String[]{id})) {
            if (!cursor.moveToFirst()) { if (nullable) return null; throw conflict(); }
            return record(cursor);
        }
    }
    private JSONObject record(Cursor cursor) {
        return obj("id", text(cursor, "id"), "revision", longValue(cursor, "revision"), "kind", text(cursor, "kind"),
                "date", text(cursor, "operation_date"), "amount", text(cursor, "amount"), "currency", text(cursor, "currency"),
                "scale", integer(cursor, "scale"), "categoryId", text(cursor, "category_id"), "note", text(cursor, "note"),
                "source", text(cursor, "source"), "createdAt", text(cursor, "created_at"), "updatedAt", text(cursor, "updated_at"));
    }
    private JSONObject category(Cursor cursor) {
        return obj("id", text(cursor, "id"), "revision", longValue(cursor, "revision"), "name", text(cursor, "name"), "kind", text(cursor, "kind"));
    }
    private static String text(Cursor cursor, String column) { int index = cursor.getColumnIndexOrThrow(column); return cursor.isNull(index) ? null : cursor.getString(index); }
    private static int integer(Cursor cursor, String column) { return cursor.getInt(cursor.getColumnIndexOrThrow(column)); }
    private static long longValue(Cursor cursor, String column) { return cursor.getLong(cursor.getColumnIndexOrThrow(column)); }
    private static Integer nullableInt(Cursor cursor, String column) { int index = cursor.getColumnIndexOrThrow(column); return cursor.isNull(index) ? null : cursor.getInt(index); }
    private static boolean exists(SQLiteDatabase db, String sql, String... args) {
        try (Cursor cursor = db.rawQuery(sql, args)) { return cursor.moveToFirst(); }
    }
    private static ContentValues values(Object... pairs) {
        ContentValues value = new ContentValues();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = (String) pairs[i]; Object item = pairs[i + 1];
            if (item == null) value.putNull(key);
            else if (item instanceof String) value.put(key, (String) item);
            else if (item instanceof Integer) value.put(key, (Integer) item);
            else if (item instanceof Long) value.put(key, (Long) item);
            else throw storageFailure();
        }
        return value;
    }
    private static JSONObject obj(Object... pairs) {
        JSONObject result = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) JsonChecks.put(result, (String) pairs[i], pairs[i + 1]);
        return result;
    }
    private static void addReason(List<String> reasons, String reason) { if (!reasons.contains(reason)) reasons.add(reason); }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static String now() { return Instant.now().toString(); }
    private static ContractException conflict() { return new ContractException("CONFLICT", "expectedRevision", "Данные изменились. Обновите экран."); }
    private static ContractException storageFailure() { return new ContractException("STORAGE_FAILED", null, "Хранилище недоступно."); }
}
