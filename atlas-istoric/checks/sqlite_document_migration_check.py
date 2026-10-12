"""Check the *actual* Room v3→v4 migration statements with SQLite foreign keys ON.

Run with python3 checks/sqlite_document_migration_check.py in atlas-istoric.
"""
import re
import sqlite3
from pathlib import Path

code = (Path(__file__).resolve().parents[1]
        / "app/src/main/java/ro/atlasistoric/data/AtlasDatabase.kt").read_text()
chunk = code.split("private val MIGRATION_3_4 =", 1)[1].split("@Volatile", 1)[0]
sql = re.findall(r'db\.execSQL\("""(.*?)"""\.trimIndent\(\)\)', chunk, re.DOTALL)
sql += re.findall(r'db\.execSQL\("([^"]+)"\)', chunk)
assert len(sql) == 7, f"Unexpected v4 migration statements: {len(sql)}"
connection = sqlite3.connect(":memory:")
connection.execute("PRAGMA foreign_keys=ON")
connection.execute("CREATE TABLE historical_sources (id TEXT PRIMARY KEY NOT NULL)")
connection.execute("CREATE TABLE historical_assertions (id TEXT PRIMARY KEY NOT NULL)")
connection.execute("INSERT INTO historical_sources VALUES('src')")
connection.execute("INSERT INTO historical_assertions VALUES('claim')")
for statement in sql:
    connection.execute(statement)
connection.execute("""INSERT INTO document_pages
(id, sourceId, pageLabel, originalName, mimeType, storagePath, sha256,
 originalOcrText, verifiedText, status, createdAt, updatedAt)
VALUES ('pg','src','42','page.png','image/png','atlas_pages/pg.png',
 ?, '', '', 'CAPTURED', 1, 1)""", ("a" * 64,))
connection.execute("""INSERT INTO page_annotations
(id, pageId, startOffset, endOffset, exactQuote, contextBefore, contextAfter,
 category, note, createdAt, updatedAt)
VALUES ('ann','pg',0,4,'Roma','','','LOC','',2,2)""")
connection.execute("INSERT INTO annotation_claim_links VALUES('ann','claim')")
assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
assert connection.execute("SELECT COUNT(*) FROM historical_assertions").fetchone()[0] == 1
try:
    connection.execute("DELETE FROM historical_sources WHERE id='src'")
    raise AssertionError("Source removed despite preserved page")
except sqlite3.IntegrityError:
    pass
try:
    connection.execute("DELETE FROM document_pages WHERE id='pg'")
    raise AssertionError("Page removed despite preserved annotation")
except sqlite3.IntegrityError:
    pass
try:
    connection.execute("""INSERT INTO document_pages
      (id, sourceId, pageLabel, originalName, mimeType, storagePath, sha256,
       originalOcrText, verifiedText, status, createdAt, updatedAt)
      VALUES ('duplicate','src','43','x','image/png','atlas_pages/duplicate.png',
       ?, '', '', 'CAPTURED', 1, 1)""", ("a" * 64,))
    raise AssertionError("Duplicate original image accepted for same source")
except sqlite3.IntegrityError:
    pass
print("PASS: historical originals, annotation links, foreign keys, SHA dedupe and old assertions preserved.")
