import hashlib,json,sqlite3
from pathlib import Path

root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/'database/manifest.json').read_text(encoding='utf-8'))
path=root/'database'/manifest['file']
assert path.resolve().is_relative_to((root/'database/versions').resolve())
content=path.read_bytes()
assert len(content)==manifest['bytes']
assert hashlib.sha256(content).hexdigest()==manifest['sha256']
assert content==(root/'database/foods.sqlite').read_bytes()
with sqlite3.connect(f'file:{path}?mode=ro',uri=True) as db:
    assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
    assert db.execute('PRAGMA user_version').fetchone()[0]==manifest['schema']
    metadata=json.loads(db.execute("SELECT value FROM metadata WHERE key='release'").fetchone()[0])
    assert metadata['version']==manifest['version']
    assert db.execute('SELECT count(*) FROM foods').fetchone()[0]==manifest['foodCount']
    tables={row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    assert tables=={'foods','metadata','translations'}
print(f"Catalogue {manifest['version']}: {manifest['foodCount']} rows; checksum, schema and privacy checks passed")
