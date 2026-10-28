async (page) => {
 if(!await page.getByRole('dialog').count()){await page.locator('[data-store-id="1"]').click();await page.getByRole('button',{name:'编辑位置',exact:true}).click();await page.waitForTimeout(500);}
 const before=await page.locator('.coordinates').innerText();
 const box=await page.getByRole('dialog').locator('.map-canvas').boundingBox();
 await page.mouse.move(box.x+box.width/2,box.y+box.height/2-18);
 await page.mouse.down(); await page.mouse.move(box.x+box.width/2+65,box.y+box.height/2-40,{steps:16}); await page.mouse.up();
 await page.waitForTimeout(900);
 const dragged=await page.locator('.coordinates').innerText();
 await page.getByRole('dialog').locator('.map-canvas').click({position:{x:180,y:180}});
 await page.waitForTimeout(900);
 const clicked=await page.locator('.coordinates').innerText();
 return {realSdk:true,before,dragChanged:before!==dragged,clickChanged:dragged!==clicked,saveDisabled:await page.getByRole('button',{name:'确认保存位置',exact:true}).isDisabled(),mapUnavailable:await page.getByText('地图暂时不可用',{exact:true}).count()};
}
