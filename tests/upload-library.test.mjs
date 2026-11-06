import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

function uploader(add) {
	const source = readFileSync(new URL('../src/plugins/upload/hooks/index.ts', import.meta.url), 'utf8');
	const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } });
	const exports = {};
	const service = { base: { comm: { uploadMode: async () => ({ mode: 'local' }) } }, request: async () => '/uploads/picture.png', space: { info: { add } } };
	const deps = {
		'element-plus': { ElMessage: { error() {} } },
		'/@/cool': { module: { get: () => ({ options: {} }) }, service },
		'/@/cool/utils': { uuid: () => 'test-image', extname: () => 'png', filename: () => 'picture' },
		'../utils': { pathJoin: (...parts) => parts.join('/') },
		'/$/base': { useBase: () => ({ user: { token: 'fixture' } }) },
		'lodash-es': { merge: Object.assign },
		'vue-i18n': { useI18n: () => ({ t: text => text }) }
	};
	new Function('require', 'exports', outputText)(id => deps[id] ?? {}, exports);
	return exports.useUpload().toUpload;
}

test('普通图片上传在返回URL前登记素材，登记失败不报告成功', async () => {
	const records = [];
	const upload = uploader(async item => { records.push(item); return { id: 1 }; });
	const image = new File(['fixture'], 'picture.png', { type: 'image/png' });
	const result = await upload(image);
	assert.equal(records.length, 1);
	assert.equal(records[0].url, result.url);
	assert.equal(records[0].name, 'picture.png');
	assert.equal(records[0].type, 'image');
	await upload(new File(['fixture'], 'empty-mime.PNG', { type: '' }));
	assert.equal(records.length, 2);
	const denied = uploader(async () => { throw new Error('无新增权限'); });
	await assert.rejects(denied(image), /图片未登记到素材库/);
});

test('素材库内部上传由自身登记，普通文件不误入图片库', async () => {
	let calls = 0;
	const upload = uploader(async () => { calls++; });
	await upload(new File(['fixture'], 'picture.png', { type: 'image/png' }), { registerMaterial: false });
	await upload(new File(['fixture'], 'data.txt', { type: 'text/plain' }));
	assert.equal(calls, 0);
});
