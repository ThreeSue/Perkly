# 券管家用户端门店与优惠券展示接口

更新日期：2026-10-20。适用后端：`cool-admin-java-test`。

本次新增门店分页、门店详情、优惠券分页、优惠券详情四个只读接口，另收录已有的用户端分类接口，方便移动端直接对接。Controller均位于对应模块的`controller/app`目录。

## 1. 通用约定

- 基础地址：本地默认`http://127.0.0.1:8001`，以实际后端配置为准。手机真机使用电脑的局域网地址。
- 以下五个接口都允许游客访问，不需要用户Token，更不需要管理员Token。
- POST使用`Content-Type: application/json`。分页允许空请求体或`{}`。
- 成功返回`{"code":1000,"message":"success","data":...}`；业务错误以非1000的code和message表示，不能仅凭HTTP状态判断成功。
- 门店、商家、分类、券模板、发布记录的ID含义不同，不可混用。ID通常返回数字，超过JavaScript安全整数范围时框架返回字符串，前端应原样传递。
- 新增优惠券接口的金额统一为整数分，例：`salePrice:1990`表示19.90元。运营端模板金额仍按原约定为元，前端不要混用。
- 时间为`yyyy-MM-dd HH:mm:ss`，业务时区为Asia/Shanghai（UTC+8）；空时间表示未设置该边界。折扣`discountRate:8.80`表示8.8折，不是金额。
- 分页默认`page=1,size=20`；page为正整数，size为1至100。固定排序，不接受客户端自定义SQL排序。
- 分页结构为`data.list`与`data.pagination`，其中total为总记录数。空结果返回空数组，越界页也返回空数组。
- 仅提供浏览和详情。本次没有新增领取、购买、模拟支付、发券或核销接口，页面不能据此提示操作成功。`saleState`表示规则和库存条件，不代表已实现领取购买。

## 2. 接口总览

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/app/merchant/category/list` | 已有：公开商家行业分类 |
| POST | `/app/merchant/store/page` | 新增：公开门店分页与搜索 |
| GET | `/app/merchant/store/info?id=门店ID` | 新增：门店及所属商家公开详情 |
| POST | `/app/coupon/page` | 新增：平台公开优惠券分页与搜索 |
| GET | `/app/coupon/info?id=券模板ID` | 新增：优惠券规则及适用门店 |

## 3. 行业分类

```http
GET /app/merchant/category/list
```

无参数。返回完整平铺数组，字段为`id、parentId、name、icon、sort`。一级分类parentId为null；二级分类通过parentId归属一级分类。只返回启用且允许前台展示的分类，其一级分类也必须可见；sort升序，相同sort按id升序。

```json
{"code":1000,"message":"success","data":[
  {"id":3,"parentId":null,"name":"餐饮美食","icon":null,"sort":10},
  {"id":6,"parentId":3,"name":"奶茶咖啡","icon":null,"sort":20}
]}
```

分类不传给列表接口时表示查询全部可展示结果。所有示例ID和内容仅说明格式，不代表数据库实际记录。

## 4. 门店列表

```http
POST /app/merchant/store/page
Content-Type: application/json

{"page":1,"size":20,"keyword":"咖啡","categoryId":3,"merchantId":1}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| page、size | 否 | 分页 |
| keyword | 否 | 门店名称、商家名称、门店地址的包含搜索；去首尾空格，最多100字 |
| categoryId | 否 | 商家行业分类ID；一级分类包含直属二级分类，二级分类仅匹配本级 |
| merchantId | 否 | 限定某个商家的门店 |

筛选条件组合生效。不传筛选条件即可持续分页浏览全部公开门店。ID筛选参数须大于0；不匹配返回空列表。按门店ID倒序排列。

只返回`store.status=1`且所属`merchant_info.status=1`的门店。商家内部的门店分类不作为平台行业分类使用。

