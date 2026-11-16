"""Build immutable SQLite catalogue releases. Never includes private app/diary data."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
import sqlite3
import unicodedata
from contextlib import closing

ROOT = Path(__file__).resolve().parents[1]
FIELDS = {'kcal': 'Energy kcal', 'protein': 'Protein (g)', 'carbs': 'Carbohydrate (g)',
          'fat': 'Fat (g)', 'fiber': 'Fiber (g)', 'sugar': 'Sugars (g)', 'sodium': 'Sodium (g)'}

def folded(text):
    return ''.join(c for c in unicodedata.normalize('NFD', text.lower()) if not unicodedata.combining(c)).replace('ı', 'i')

def number(text):
    if not str(text).strip(): return None
    value = float(str(text).replace(',', '.'))
    if not math.isfinite(value) or value < 0: raise ValueError('Invalid nutrient')
    return value

def convert(row):
    # No density is invented for values measured per ml or per serving.
    if row.get('Basis unit', '').strip().lower() != 'g': raise ValueError('Not a mass-based label')
    basis = number(row.get('Basis qty', ''))
    if not basis: raise ValueError('Missing serving basis')
    name, url = row.get('Product', '').strip(), row.get('Source URL', '').strip()
    if not name or not url.startswith('https://'): raise ValueError('Missing name or HTTPS provenance')
    variant = row.get('Flavor / variant', '').strip()
    if variant and variant.lower() not in name.lower(): name += ' · ' + variant
    nutrient = {key: number(row.get(field, '')) for key, field in FIELDS.items()}
    if all(value is None for value in nutrient.values()): raise ValueError('No reported nutrients')
    nutrient = {key: None if value is None else value * 100 / basis for key, value in nutrient.items()}
    identity = url + '\n' + variant
    return dict(id='catalogue:' + hashlib.sha256(identity.encode()).hexdigest()[:24], name=name,
                brand=row.get('Brand', ''), source=row.get('Store', '') + ' · web catalogue',
                source_url=url, source_license='Source-dependent; redistribution rights not verified',
                barcode='', search_key=folded(name + ' ' + row.get('Brand', '')),
                qc_note=' · '.join(filter(None, [row.get('QC status'), row.get('QC note')])), **nutrient)

def publish(csv_path, version, destination=ROOT / 'database', kind='minor', release_notes=None):
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', version): raise ValueError('Invalid version')
    release_dir = destination / 'versions' / version
    if release_dir.exists(): raise ValueError('Released database versions are immutable; choose a new version')
    rows, rejected, duplicate = {}, {}, 0
    if csv_path:
        with open(csv_path, encoding='utf-8-sig', newline='') as stream:
            for raw in csv.DictReader(stream):
                try:
                    row = convert(raw)
                    if row['id'] in rows: duplicate += 1
                    rows[row['id']] = row
                except (ValueError, OverflowError) as error:
                    key = str(error); rejected[key] = rejected.get(key, 0) + 1
    release_dir.mkdir(parents=True)
    path = release_dir / 'foods.sqlite'
    notes = {'en': ['Independent food database updates.', 'Source-attributed mass-based food labels.', 'Offline English/Turkish food-term search.'],
             'tr': ['Bağımsız besin veri tabanı güncellemeleri.', 'Kaynak bilgili, gram esaslı besin etiketleri.', 'Çevrimdışı İngilizce/Türkçe besin terimi araması.']}
    if kind not in {'major','minor','patch'}: raise ValueError('Unknown release kind')
    metadata = dict(version=version, schema=1, minimumAppCode=2, highlights=kind!='patch', notes=release_notes or notes,
                    foodCount=len(rows), excluded=rejected, duplicateRows=duplicate, noticeVersion=1)
    try:
        with closing(sqlite3.connect(path)) as db, db:
            db.executescript('''PRAGMA user_version=1;
              CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
              CREATE TABLE foods (id TEXT PRIMARY KEY, name TEXT NOT NULL, brand TEXT NOT NULL,
                source TEXT NOT NULL, source_url TEXT NOT NULL, source_license TEXT NOT NULL,
                barcode TEXT NOT NULL, search_key TEXT NOT NULL, qc_note TEXT NOT NULL,
                kcal REAL, protein REAL, carbs REAL, fat REAL, fiber REAL, sugar REAL, sodium REAL);
              CREATE INDEX food_name ON foods(name);
              CREATE TABLE translations(food_id TEXT NOT NULL, locale TEXT NOT NULL, name TEXT NOT NULL,
                PRIMARY KEY(food_id,locale));''')
            db.execute('INSERT INTO metadata VALUES (?,?)', ('release', json.dumps(metadata, ensure_ascii=False)))
            for row in rows.values():
                columns=list(row)
                db.execute('INSERT INTO foods ('+','.join(columns)+') VALUES ('+','.join('?' for _ in columns)+')', list(row.values()))
            assert db.execute('PRAGMA quick_check').fetchone()[0] == 'ok'
        # Only closed, checkpointed SQLite files are published, never a live WAL database.
        content=path.read_bytes()
        manifest={**metadata, 'formatVersion':1, 'file':f'versions/{version}/foods.sqlite',
                  'bytes':len(content), 'sha256':hashlib.sha256(content).hexdigest()}
        (release_dir/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        (destination/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        shutil.copyfile(path, destination/'foods.sqlite')
        return manifest
    except Exception:
        # Leave failed output for inspection; never publish over an existing version.
        raise

if __name__ == '__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--csv', type=Path); parser.add_argument('--version', required=True)
    parser.add_argument('--kind',choices=['major','minor','patch'],default='minor')
    parser.add_argument('--notes-json',type=Path)
    args=parser.parse_args(); notes=json.loads(args.notes_json.read_text(encoding='utf-8')) if args.notes_json else None
    print(json.dumps(publish(args.csv,args.version,kind=args.kind,release_notes=notes), ensure_ascii=False, indent=2))
