async (page) => {
 const check=(v,m)=>{if(!v)throw new Error(m)};
 await page.evaluate(()=>{window.__mapInspection={maps:[],clusters:[],markers:[]};for(const [name,key] of [['Map','maps'],['MarkerCluster','clusters'],['MultiMarker','markers']]){const Original=window.TMap[name];window.TMap[name]=class extends Original{constructor(options,...rest){super(options,...rest);window.__mapInspection[key].push(this);}};}});
 await page.getByRole('menuitem',{name:'我的门店地图',exact:true}).click();await page.waitForTimeout(900);
 await page.evaluate(()=>{const map=window.__mapInspection.maps.at(-1);map.setCenter(new window.TMap.LatLng(26.07,119.29));map.setZoom(14);});await page.waitForTimeout(1500);
 const aggregation=await page.evaluate(()=>window.__mapInspection.clusters.at(-1).getClusters().map(c=>c.geometries.length));
 check(aggregation.some(n=>n>1),'real cluster groups');
 const geometryId=await page.evaluate(()=>window.__mapInspection.markers[0].getGeometries().find(g=>String(g.id).startsWith('cluster-'))?.id);
 check(!!geometryId,'custom cluster markers drawn');
 await page.screenshot({path:'output/playwright/store-map-cluster-live.png'});
 await page.getByRole('textbox',{name:'关键词',exact:true}).fill('没有任何结果');await page.waitForTimeout(600);check(await page.locator('.store-card').count()===0,'empty result');
 await page.getByRole('button',{name:'重置',exact:true}).click();await page.waitForTimeout(500);
 await page.route('https://map.qq.com/api/gljs*',route=>route.abort());await page.reload();await page.waitForTimeout(1800);
 check(await page.getByText('地图暂时不可用',{exact:true}).count()===1,'sdk load failure shown');
 check(await page.locator('.store-card').count()>100,'list usable when sdk fails');
 await page.unroute('https://map.qq.com/api/gljs*');await page.getByRole('button',{name:'重新加载地图',exact:true}).click();await page.waitForTimeout(5000);
 check(await page.getByText('地图暂时不可用',{exact:true}).count()===0,'sdk retry works');
 check(await page.locator('.map-canvas canvas').count()===1,'real map after retry');
 return {passed:['real MarkerCluster aggregates dense points','custom cluster markers drawn','empty filter result','SDK failure list remains usable','SDK retry succeeds'],largestCluster:Math.max(...aggregation)};
}
