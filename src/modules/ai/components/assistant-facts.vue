<template>
  <div class="assistant-facts">
    <template v-if="Array.isArray(value)">
      <div v-for="(item, index) in value" :key="index" class="fact-record">
        <AssistantFacts :value="item" :scope="scope" />
      </div>
      <p v-if="!value.length" class="empty-facts">没有匹配记录</p>
    </template>
    <dl v-else-if="value && typeof value === 'object'">
      <template v-for="(item, key) in value" :key="key">
        <template v-if="visible(String(key), item)">
          <dt>{{ labels[String(key)] || String(key) }}</dt>
          <dd><AssistantFacts v-if="item && typeof item === 'object'" :value="item" :scope="scope" /><span v-else>{{ format(String(key), item) }}</span></dd>
        </template>
      </template>
    </dl>
    <span v-else>{{ value ?? '未提供' }}</span>
  </div>
</template>
<script setup lang="ts">
defineOptions({ name: 'AssistantFacts' });
const props=defineProps<{ value: any; scope?: string }>();
const labels: Record<string,string> = {
  name:'名称', role:'岗位资料', permission:'权限标识', accountCount:'关联账号数', globalAccountCount:'全局关联账号数', builtin:'内置岗位', storeScope:'门店范围', label:'岗位标记', title:'标题', content:'正文', description:'说明', status:'状态', sort:'排序', showOnFront:'前台展示', icon:'图标',
  before:'当前值', after:'修改后', action:'操作', list:'记录', records:'记录', total:'总数', pagination:'分页',
  page:'页码', size:'每页条数', pageNumber:'页码', pageSize:'每页条数', totalRow:'总数', totalPage:'总页数',
  merchantName:'商家', storeName:'门店', couponName:'优惠券', orderNo:'订单号', username:'账号', roleName:'岗位',
  accountType:'身份', createTime:'创建时间', updateTime:'更新时间', readAt:'阅读时间', confirmedAt:'确认时间',
  noticeType:'通知类型', senderName:'发送人', contactPhone:'联系电话', address:'地址', phone:'电话',
  successfulRedemptions:'成功核销数', knownConsumptionYuan:'已知消费金额（元）', failedAttempts:'失败次数',
  consumptionAmount:'消费金额（分）', discountAmount:'优惠金额（分）', receivedAmount:'实收金额（分）',
  completedAt:'完成时间', remark:'备注', permissions:'权限', storeNames:'门店范围', type:'类型',
  salePrice:'售价', totalAmount:'订单金额', paidAmount:'实付金额', statusText:'状态', auditStatus:'审核状态',
  publishStatus:'发布状态', parentName:'上级分类', storeCount:'关联门店数', merchantCount:'关联商家数',
  statuses:'状态分布', count:'数量', note:'统计说明', range:'统计范围', summary:'汇总', trend:'趋势', types:'券类型分布', people:'人员统计',
  startDate:'开始日期', endDate:'结束日期', time:'时间', percentage:'占比（%）', operatorName:'核销员工', redeemedAt:'核销时间',
  linkedOrders:'已确认关联订单数', unknownAmountGroups:'消费金额未知组数', knownDiscountYuan:'已知优惠金额（元）', unknownDiscountRecords:'优惠未知记录数', consumptionYuan:'消费金额（元）', discountYuan:'优惠金额（元）'
};
function visible(key: string, value: unknown) {
  if(props.scope==='STORE_TYPE' && ['parentId','icon','showOnFront'].includes(key))return false;
  return !/(^id$|Id$|Ids$|password|token|secret|Fingerprint)/i.test(key) && value !== undefined;
}
function format(key: string, value: any) {
  if (value === null) return '未提供';
  if (key === 'status' && props.scope==='STORE')return ({0:'停用',1:'营业',2:'休息'} as Record<number,string>)[value] || String(value);
  if (key === 'status' && ['CATEGORY','STORE_TYPE','MERCHANT','STAFF'].includes(props.scope || '') && [0,1].includes(value)) return value === 1 ? '启用' : '停用';
  if (key === 'showOnFront') return value === 1 ? '展示' : '隐藏';
  const names: Record<string,string> = { CREATE:'新增', UPDATE:'编辑', DELETE:'删除', PENDING:'待处理', COMPLETED:'已完成', PAID:'已支付', CANCELLED:'已取消', LIGHT:'轻通知', STRONG:'强提醒', PLATFORM:'运营', MERCHANT:'商家', STORE:'门店' };
  return names[value] || String(value);
}
</script>
<style scoped>
.assistant-facts { min-width: 0; font-size: 13px; line-height: 1.65; overflow-wrap: anywhere; }
dl { display: grid; grid-template-columns: minmax(60px, 30%) minmax(0, 1fr); gap: 5px 10px; margin: 0; }
dt { color: var(--el-text-color-secondary); } dd { margin: 0; min-width: 0; }
.fact-record + .fact-record { border-top: 1px solid var(--el-border-color-light); margin-top: 12px; padding-top: 12px; }
.empty-facts { color: var(--el-text-color-secondary); }
</style>
