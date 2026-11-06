import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import ts from 'typescript';
import { parse, compileScript } from '@vue/compiler-sfc';
import * as Vue from 'vue';

const read = name => readFileSync(new URL('../src/modules/business/components/' + name + '.vue', import.meta.url), 'utf8');
test('双权限上传立即登记素材，可在另一个编辑框复用并重开回显', async () => {
	const materials = [];
	const form = { cover: '/old.png', coverMaterialId: null };
	const emit = (event, value) => {
		if (event === 'update:modelValue') form.cover = value;
		if (event === 'update:materialId') form.coverMaterialId = value;
	};
	let legacyCalls = 0;
	const upload = setup('merchant-image-upload', { canBrowse: true, canManage: true }, emit, {
		'../merchant-material-api': { uploadMaterial: async () => {
			const image = { id: 51, url: '/merchant-a/new.png', name: '新封面' };
			materials.push(image);
			return image;
		} },
		'/@/merchant-api': { uploadMerchantImage: async () => { legacyCalls++; } }
	});
	await upload.send({ file: {} });
	assert.equal(legacyCalls, 0);
	assert.equal(materials.length, 1);
	assert.deepEqual(form, { cover: materials[0].url, coverMaterialId: 51 });
	assert.equal(upload.materialRevision.value, 1);
	const selected = [];
	const picker = setup('merchant-material-picker', {}, (event, value) => {
		if (event === 'select') selected.push(value);
	}, { '../merchant-material-api': {
		listMaterials: async () => ({ list: materials, total: materials.length }),
		listMaterialCategories: async () => []
	} });
	await picker.open();
	picker.selectedId.value = 51;
	picker.confirm();
	assert.equal(selected[0].url, form.cover);
	const reopened = setup('merchant-material-picker', { currentUrl: form.cover }, () => {}, {
		'../merchant-material-api': {
			listMaterials: async () => ({ list: materials, total: materials.length }),
			listMaterialCategories: async () => []
		}
	});
	await reopened.open();
	assert.equal(reopened.selectedId.value, form.coverMaterialId);
});

test('缺少任一素材权限明确拒绝上传，不再产生不入库的封面；登记失败保留原封面', async () => {
	for (const props of [{}, { canBrowse: true }, { canManage: true }]) {
		let legacyCalls = 0;
		const upload = setup('merchant-image-upload', props, () => {}, {
			'../merchant-material-api': { uploadMaterial: async () => { throw new Error('不应调用'); } },
			'/@/merchant-api': { uploadMerchantImage: async () => { legacyCalls++; return '/legacy.png'; } }
		});
		await upload.send({ file: {} });
		assert.equal(legacyCalls, 0);
		assert.match(upload.error.value, /素材库查看与管理权限/);
	}
	for (const failure of ['没有素材库操作权限', '图片超出限制', null]) {
		const changes = [];
		let legacyCalls = 0;
		const upload = setup('merchant-image-upload', { canBrowse: true, canManage: true },
			(event, value) => { if (event.startsWith('update:')) changes.push(value); }, {
				'../merchant-material-api': { uploadMaterial: async () => {
					if (failure) throw new Error(failure);
					return null;
				} },
				'/@/merchant-api': { uploadMerchantImage: async () => { legacyCalls++; } }
			});
		await upload.send({ file: {} });
		assert.equal(legacyCalls, 0);
		assert.deepEqual(changes, []);
		assert.ok(upload.error.value.includes(failure || '素材登记响应异常'));
		assert.equal(upload.busy.value, false);
	}
});
function setup(name, props, emit, dependencies) {
	const { descriptor } = parse(read(name));
	const script = compileScript(descriptor, { id: name }).content;
	const { outputText } = ts.transpileModule(script, {
		compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
	});
	const exports = {};
	new Function('require', 'exports', outputText)(id => {
		if (id === 'vue') return { ...Vue, onBeforeUnmount() {} };
		return dependencies[id] ?? {};
	}, exports);
	return exports.default.setup(props, { emit, expose() {} });
}

