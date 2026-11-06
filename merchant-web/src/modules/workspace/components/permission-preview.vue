<template>
 <section class="permission-preview">
  <div class="preview-heading"><div><strong>登录页面预览</strong><p>按当前设置展示菜单和工作台，未保存的修改也可预览。示例页面不读取业务数据、不执行操作；实际权限以保存后的上级授权校验为准。</p></div><el-button @click="fullscreen = true">全屏预览</el-button></div>
  <div class="preview-mini"><span class="preview-brand">券管家管理后台</span><span>{{ pages.map(m => m.name).join(' · ') || '暂无可用页面' }}</span></div>
  <el-dialog v-model="fullscreen" fullscreen append-to-body title="登录页面预览 · 当前权限配置">
   <div class="preview-app">
    <aside><h2>券管家管理后台</h2><button v-for="page in pages" :key="page.id" :class="{active: activePage?.id === page.id}" @click="activeId = page.id">{{ page.name }}</button><p v-if="!pages.length">暂无可用页面</p></aside>
    <main><header>{{ accountName || '员工账号' }} · 权限预览 <el-tag type="info">示例数据</el-tag></header>
     <template v-if="activePage"><h2>{{ activePage.name }}</h2>
      <template v-if="/工作台/.test(activePage.name)"><h3>已授权功能</h3><div class="preview-cards"><button v-for="page in pages.filter(p => p.id !== activePage.id)" :key="page.id" @click="activeId = page.id">{{ page.name }}</button></div></template>
      <template v-else><div class="preview-actions"><el-button v-for="action in actions" :key="action.id" disabled>{{ action.name }}</el-button></div><el-empty description="此处展示对应功能页面；预览不加载真实业务数据" /></template>
     </template><el-empty v-else description="未勾选任何可访问页面" />
    </main>
   </div>
  </el-dialog>
 </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
const props = defineProps<{ menus: any[]; selectedIds: number[]; accountName?: string }>();
const fullscreen = ref(false), activeId = ref<number>();
const selected = computed(() => new Set((props.selectedIds || []).map(Number)));
const pages = computed(() => props.menus.filter(m => m.type === 1 && selected.value.has(Number(m.id))).sort((a,b) => (a.orderNum || 0) - (b.orderNum || 0)));
const activePage = computed(() => pages.value.find(m => m.id === activeId.value) || pages.value[0]);
const actions = computed(() => props.menus.filter(m => m.type === 2 && selected.value.has(Number(m.id)) && Number(m.parentId) === Number(activePage.value?.id)));
</script>
<style scoped>
.permission-preview{width:100%;border:1px solid var(--el-border-color);border-radius:10px;padding:16px;box-sizing:border-box;margin-top:16px}.preview-heading{display:flex;justify-content:space-between;gap:20px}.preview-heading p{color:var(--el-text-color-secondary);font-size:13px;line-height:1.6}.preview-mini{display:flex;gap:20px;background:var(--el-fill-color-light);padding:16px;border-radius:8px}.preview-brand{font-weight:600}.preview-app{display:flex;min-height:calc(100vh - 110px);background:var(--el-fill-color-light)}aside{width:240px;flex-shrink:0;background:#2d3348;color:white;padding:20px;box-sizing:border-box}aside h2{font-size:20px;margin-bottom:30px}aside button{display:block;width:100%;padding:15px;text-align:left;border:0;background:transparent;color:white;border-radius:6px;cursor:pointer}aside button.active{background:#4465dd}main{flex:1;padding:28px;min-width:0}header{padding:16px;background:var(--el-bg-color);margin-bottom:30px}.preview-cards{display:flex;gap:16px;flex-wrap:wrap}.preview-cards button{padding:24px;border:1px solid var(--el-border-color);background:var(--el-bg-color);color:var(--el-text-color-primary);border-radius:8px;cursor:pointer}.preview-actions{display:flex;gap:10px;flex-wrap:wrap}
</style>
