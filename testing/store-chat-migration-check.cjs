// Runs only the reviewed chat migration against the dedicated regression database.
const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const yaml = require('../../cool-admin-vue-8.x/node_modules/yaml');
const root = path.resolve(__dirname, '..');
const ds = yaml.parse(fs.readFileSync(path.join(root, 'src/main/resources/application-local.yml'), 'utf8')).spring.datasource;
const url = new URL(ds.url.replace(/^jdbc:/, ''));
if (!['localhost', '127.0.0.1'].includes(url.hostname)) throw Error('Local regression database only');
const database = 'perkly_ai_regression_20261106';
const migration = fs.readFileSync(path.join(root, 'deploy/store-chat-ai-20261110.sql'), 'utf8');
const sql = migration + '\n' + migration + "\nSELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='support_store_chat' AND column_name IN ('state','round','version','assignee_id','latest_question_id','summary')) OR (table_name='support_chat_message' AND column_name IN ('round','card_snapshot','reference_json','command','command_version','card_type','card_ref_id')));";
const result = cp.spawnSync('C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe', [
  '--host=' + url.hostname, '--port=' + (url.port || 3306), '--user=' + ds.username,
  '--default-character-set=utf8mb4', '--batch', '--skip-column-names', database
], {input: sql, env: {...process.env, MYSQL_PWD: String(ds.password)}, encoding: 'utf8', windowsHide: true, timeout: 30000});
if (result.status !== 0) throw Error('Chat migration failed: ' + (result.error?.code || result.stderr.replaceAll(String(ds.password), '[redacted]').slice(0, 500)));
const lines = result.stdout.trim().split(/\r?\n/);
if (lines.at(-1) !== '13') throw Error('Chat columns incomplete');
console.log('PASS: chat migration executed twice on dedicated regression database; all 13 additive columns verified. No production database used.');
