// Only synthetic browser fixtures. No database connection, API key, or cloud call.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const dist=path.resolve(__dirname,'../dist');
const profile={customerId:90001,revision:1,cutoff:'2026-11-10T08:00:00Z',updatedAt:'2026-11-10T08:01:00Z',state:'COMPLETED',modelVersion:'qwen3.8-flash',promptVersion:'profile-grounded-v1',errorCode:null,
 evidence:{batch:{facts:[],issues:[]},categoryNames:{1:'咖啡与烘焙',2:'餐饮'},statistics:{sufficiency:'SUPPORTED',approximateTimeVisits:0,windows:[30,90,180].map(days=>({days,visits:8,freeClaims:12,paidPurchases:3,knownAmountVisits:7,unknownAmountVisits:1,consumptionCents:32600,purchaseSpendCents:2900,categories:[{categoryId:1,visits:6,meanGapDays:7,lastVisit:'2026-11-08T04:00:00Z'},{categoryId:2,visits:2,meanGapDays:14,lastVisit:'2026-11-02T04:00:00Z'}],priceBands:[{name:'0–50元',visits:5},{name:'50–150元',visits:2},{name:'150元以上',visits:0}]}))}},interpretation:{sufficiency:'SUPPORTED',tags:[{label:'咖啡',quote:'虚构咖啡店提供现磨咖啡与面包',contentKey:'DEMO',factKeys:['DEMO1','DEMO2']}]}};
const user={id:90001,nickName:'画像演示用户（虚构）',status:1,createTime:'2026-10-01 10:00:00',wechatBound:0,passwordSet:0,historyPending:0};
function api(route,p){
 if(route.endsWith('/permmenu'))return {menus:[{id:1,name:'用户列表',router:'/user/list',viewPath:'modules/user/views/list.vue',type:1,isShow:true,keepAlive:false}],perms:['user:info:page','user:info:info','profile:read','profile:update']};
 if(route.endsWith('/person'))return {id:1,username:'admin',name:'布局验收（虚构）'};
 if(route==='/admin/user/info/page')return {list:[user],pagination:{page:1,size:20,total:1}};
 if(route==='/admin/profile/info')return profile;
 if(route==='/admin/profile/correction'){profile.excludedLabels=p.excludedLabels;profile.interpretation.tags=p.excludedLabels.includes('咖啡')?[]:[{label:'咖啡',quote:'虚构咖啡店提供现磨咖啡与面包',contentKey:'DEMO',factKeys:['DEMO1','DEMO2']}];return profile;}
 if(route==='/admin/profile/refresh'){profile.state='PENDING';setTimeout(()=>profile.state='COMPLETED',1500);return profile;}
 if(route==='/admin/profile/preview')return {list:[{id:1,name:'虚构下午茶券',merchantName:'虚构咖啡店',salePrice:990,recommendationReason:'参考近期业务偏好'}],total:1};
 if(route.endsWith('/eps'))return {};
 if(route.endsWith('/count'))return 0;
 if(route.endsWith('/page'))return {list:[],pagination:{page:1,size:20,total:0}};
 return [];
}
http.createServer(async(req,res)=>{
 const url=new URL(req.url,'http://127.0.0.1:19045');
 if(url.pathname==='/__start'){res.setHeader('content-type','text/html; charset=utf-8');return res.end(`<script>const data={token:'synthetic-ui-token',refreshToken:'synthetic',token_deadtime:Date.now()+3600000,refreshToken_deadtime:Date.now()+3600000,userInfo:{id:1,username:'admin',name:'布局验收'}};for(const[k,v]of Object.entries(data))localStorage.setItem(k,JSON.stringify(v));sessionStorage.setItem('perkly.workspace','PLATFORM');location.replace('/user/list');</script>`);}
 if(url.pathname.startsWith('/api/')){let body='';for await(const b of req)body+=b;let p=Object.fromEntries(url.searchParams);if(body)try{Object.assign(p,JSON.parse(body))}catch{}res.setHeader('content-type','application/json');return res.end(JSON.stringify({code:1000,data:api(url.pathname.slice(4),p)}));}
 const file=path.resolve(dist,'.'+decodeURIComponent(url.pathname));if(!file.startsWith(dist+path.sep)){res.writeHead(403);return res.end();}
 const target=fs.existsSync(file)&&fs.statSync(file).isFile()?file:path.join(dist,'index.html');res.setHeader('content-type',({'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.woff2':'font/woff2'})[path.extname(target)]||'application/octet-stream');fs.createReadStream(target).pipe(res);
}).listen(19045,'127.0.0.1',()=>console.log('Synthetic profile UI fixture listening at http://127.0.0.1:19045/__start'));
