async (page) => {
 await page.route('**/merchant/workspace/**',async route=>{const path=new URL(route.request().url()).pathname;let data={};
 if(path.endsWith('/me'))data={id:1,name:'地图测试商家',username:'map_fixture',role:'OWNER',merchantId:1,onboardingLocked:false};
 if(path.endsWith('/category/options'))data=[{id:1,name:'餐饮门店'}];
 if(path.endsWith('/store/map'))data={coordinateSystem:'GCJ-02',total:3,locatedCount:2,unlocatedCount:1,list:[{id:1,name:'营业测试店',storeTypeId:1,storeTypeName:'餐饮门店',address:'福州市鼓楼区',longitude:119.2965,latitude:26.0745,status:1,located:true},{id:2,name:'停用测试店',storeTypeId:1,storeTypeName:'餐饮门店',address:'福州市鼓楼区',longitude:119.2968,latitude:26.0747,status:0,located:true},{id:3,name:'待补录测试店',storeTypeId:1,storeTypeName:'餐饮门店',address:'福州市',longitude:null,latitude:null,status:1,located:false}]};
 if(path.endsWith('/reverseGeocode'))data={address:'福州市鼓楼区测试位置',longitude:Number(new URL(route.request().url()).searchParams.get('longitude')),latitude:Number(new URL(route.request().url()).searchParams.get('latitude'))};
 await route.fulfill({json:{code:1000,data}}); });
 await page.route('**/admin/base/open/eps',route=>route.fulfill({json:{code:1000,data:[]}}));
 await page.addInitScript(()=>{localStorage.setItem('quanguanjia:merchant:token',JSON.stringify('map-fixture-only'));localStorage.setItem('quanguanjia:merchant:token_deadtime',JSON.stringify(Date.now()+3600000));});
 await page.goto('http://localhost:9001/merchant/store-map');
 await page.waitForTimeout(18000);
 return await page.evaluate(()=>({sdk:!!window.TMap?.Map,cluster:!!window.TMap?.MarkerCluster,editor:!!window.TMap?.tools?.GeometryEditor,mapUnavailable:document.body.innerText.includes('地图暂时不可用'),canvases:document.querySelectorAll('.map-canvas canvas').length,cards:document.querySelectorAll('.store-card').length}));
}
