import csv,hashlib,json,sqlite3,tempfile,unittest
from contextlib import closing
from pathlib import Path
from publish_database import convert,publish

class CatalogueTests(unittest.TestCase):
    def row(self,**changes):
        return {'Product':'Example powder','Source URL':'https://example.org/product/1','Store':'Example',
            'Brand':'Example','Basis qty':'30','Basis unit':'g','Protein (g)':'24','Energy kcal':'120',**changes}
    def test_serving_normalization_preserves_unknown(self):
        row=convert(self.row())
        self.assertEqual(row['protein'],80)
        self.assertEqual(row['kcal'],400)
        self.assertIsNone(row['fat'])
    def test_unknown_density_is_not_invented(self):
        with self.assertRaises(ValueError):convert(self.row(**{'Basis unit':'ml'}))
    def test_invalid_labels_rejected(self):
        for value in ['NaN','inf','-1']:
            with self.assertRaises(ValueError):convert(self.row(**{'Protein (g)':value}))
    def test_release_integrity_and_immutability(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); source=root/'input.csv'; row=self.row()
            with source.open('w',newline='') as f:
                writer=csv.DictWriter(f,fieldnames=list(row));writer.writeheader();writer.writerow(row)
            manifest=publish(source,'1.0.0',root/'database')
            db=root/'database'/manifest['file']
            self.assertEqual(hashlib.sha256(db.read_bytes()).hexdigest(),manifest['sha256'])
            with closing(sqlite3.connect(db)) as connection:
                self.assertEqual(connection.execute('PRAGMA integrity_check').fetchone()[0],'ok')
                self.assertEqual(connection.execute('SELECT count(*) FROM foods').fetchone()[0],1)
                self.assertEqual(connection.execute('PRAGMA user_version').fetchone()[0],1)
                self.assertNotIn('diary',[r[0] for r in connection.execute('SELECT name FROM sqlite_master')])
            with self.assertRaises(ValueError):publish(source,'1.0.0',root/'database')
    def test_patch_release_does_not_trigger_feature_modal(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertFalse(publish(None,'1.0.1',Path(temp)/'database',kind='patch')['highlights'])

if __name__=='__main__':unittest.main()