```json
{
  "code":1000,"message":"success",
  "data":{
    "list":[{
      "id":101,"name":"青禾咖啡·金山店",
      "merchantId":1,"merchantName":"青禾咖啡",
      "merchantLogo":"/uploads/logo.png","cover":"/uploads/logo.png",
      "categoryId":6,"categoryName":"奶茶咖啡",
      "address":"福州市仓山区……","phone":"0591-12345678",
      "businessHours":"09:00-22:00",
      "longitude":119.20,"latitude":26.03,
      "status":1,"statusText":"营业中"
    }],
    "pagination":{"page":1,"size":20,"total":1}
  }
}
```

字段说明：id是门店ID；categoryId/categoryName是商家行业分类；地址、电话、营业时间及坐标来自门店。现有门店表没有独立封面字段，因此cover暂复用商家Logo，可能为空；前端可使用默认图。status/statusText对应后台门店状态，不根据营业时间实时判断是否处于营业时段。没有定位计算，不返回虚构距离。

## 5. 门店详情

```http
GET /app/merchant/store/info?id=101
```

id必填且为正整数。data为单个门店对象，包含列表中的全部字段，额外返回`merchantIntroduction`（商家简介，可为空）。不返回内部备注、联系人姓名、联系人私人电话等管理字段。

不存在、门店停用或所属商家停用时返回业务错误“门店不存在或暂不可展示”。详情与列表使用相同可见规则，不能通过猜测ID读取隐藏门店。

详情页展示优惠券时，调用第6节接口并传`storeId=101`。优惠券在详情页单独分页加载，门店详情不内嵌无上限券列表。

## 6. 优惠券列表

