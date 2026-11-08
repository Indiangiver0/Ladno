-- APP-005 schema 1. Exact money stays TEXT. No raw source text or reversible metadata.
-- @statement
CREATE TABLE settings (
 singleton INTEGER PRIMARY KEY CHECK(singleton=1),
 initialized INTEGER NOT NULL CHECK(initialized IN (0,1)),
 primary_currency TEXT, zone_id TEXT, date_policy TEXT,
 CHECK((initialized=0 AND primary_currency IS NULL AND zone_id IS NULL AND date_policy IS NULL)
 OR (initialized=1 AND primary_currency IS NOT NULL AND length(primary_currency)=3 AND zone_id IS NOT NULL AND date_policy IS NOT NULL AND date_policy='POST_TIME_IN_ZONE'))
);
-- @statement
INSERT INTO settings(singleton,initialized) VALUES(1,0);
-- @statement
CREATE TABLE categories (
 id TEXT PRIMARY KEY NOT NULL, revision INTEGER NOT NULL CHECK(typeof(revision)='integer' AND revision>0),
 name TEXT NOT NULL CHECK(length(name) BETWEEN 1 AND 80),
 kind TEXT NOT NULL CHECK(kind IN ('income','expense')),
 deleted INTEGER NOT NULL DEFAULT 0 CHECK(deleted IN (0,1))
);
-- @statement
CREATE TABLE transactions (
 id TEXT PRIMARY KEY NOT NULL, revision INTEGER NOT NULL CHECK(typeof(revision)='integer' AND revision>0),
 kind TEXT NOT NULL CHECK(kind IN ('income','expense')),
 operation_date TEXT NOT NULL CHECK(length(operation_date)=10 AND operation_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
 amount TEXT NOT NULL CHECK(typeof(amount)='text' AND amount NOT GLOB '*[^0-9.]*' AND amount GLOB '*[1-9]*'
 AND ((scale=0 AND instr(amount,'.')=0 AND length(amount) BETWEEN 1 AND 24)
 OR (scale>0 AND instr(amount,'.') BETWEEN 2 AND 25 AND length(amount)-instr(amount,'.')=scale
 AND length(amount)-length(replace(amount,'.',''))=1))
 AND (substr(amount,1,1)!='0' OR amount='0' OR substr(amount,1,2)='0.')),
 currency TEXT NOT NULL CHECK(length(currency)=3 AND currency NOT GLOB '*[^A-Z]*'),
 scale INTEGER NOT NULL CHECK(typeof(scale)='integer' AND scale BETWEEN 0 AND 6),
 category_id TEXT REFERENCES categories(id), note TEXT NOT NULL CHECK(length(note)<=500),
 source TEXT NOT NULL CHECK(source IN ('manual','notification','receipt')),
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
 deleted INTEGER NOT NULL DEFAULT 0 CHECK(deleted IN (0,1)),
 CHECK(CAST(substr(operation_date,1,4) AS INTEGER) BETWEEN 1 AND 9999
 AND CAST(substr(operation_date,6,2) AS INTEGER) BETWEEN 1 AND 12
 AND CAST(substr(operation_date,9,2) AS INTEGER) BETWEEN 1 AND
 CASE CAST(substr(operation_date,6,2) AS INTEGER)
 WHEN 2 THEN 28+(CAST(substr(operation_date,1,4) AS INTEGER)%4=0 AND (CAST(substr(operation_date,1,4) AS INTEGER)%100!=0 OR CAST(substr(operation_date,1,4) AS INTEGER)%400=0))
 WHEN 4 THEN 30 WHEN 6 THEN 30 WHEN 9 THEN 30 WHEN 11 THEN 30 ELSE 31 END)
);
-- @statement
CREATE INDEX transactions_period ON transactions(deleted,currency,operation_date);
-- @statement
CREATE TABLE import_candidates (
 id TEXT PRIMARY KEY NOT NULL, revision INTEGER NOT NULL CHECK(typeof(revision)='integer' AND revision>0),
 status TEXT NOT NULL CHECK(status IN ('needs_review','posted','linked','ignored')),
 source TEXT NOT NULL CHECK(source IN ('notification','receipt')),
 source_label TEXT NOT NULL CHECK(source_label IN ('Чек','Уведомление')),
 received_at TEXT NOT NULL,
 kind TEXT CHECK(kind IS NULL OR kind IN ('income','expense')),
 amount TEXT, currency TEXT, scale INTEGER,
 operation_date TEXT, date_origin TEXT CHECK(date_origin IS NULL OR date_origin IN ('source','post_time','user')),
 note TEXT NOT NULL CHECK(length(note)<=500),
 confidence TEXT NOT NULL CHECK(confidence IN ('high','uncertain','unavailable')),
 transaction_id TEXT REFERENCES transactions(id),
 CHECK((currency IS NULL AND scale IS NULL) OR (currency IS NOT NULL AND length(currency)=3 AND currency NOT GLOB '*[^A-Z]*' AND scale IS NOT NULL AND typeof(scale)='integer' AND scale BETWEEN 0 AND 6)),
 CHECK((amount IS NULL) OR (typeof(amount)='text' AND currency IS NOT NULL AND scale IS NOT NULL AND scale BETWEEN 0 AND 6
 AND amount NOT GLOB '*[^0-9.]*' AND amount GLOB '*[1-9]*'
 AND ((scale=0 AND instr(amount,'.')=0 AND length(amount) BETWEEN 1 AND 24)
 OR (scale>0 AND instr(amount,'.') BETWEEN 2 AND 25 AND length(amount)-instr(amount,'.')=scale
 AND length(amount)-length(replace(amount,'.',''))=1))
 AND (substr(amount,1,1)!='0' OR substr(amount,1,2)='0.'))),
 CHECK((operation_date IS NULL AND date_origin IS NULL) OR (operation_date IS NOT NULL AND date_origin IS NOT NULL)),
 CHECK(operation_date IS NULL OR (length(operation_date)=10 AND operation_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
 AND CAST(substr(operation_date,1,4) AS INTEGER) BETWEEN 1 AND 9999
 AND CAST(substr(operation_date,6,2) AS INTEGER) BETWEEN 1 AND 12
 AND CAST(substr(operation_date,9,2) AS INTEGER) BETWEEN 1 AND
 CASE CAST(substr(operation_date,6,2) AS INTEGER)
 WHEN 2 THEN 28+(CAST(substr(operation_date,1,4) AS INTEGER)%4=0 AND (CAST(substr(operation_date,1,4) AS INTEGER)%100!=0 OR CAST(substr(operation_date,1,4) AS INTEGER)%400=0))
 WHEN 4 THEN 30 WHEN 6 THEN 30 WHEN 9 THEN 30 WHEN 11 THEN 30 ELSE 31 END)),
 CHECK((status IN ('posted','linked') AND transaction_id IS NOT NULL) OR (status IN ('needs_review','ignored') AND transaction_id IS NULL))
);
-- @statement
CREATE TABLE source_observations (
 id TEXT PRIMARY KEY NOT NULL,
 candidate_id TEXT NOT NULL UNIQUE REFERENCES import_candidates(id),
 source_kind TEXT NOT NULL CHECK(source_kind IN ('notification','receipt')),
 event_id TEXT NOT NULL,
 provider_package TEXT NOT NULL,
 source_key_token TEXT CHECK(source_key_token IS NULL OR (length(source_key_token)=64 AND source_key_token NOT GLOB '*[^0-9a-f]*')),
 revision_token TEXT NOT NULL CHECK(length(revision_token)=64 AND revision_token NOT GLOB '*[^0-9a-f]*'),
 account_surrogate TEXT CHECK(account_surrogate IS NULL OR (length(account_surrogate)=64 AND account_surrogate NOT GLOB '*[^0-9a-f]*')),
 payment_ref_token TEXT CHECK(payment_ref_token IS NULL OR (length(payment_ref_token)=64 AND payment_ref_token NOT GLOB '*[^0-9a-f]*')),
 captured_at TEXT NOT NULL, post_time INTEGER CHECK(post_time IS NULL OR (typeof(post_time)='integer' AND post_time>=0))
);
-- @statement
CREATE UNIQUE INDEX source_exact_revision ON source_observations(source_kind,provider_package,source_key_token,revision_token)
 WHERE source_key_token IS NOT NULL;
-- @statement
CREATE UNIQUE INDEX source_uncertain_observation ON source_observations(source_kind,provider_package,revision_token)
 WHERE source_key_token IS NULL;
-- @statement
CREATE INDEX source_payment_reference ON source_observations(provider_package,account_surrogate,payment_ref_token);
-- @statement
CREATE TABLE candidate_reasons (
 candidate_id TEXT NOT NULL REFERENCES import_candidates(id),
 position INTEGER NOT NULL CHECK(position>=0), reason TEXT NOT NULL CHECK(length(reason) BETWEEN 1 AND 64 AND reason NOT GLOB '*[^A-Z0-9_]*'),
 PRIMARY KEY(candidate_id,reason), UNIQUE(candidate_id,position)
);
-- @statement
CREATE TABLE candidate_matches (
 candidate_id TEXT NOT NULL REFERENCES import_candidates(id),
 transaction_id TEXT NOT NULL REFERENCES transactions(id), PRIMARY KEY(candidate_id,transaction_id)
);
-- @statement
CREATE TABLE selected_sources (package_name TEXT PRIMARY KEY NOT NULL);
-- @statement
CREATE TRIGGER transaction_category_insert BEFORE INSERT ON transactions
WHEN NEW.category_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM categories WHERE id=NEW.category_id AND deleted=0 AND kind=NEW.kind)
BEGIN SELECT RAISE(ABORT,'category constraint'); END;
-- @statement
CREATE TRIGGER transaction_category_update BEFORE UPDATE OF category_id,kind ON transactions
WHEN NEW.category_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM categories WHERE id=NEW.category_id AND deleted=0 AND kind=NEW.kind)
BEGIN SELECT RAISE(ABORT,'category constraint'); END;
-- @statement
CREATE TRIGGER category_kind_update BEFORE UPDATE OF kind ON categories
WHEN NEW.kind!=OLD.kind AND EXISTS(SELECT 1 FROM transactions WHERE category_id=OLD.id)
BEGIN SELECT RAISE(ABORT,'category constraint'); END;
-- @statement
CREATE TRIGGER category_delete_guard BEFORE UPDATE OF deleted ON categories
WHEN NEW.deleted=1 AND EXISTS(SELECT 1 FROM transactions WHERE category_id=OLD.id)
BEGIN SELECT RAISE(ABORT,'category constraint'); END;
