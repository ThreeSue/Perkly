async (page) => {
 const check=(v,m)=>{if(!v)throw new Error(m)};let fail=true;
 await page.route('**/merchant/workspace/store/map',async route=>{await route.fulfill({json:fail?{code:1001,message:'模拟门店加载失败'}:{code:1000,data:{coordinateSystem:'GCJ-02',total:0,locatedCount:0,unlocatedCount:0,list:[]}}});});
 await page.getByRole('button',{name:'刷新',exact:true}).click();await page.waitForTimeout(400);
 check(await page.getByText('模拟门店加载失败',{exact:true}).count()===1,'map api error shown');
 fail=false;await page.getByRole('button',{name:'重新加载门店',exact:true}).click();await page.waitForTimeout(400);
 check((await page.locator('.map-summary').innerText()).includes('0 家'),'api retry clears error');
 check(await page.getByText('模拟门店加载失败',{exact:true}).count()===0,'old error cleared');
 return {passed:['map API failure state','map API retry','zero result counters']};
}
