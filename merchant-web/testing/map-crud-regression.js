async (page) => {
 if(!await page.getByRole('textbox',{name:'门店名称*',exact:true}).count()) await page.getByRole('button',{name:'新增',exact:true}).click();
 const check=(v,m)=>{if(!v)throw new Error(m)};const saves=[];let locationWrites=0;
 await page.route('**/merchant/workspace/store/save',async route=>{saves.push(route.request().postDataJSON());await route.fulfill({json:{code:1000,data:true}})});
 await page.route('**/merchant/workspace/store/location/update',async route=>{locationWrites++;await route.fulfill({json:{code:1000,data:true}})});
 await page.route('**/merchant/workspace/map/suggest?**',route=>route.fulfill({json:{code:1000,data:{list:[{id:'zero',title:'测试选点',address:'确认后的门店地址',longitude:0,latitude:0}]}}}));
 await page.getByRole('textbox',{name:'门店名称*',exact:true}).fill('新增位置测试店');
 await page.getByRole('textbox',{name:'联系电话*',exact:true}).fill('13800000000');
 await page.getByRole('combobox',{name:'门店类型*',exact:true}).locator('xpath=ancestor::div[contains(@class,"el-select__wrapper")]').click();await page.getByRole('option',{name:'餐饮门店',exact:true}).click();
 await page.getByRole('button',{name:'地图选点 / 核对位置',exact:true}).click();
 await page.getByRole('textbox',{name:'搜索地点',exact:true}).fill('测试');await page.waitForTimeout(650);await page.locator('.place-item').click();
 await page.getByText('我已核对地图位置和详细地址',{exact:true}).click();await page.getByRole('button',{name:'应用到门店表单',exact:true}).click();await page.waitForTimeout(350);
 check(saves.length===0&&locationWrites===0,'apply not write');
 check((await page.locator('.location-field').innerText()).includes('经度 0，纬度 0'),'form zero coordinates retained');
 await page.getByRole('button',{name:'保存',exact:true}).click();await page.waitForTimeout(550);
 check(saves.length===1,'new store submitted');check(saves[0].longitude===0&&saves[0].latitude===0&&saves[0].address==='确认后的门店地址','new store save location');check(!('merchantId'in saves[0])&&!('_locationPicker'in saves[0]),'no cross merchant or virtual payload');
 await page.getByRole('button',{name:'编辑',exact:true}).click();await page.waitForTimeout(450);
 check((await page.locator('.location-field').innerText()).includes('119.29'),'existing coordinates shown');
 await page.getByRole('textbox',{name:'详细地址*',exact:true}).fill('手动修改后的地址');await page.getByRole('button',{name:'保存',exact:true}).click();await page.waitForTimeout(350);
 check(saves.length===1,'changed address requires map recheck');
 await page.getByRole('button',{name:'地图选点 / 核对位置',exact:true}).click();
 await page.getByText('我已核对地图位置和详细地址',{exact:true}).click();await page.getByRole('button',{name:'应用到门店表单',exact:true}).click();await page.waitForTimeout(350);
 await page.getByRole('button',{name:'保存',exact:true}).click();await page.waitForTimeout(500);
 check(saves.length===2&&saves[1].id===1&&saves[1].longitude===119.29,'existing store full edit preserves confirmed coordinates');
 await page.getByRole('button',{name:'门店位置',exact:true}).click();await page.waitForTimeout(400);
 await page.getByRole('button',{name:'取消',exact:true}).click();await page.waitForTimeout(350);check(locationWrites===0,'cancel position no write');
 return {passed:['new form preview no write','zero coordinates in original store/save','no merchant or virtual fields','existing edit address recheck','confirmed edit preserves coordinates','standalone location cancel no write'],saveCount:saves.length};
}
