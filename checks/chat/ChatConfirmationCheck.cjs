const assert = require('node:assert/strict');
const fs = require('node:fs');
const { DatabaseSync } = require('node:sqlite');
const source = fs.readFileSync('app/src/main/java/com/wechattodo/assistant/TaskStore.java', 'utf8');
const statements = [...source.matchAll(/database\.execSQL\(([^;]+)\);/g)]
  .map(([, expression]) => [...expression.matchAll(/"[^"]*"/g)]
    .map(([literal]) => JSON.parse(literal)).join(''));
const [tasks, notifications, upgradeChat, freshChat] = statements;
const pendingQuery = source.match(/"fingerprint = \?", new String\[\]\{key\}/)?.[0];
const recent = source.match(/"(fingerprint IN \(\?, \?\) AND seen_at >= \? AND accepted = 1)"/)?.[1];
assert.ok(pendingQuery && recent);

function verify(initialVersion) {
  const database = new DatabaseSync(':memory:');
  database.exec(tasks);
  database.exec(notifications);
  database.prepare('INSERT INTO tasks (source, body, due, done) VALUES (?, ?, ?, ?)')
    .run('existing', 'old completed item', 1700000000, 1);
  if (initialVersion === 2) {
    database.exec('CREATE TABLE chat_seen (fingerprint TEXT PRIMARY KEY, seen_at INTEGER NOT NULL)');
    database.prepare('INSERT INTO chat_seen VALUES (?, ?)').run('old-capture', 100);
    database.exec(upgradeChat);
    assert.equal(database.prepare('SELECT accepted FROM chat_seen WHERE fingerprint = ?')
      .get('old-capture').accepted, 1);
  } else {
    database.exec(freshChat);
  }
  const unresolved = database.prepare('SELECT fingerprint FROM chat_seen WHERE fingerprint = ?');
  const decision = database.prepare('INSERT OR IGNORE INTO chat_seen VALUES (?, ?, ?)');
  const recentCapture = database.prepare('SELECT fingerprint FROM chat_seen WHERE ' + recent);

  assert.equal(unresolved.get('visible-candidate'), undefined);
  assert.equal(database.prepare('SELECT count(*) AS n FROM tasks').get().n, 1);
  assert.equal(decision.run('skipped-candidate', 200, 0).changes, 1);
  assert.ok(unresolved.get('skipped-candidate'));
  assert.equal(recentCapture.get('skipped-candidate', 'skipped-candidate', 150), undefined);
  assert.equal(decision.run('skipped-candidate', 201, 1).changes, 0);

  database.exec('BEGIN');
  assert.equal(decision.run('visible-candidate', 202, 1).changes, 1);
  database.prepare('INSERT INTO tasks (source, body) VALUES (?, ?)').run('test chat', 'confirmed item');
  database.exec('COMMIT');
  assert.equal(decision.run('visible-candidate', 203, 1).changes, 0);
  assert.ok(recentCapture.get('visible-candidate', 'visible-candidate', 150));
  assert.deepEqual(database.prepare('SELECT body, done FROM tasks ORDER BY _id').all()
    .map(row => ({ ...row })), [
    { body: 'old completed item', done: 1 },
    { body: 'confirmed item', done: 0 }
  ]);
  database.close();
}

verify(1);
verify(2);
console.log('Upgrade, confirmation, dismissal, and notification dedup checks passed.');
