import React,{useState,useRef} from 'react';import {createRoot} from 'react-dom/client';
import {LiquidGlass} from 'simple-liquid-glass';import './style.css';
function Snapshot(){const [count,setCount]=useState(0),[status,setStatus]=useState('pending'),[refresh,setRefresh]=useState('尚未手动刷新');const source=useRef(null),glass=useRef(null);
 return <main><header><a href="/">← 返回技术实验</a><span>原版 HTML → 缓存 → WebGL</span></header><section className="intro"><div><p className="eyebrow">DOM SNAPSHOT / EXPLICIT SOURCE</p><h1>只捕获指定背景。</h1></div><p>背景与镜片为兄弟节点。更新 DOM 后由原包观察器刷新；<br/>不对全页面逐帧截图。此路径仅为 H5 验证。</p></section>
 <div style={{position:'relative',height:350,maxWidth:760,overflow:'hidden'}}>
  <div ref={source} style={{position:'absolute',inset:0,background:count%2?'#ce785d':'#527f91',padding:20,color:'white',fontSize:24,lineHeight:1.7}}>
   <strong>DOM BACKDROP · {count}</strong><br/>PERKLY — 0123456789<br/>背景更新 {count} · 字体与直线折射<br/>ABCDEFGHIJKLMNOPQRSTUVWXYZ<br/>背景是普通 HTML，不是图片或 Canvas。
  </div>
  <div style={{position:'absolute',left:'12%',top:90,width:'76%',height:160}}>
   <LiquidGlass ref={glass} renderer="webgl" backdropRef={source} radius={32} quality="high" blur={0} frost={.03} scale={100} onDiagnosticsChange={v=>setStatus(JSON.stringify(v))}><div style={{padding:20}}>原版 WebGL 镜片</div></LiquidGlass>
  </div>
 </div><div className="controls"><button onClick={()=>setCount(v=>v+1)}>更新 DOM 背景</button><button onClick={async()=>{try{await glass.current.refreshBackdrop();setRefresh('refreshBackdrop resolved · '+count);}catch(e){setRefresh('FAILED: '+e.message);}}}>手动刷新快照</button></div>
 <pre data-testid="snapshot-status">{status}</pre><pre data-testid="refresh-status">{refresh}</pre><footer>原版库 simple-liquid-glass 5.4.0 · MIT / THIRD_PARTY_NOTICES.md</footer></main>;
}createRoot(document.getElementById('root')).render(<Snapshot/>);
