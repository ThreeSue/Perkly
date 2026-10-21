// 已停用官方演示站点。业务默认使用本地 mock，真实接口由组员提供。
export const proxy = { dev: { target:'http://127.0.0.1:8080/api', changeOrigin:true, rewrite:(path:string)=>path.replace('/dev','') }, prod:{ target:'http://127.0.0.1:8080',changeOrigin:true,rewrite:(path:string)=>path.replace('/prod','/api') } };
export const value='dev';