```http
POST /app/coupon/page
Content-Type: application/json

{"page":1,"size":20,"storeId":101,"type":"CASH"}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| page、size | 否 | 分页 |
| keyword | 否 | 券名称或商家名称的包含搜索，最多100字 |
| categoryId | 否 | 商家行业分类，同门店列表的上下级规则 |
| merchantId | 否 | 所属商家ID |
| storeId | 否 | 返回适用于此门店的券，兼容全店适用和指定门店两种范围 |
| type | 否 | 下表六种券类型之一；不传表示全部 |

| type | typeText |
|---|---|
| FULL_REDUCTION | 满减券 |
| DISCOUNT | 折扣券 |
| CASH | 代金券 |
| PACKAGE | 套餐券 |
| EXPERIENCE | 体验券 |
| EXCHANGE | 兑换券 |

### 6.1 公开范围

查询复用`coupon_template、coupon_publication、coupon_store、store、merchant_info`等已有表。服务端固定要求：

1. 券模板启用、所属商家启用。
2. 发布渠道为PLATFORM、审核状态为2（通过）、上架状态为1（上架）。
3. 已发布版本published_version与模板rule_version一致，避免展示未经审核的新版本。
4. 至少有一家同商家的营业门店适用该券。
5. 传storeId时，该门店必须营业、属于对应商家，且符合券的适用范围。

草稿、待审核、驳回、下架、仅商家自有端发布或版本不一致的券，均不返回。**模板启用不等于平台上架。** 当前发布审核管理流程尚未完成；若没有符合条件的发布记录，即使运营端已有模板，用户端也会返回空列表。本次不自动审核、上架或修改现有数据。

允许展示仍在平台上架的未开始、售罄和已结束券，通过saleState区分；它们不能因前端按钮而被视为已领取或已购买。按发布时间倒序、同时间按券模板ID倒序。

### 6.2 返回字段

分页结构与门店列表相同；list每项包含：

| 字段 | 说明 |
|---|---|
| id、publicationId | 券模板ID、平台发布记录ID；不是顾客持券ID，也不是核销码 |
| merchantId、merchantName、merchantLogo | 所属商家公开信息 |
| categoryId、categoryName | 商家行业分类 |
| name、type、typeText、cover、description | 券名称、类型、类型文字、封面、完整使用说明 |
| salePrice | 售价，整数分；0表示售价为零，不代表已支持领取 |
| minSpend、discountAmount、deductAmount、maxDiscount | 门槛、满减金额、抵扣额、最高优惠；整数分，不适用时为null |
| discountRate | 折扣数，例如8.80表示8.8折，不适用时为null |
| scopeType | 0商家全部营业门店；1指定门店 |
| issueLimit、issuedCount、remainingStock | 发行上限、已发放数量、剩余数量；不限量时issueLimit和remainingStock为null |
| perCustomerLimit | 每位顾客领取／购买总量上限，不表示当前用户剩余可领数量 |
| allowCombine | 0不可同享其他优惠，1允许同享 |
| saleStartTime、saleEndTime | 可领取／购买起止时间 |
| validityType、validFrom、validTo、validDays | FIXED使用固定起止日期；AFTER_ISSUE使用发券后有效天数 |
| saleState、saleStateText | 下表展示状态 |

| saleState | 文字 | 条件及优先级 |
|---|---|---|
| ENDED | 已结束 | 领取购买结束或固定有效期结束，优先级最高 |
| SOLD_OUT | 已售罄 | 有发行上限且已发放数量达到上限 |
| NOT_STARTED | 未开始 | 领取购买开始时间未到 |
| AVAILABLE | 可领购 | 通过上述时间和库存判断；实际操作接口尚未开放 |

金额示例：`{"salePrice":1990,"deductAmount":2000,"discountRate":null}`表示售价19.90元、可抵扣20元。

## 7. 优惠券详情

```http
GET /app/coupon/info?id=201
```

id为券模板ID，必填且大于0。与列表使用完全相同的公开范围；不存在或不可展示时返回业务错误“优惠券不存在或暂不可展示”。

data包含第6节每个列表项的全部字段，另返回：

- `stores`：适用的营业门店数组，每项字段同第4节门店列表。全店适用返回当前所有营业门店；指定门店仅返回合法关联且营业的门店。
- `contents`：已有商品／服务关联数组，包含productId、relationType（1适用范围，2套餐或兑换内容）、quantity、description。无关联返回空数组。商品模块尚未建立，接口不伪造商品名称、图片或商品详情；有关系时前端可展示现有内容说明。

详情示意（以下只展示新增的关联字段，其他字段同第6节）：

```json
{
  "stores":[{"id":101,"name":"青禾咖啡·金山店","merchantId":1,"merchantName":"青禾咖啡"}],
  "contents":[{"productId":501,"relationType":2,"quantity":2,"description":"指定拿铁两杯"}]
}
```

门店列表无上限嵌套仅用于单券详情的适用范围；首页门店、首页优惠券和门店内优惠券使用各自分页接口。

## 8. 前端接入顺序与失败处理

1. 首页调用分类和门店分页；“全部”不传categoryId。
2. 点击一级分类传其ID，包含其直属子分类的门店；二级分类传二级ID。
3. 点击门店卡片调用门店详情，同时以storeId分页查询该门店的券。
4. 优惠券频道不传storeId，分页查看全部公开券。
5. 点击券卡片用券模板id调用优惠券详情；点击适用门店使用门店id打开门店详情。
6. 加载中、空列表、请求失败分别显示对应状态。业务code非1000时展示message，不切换为假数据。

示例：

```javascript
uni.request({
  url: BASE_URL + '/app/merchant/store/page',
  method: 'POST',
  data: { page: 1, size: 20, categoryId: 3 },
  success: ({ data }) => {
    if (data.code === 1000) {
      // data.data.list为门店；data.data.pagination.total为总记录数。
    } else {
      uni.showToast({ title: data.message, icon: 'none' });
    }
  }
});
```

## 9. 运行与校验

复用现有数据库结构，无新增表或数据迁移。数据库需已执行原有优惠券建表脚本`testing/data/coupon-schema-v1.sql`。新增代码部署或在IDEA重启CoolApplication后生效。

回归测试入口：`AppDiscoveryPersistenceTest`（真实MySQL、测试数据事务回滚）与`AppMerchantCategoryControllerTest`。前者覆盖公开范围、商家停用、平台审核与版本、门店适用范围、金额分转换、分页、实际框架路由及经过安全过滤器的游客请求；测试结果以本次交付说明为准。真实短信、微信、前端页面接入不属于这组查询接口的验收项。
