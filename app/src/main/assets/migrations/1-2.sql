ALTER TABLE diary ADD COLUMN isSupplement INTEGER NOT NULL DEFAULT 0;
ALTER TABLE diary ADD COLUMN supplementKind TEXT NOT NULL DEFAULT 'OTHER';
ALTER TABLE supplements ADD COLUMN kind TEXT NOT NULL DEFAULT 'OTHER';
CREATE TABLE IF NOT EXISTS water_entries (id TEXT NOT NULL, amountMl REAL NOT NULL, time INTEGER NOT NULL, PRIMARY KEY(id));
CREATE INDEX IF NOT EXISTS index_water_entries_time ON water_entries(time);
CREATE TABLE IF NOT EXISTS favorite_foods (foodId TEXT NOT NULL, savedAt INTEGER NOT NULL, PRIMARY KEY(foodId));
UPDATE supplements SET kind = 'CREATINE' WHERE lower(replace(replace(name, 'İ', 'I'), 'ı', 'i')) LIKE '%creatin%' OR lower(replace(replace(name, 'İ', 'I'), 'ı', 'i')) LIKE '%kreatin%';
UPDATE supplements SET kind = 'PROTEIN_POWDER' WHERE kind = 'OTHER' AND (lower(replace(replace(name, 'İ', 'I'), 'ı', 'i')) LIKE '%protein%' OR lower(replace(replace(name, 'İ', 'I'), 'ı', 'i')) LIKE '%whey%' OR lower(replace(replace(name, 'İ', 'I'), 'ı', 'i')) LIKE '%highpro%' OR lower(replace(replace(name, 'İ', 'I'), 'ı', 'i')) LIKE '%kazein%' OR lower(replace(replace(name, 'İ', 'I'), 'ı', 'i')) LIKE '%casein%');
UPDATE diary SET isSupplement = 1, supplementKind = COALESCE((SELECT p.kind FROM supplement_intakes i JOIN supplements p ON p.id = i.productId WHERE i.diaryEntryId = diary.id AND i.deleted = 0 LIMIT 1), 'OTHER') WHERE id IN (SELECT diaryEntryId FROM supplement_intakes WHERE diaryEntryId IS NOT NULL AND deleted = 0);