test('商家素材入口读取有效查看权限，管理权限单独存在时不开放', () => {
	const form = read('business-crud');
	const gate = form.match(/const can = (.+);/)[1];
	const { outputText } = ts.transpileModule('return (' + gate + ')', {});
	const can = perms => new Function('menu', outputText)({ perms });
	assert.match(form, /:can-browse="can\('material:view'\)"/);
	assert.match(read('merchant-image-upload'), /<MerchantMaterialPicker\s+v-if="canBrowse"/);
	assert.match(read('merchant-image-upload'), /<el-button v-else disabled/);
	assert.equal(can(['material:view'])('material:view'), true);
	assert.equal(can(['material:manage'])('material:view'), false);
	assert.equal(can([])('material:view'), false);
});

test('素材选用提交 ID 和 URL，重开用服务端封面 URL 回显，更换和移除清除旧 ID', async () => {
	const material = { id: 42, url: '/uploads/cover.png', name: '门店封面' };
	const form = { cover: '/uploads/old.png', coverMaterialId: null };
	const pick = setup('merchant-material-picker', {}, (event, item) => {
		if (event === 'select') upload.selectMaterial(item);
	}, {
		'../merchant-material-api': {
			listMaterials: async () => ({ list: [material], total: 1 }),
			listMaterialCategories: async () => []
		}
	});
	const upload = setup('merchant-image-upload', { modelValue: form.cover, canBrowse: true, canManage: true }, (event, value) => {
		if (event === 'update:modelValue') form.cover = value;
		if (event === 'update:materialId') form.coverMaterialId = value;
	}, { '../merchant-material-api': { uploadMaterial: async () => ({ id: 43, url: '/uploads/new.png' }) } });
	await pick.open();
	pick.selectedId.value = material.id;
	pick.confirm();
	assert.deepEqual(form, { cover: material.url, coverMaterialId: material.id });

	const saved = { cover: form.cover };
	const reopened = setup('merchant-material-picker', { currentUrl: saved.cover }, () => {}, {
		'../merchant-material-api': {
			listMaterials: async () => ({ list: [material], total: 1 }),
			listMaterialCategories: async () => []
		}
	});
	await reopened.open();
	assert.equal(reopened.selectedId.value, material.id);
	await upload.send({ file: {} });
	assert.deepEqual(form, { cover: '/uploads/new.png', coverMaterialId: 43 });
	upload.selectMaterial(material);
	upload.remove();
	assert.deepEqual(form, { cover: '', coverMaterialId: null });
});

test('渲染商家编辑封面夹具：素材库入口始终可见，无权限显示授权说明', async () => {
	const { renderToString } = await import('@vue/server-renderer');
	const { descriptor } = parse(read('merchant-image-upload'));
	const script = compileScript(descriptor, { id: 'material-render', inlineTemplate: true }).content;
	const { outputText } = ts.transpileModule(script, {
		compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
	});
	const exports = {};
	new Function('require', 'exports', outputText)(id => {
		if (id === 'vue') return Vue;
		if (id.includes('merchant-material-picker')) return { default: {
			render: () => Vue.h('button', '从素材库选择')
		} };
		if (id.includes('cover-placeholder')) return { default: {
			render: () => Vue.h('span', '暂无封面')
		} };
		return {};
	}, exports);
	for (const permissions of [['material:view'], [], ['material:manage']]) {
		const app = Vue.createSSRApp(exports.default, {
			canBrowse: permissions.includes('material:view'),
			modelValue: '/existing-cover.png'
		});
		for (const tag of ['el-image', 'el-upload', 'el-button']) {
			app.component(tag, {
				setup: (_, { slots }) => () => Vue.h('div', slots.default?.())
			});
		}
		const html = await renderToString(app);
		assert.ok(html.includes('从素材库选择'));
		if (!permissions.includes('material:view')) assert.ok(html.includes('请上级开放'));
		assert.ok(html.includes('更换图片'));
	}
});

test('仅权限变化也会刷新权限集合，门店和优惠券页均使用商家共用表单', () => {
	const file = path => readFileSync(new URL('../src/' + path, import.meta.url), 'utf8');
	const main = file('modules/base/pages/main/index.vue');
	const update = main.indexOf('menu.setPerms(result.perms || [])');
	assert.ok(update > 0 && update < main.indexOf('if (JSON.stringify(result.menus)'));
	for (const kind of ['coupons', 'stores']) {
		assert.match(file('modules/business/views/' + kind + '.vue'), /business-crud.vue/);
	}
	const form = read('business-crud');
	assert.match(form, /v-model:material-id="scope.coverMaterialId"/);
	assert.equal((form.match(/prop: 'coverMaterialId', hidden: true/g) || []).length, 2);
});
