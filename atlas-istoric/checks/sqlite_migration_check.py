"""SQLite smoke test for Room 1 → 2 DDL. It is NOT an Android compile test."""
import sqlite3

conn = sqlite3.connect(':memory:')
conn.execute('PRAGMA foreign_keys=ON')
conn.execute('CREATE TABLE historical_sources (id TEXT PRIMARY KEY, author TEXT NOT NULL, title TEXT NOT NULL)')
conn.execute('CREATE TABLE historical_assertions (id TEXT PRIMARY KEY, sourceId TEXT NOT NULL, FOREIGN KEY(sourceId) REFERENCES historical_sources(id) ON DELETE RESTRICT)')
conn.execute("INSERT INTO historical_sources VALUES ('s1', 'autor', 'carte')")
conn.execute("INSERT INTO historical_assertions VALUES ('a1', 's1')")
conn.executescript('''
ALTER TABLE historical_sources ADD COLUMN readingStatus TEXT NOT NULL DEFAULT 'NEINCEPUTA';
ALTER TABLE historical_sources ADD COLUMN lastPage TEXT NOT NULL DEFAULT '';
CREATE TABLE IF NOT EXISTS research_drafts (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS index_research_drafts_updatedAt ON research_drafts (`updatedAt`);
CREATE TABLE IF NOT EXISTS draft_claim_links (`draftId` TEXT NOT NULL, `assertionId` TEXT NOT NULL, PRIMARY KEY(`draftId`, `assertionId`), FOREIGN KEY(`draftId`) REFERENCES `research_drafts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`assertionId`) REFERENCES `historical_assertions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT);
CREATE INDEX IF NOT EXISTS index_draft_claim_links_assertionId ON draft_claim_links (`assertionId`);
''')
assert conn.execute("SELECT readingStatus, lastPage FROM historical_sources WHERE id='s1'").fetchone() == ('NEINCEPUTA', '')
conn.execute("INSERT INTO research_drafts VALUES ('d1', 'teză', '', 1, 1)")
conn.execute("INSERT INTO draft_claim_links VALUES ('d1','a1')")
assert conn.execute('PRAGMA foreign_key_check').fetchall() == []
try:
    conn.execute("INSERT INTO draft_claim_links VALUES ('d1','missing')")
    raise AssertionError('Foreign keys are not enforced')
except sqlite3.IntegrityError:
    pass
print('SQLITE: testul de migrare și integritate referențială a trecut')
