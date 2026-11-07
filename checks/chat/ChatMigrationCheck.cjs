const assert = require('node:assert/strict');
const fs = require('node:fs');
const { DatabaseSync } = require('node:sqlite');
const source = fs.readFileSync('app/src/main/java/com/wechattodo/assistant/TaskStore.java', 'utf8');
const schemas = [...source.matchAll(/database\.execSQL\(([^;]+)\);/g)]
  .map(([, expression]) => [...expression.matchAll(/"[^"]*"/g)]
    .map(([literal]) => JSON.parse(literal)).join(''));
const [tasks, notificationHistory, chatHistory] = schemas;
const db = new DatabaseSync(':memory:');
db.exec(tasks);
db.exec(notificationHistory);
db.prepare('INSERT INTO tasks (source, body, due, done) VALUES (?, ?, ?, ?)')
  .run('fixture', 'existing task', 1700000000, 1);
db.prepare('INSERT INTO seen VALUES (?, ?)').run('old-fingerprint', 1700000000);
db.exec('PRAGMA user_version=1; BEGIN');
db.exec(chatHistory);
db.exec('PRAGMA user_version=2; COMMIT');
const task = db.prepare('SELECT source, body, due, done FROM tasks').get();
assert.deepEqual({...task}, {source:'fixture', body:'existing task', due:1700000000, done:1});
assert.equal(db.prepare('SELECT count(*) AS n FROM seen').get().n, 1);
const seen = db.prepare('INSERT OR IGNORE INTO chat_seen VALUES (?, ?)');
assert.equal(seen.run('same-chat-and-text', 1700000001).changes, 1);
assert.equal(seen.run('same-chat-and-text', 1700000002).changes, 0);
assert.equal(db.prepare('SELECT seen_at FROM chat_seen').get().seen_at, 1700000001);
db.close();
console.log('Old tasks, notification history, and the new chat fingerprint key passed.');
