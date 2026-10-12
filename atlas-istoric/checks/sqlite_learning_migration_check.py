"""Lightweight SQLite check of the actual SQL inside MIGRATION_2_3.

This checks database-level referential integrity but does not replace Android Room
instrumentation or tests on a real device.
"""
import re
import sqlite3
from pathlib import Path

source = Path(__file__).resolve().parents[1] / "app/src/main/java/ro/atlasistoric/data/AtlasDatabase.kt"
txt = source.read_text(encoding="utf-8")
snippet = txt.split("private val MIGRATION_2_3 =", 1)[1].split("@Volatile", 1)[0]
sqls = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\s*\)', snippet, re.DOTALL)
sqls += re.findall(r'db\.execSQL\("([^"]+)"\)', snippet)
assert len(sqls) == 6, f"Unexpected schema statements: {len(sqls)}"
db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
db.execute("CREATE TABLE historical_assertions (id TEXT PRIMARY KEY NOT NULL)")
db.execute("INSERT INTO historical_assertions (id) VALUES ('a1')")
for sql in sqls:
    db.execute(sql)
db.execute("""INSERT INTO study_cards (
  id, assertionId, question, answerMode, note, suspended, dueAt,
  intervalDays, easePermille, repetitions, lapses, lastReviewedAt,
  createdAt, updatedAt
) VALUES ('c1', 'a1', 'Ce afirmă sursa?', 'PASAJ', '', 0, 0, 0, 2300, 0, 0, NULL, 1, 1)""")
db.execute("""INSERT INTO study_reviews (
  id, cardId, grade, reviewedAt, previousIntervalDays, nextIntervalDays, nextDueAt
) VALUES ('r1', 'c1', 2, 2, 0, 1, 86400002)""")
assert db.execute("SELECT COUNT(*) FROM historical_assertions").fetchone()[0] == 1
assert db.execute("SELECT COUNT(*) FROM study_reviews").fetchone()[0] == 1
try:
    db.execute("""INSERT INTO study_cards (
      id, assertionId, question, answerMode, note, suspended, dueAt,
      intervalDays, easePermille, repetitions, lapses, lastReviewedAt,
      createdAt, updatedAt
    ) VALUES ('broken', 'absent', 'Ce?', 'PASAJ', '', 0, 0, 0, 2300, 0, 0, NULL, 1, 1)""")
    raise AssertionError("Missing source assertion unexpectedly accepted")
except sqlite3.IntegrityError:
    pass
try:
    db.execute("DELETE FROM historical_assertions WHERE id='a1'")
    raise AssertionError("Deletion of cited evidence unexpectedly accepted")
except sqlite3.IntegrityError:
    pass
assert db.execute("PRAGMA foreign_key_check").fetchall() == []
print("PASS: Study migration v2→v3 adds reference-safe tables and retains original assertions.")
