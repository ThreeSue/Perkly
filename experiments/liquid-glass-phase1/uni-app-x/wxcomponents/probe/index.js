const motionCore=require('./generated/motion');
const {createRenderer,makeTexture}=require('./generated/renderer');
const labels=['首页','发现','券包','我的'];
Component({
 data:{dynamic:false,specular:false,sampling:false,selected:'首页',report:'等待本机 Canvas / WebGL 初始化…'},
 lifetimes:{attached(){this._dead=false;this._timers=[];this.initialize();},detached(){this.cleanup();}},
 pageLifetimes:{hide(){this.stop();},show(){if(this._renderer&&!this._dead){this.scheduleTexture();this.wake();}}},
 methods:{
  initialize(){
   const device=wx.getDeviceInfo?wx.getDeviceInfo():wx.getSystemInfoSync();
   const system=wx.getWindowInfo?wx.getWindowInfo():wx.getSystemInfoSync();
   const app=wx.getAppBaseInfo?wx.getAppBaseInfo():wx.getSystemInfoSync();
   this._evidence={environment:'actual WeChat runtime when this component executes',platform:device.platform,model:device.model,system:device.system,SDKVersion:app.SDKVersion,pixelRatio:system.pixelRatio,
    canvas2d:'pending',webgl:'pending',shader:'pending',texture:'pending',backgroundSource:'controlled CPU RGBA; NOT native-view capture',DOMCapture:'not-used / unavailable as a browser-DOM pipeline',nativeBackgroundCapture:'not tested; dedicated Skyline subtree probe still required',touchEvents:0,draws:0,uploads:0,bytes:0,captureCount:0,renderScale:Math.min(system.pixelRatio||1,2)};
   this.createSelectorQuery().select('#context2d').fields({node:true}).exec(result=>{
    try{const canvas=result[0]&&result[0].node,ctx=canvas&&canvas.getContext('2d');if(!ctx)throw new Error('2D context missing');ctx.fillRect(0,0,1,1);this._evidence.canvas2d='initialized-and-drawn';}catch(e){this._evidence.canvas2d='failed: '+e.message;}this.publish();
   });
   this.createSelectorQuery().select('#probeCanvas').fields({node:true,size:true,rect:true}).exec(result=>{
    try{if(this._dead)return;const node=result[0];if(!node||!node.node)throw new Error('Canvas node unavailable');
     this._canvas=node.node;this._width=node.width;this._left=node.left||0;this._top=node.top||0;
     this._canvas.width=Math.round(node.width*this._evidence.renderScale);this._canvas.height=220*this._evidence.renderScale;
     const gl=this._canvas.getContext('webgl',{alpha:false,antialias:false,preserveDrawingBuffer:true});
     if(!gl)throw new Error('WebGL context unavailable');this._evidence.webgl='initialized';
     this._renderer=createRenderer(gl);this._evidence.shader='compiled-and-linked';this._motion=motionCore.createMotion(node.width);this._phase=0;this._last=0;this._samples=[];this._uploadSamples=[];this._frameSamples=[];this._touchSamples=[];
     this.updateTexture();this.publish();
    }catch(e){this._evidence.error=e.message;this.publish();}
   });
  },
  now(){return Date.now();},
  raf(callback){return this._canvas.requestAnimationFrame(callback);},
  wake(){if(!this._raf&&!this._dead&&this._canvas)this._raf=this.raf(()=>this.draw());},
  draw(){
   if(this._dead||!this._renderer)return;this._raf=0;
   const now=this.now(),dt=this._last?(now-this._last)/1000:1/60;this._last=now;
   const moving=motionCore.step(this._motion,dt);
   const before=this.now();try{this._renderer.draw(this._width,220,motionCore.geometry(this._motion),{strength:13,dispersion:.12,specular:this.data.specular?.2:0});this._evidence.draws++;}
   catch(e){this._evidence.error=e.message;this.publish();return;}
   if(this.data.sampling){this._samples.push(this.now()-before);if(this._frameTime)this._frameSamples.push(now-this._frameTime);this._frameTime=now;if(this._touchPending){this._touchSamples.push(now-this._touchPending);this._touchPending=0;}}
   if(moving||this.data.sampling)this.wake();else this.publish();
  },
  updateTexture(){if(this._dead||!this._renderer)return;try{
   const image=makeTexture(384,192,this._phase),before=this.now();this._renderer.upload(image);
   this._evidence.uploads++;this._evidence.bytes+=image.pixels.byteLength;this._evidence.texture='texImage2D / texSubImage2D success';
   if(this.data.sampling)this._uploadSamples.push(this.now()-before);this.wake();
  }catch(e){this._evidence.error=e.message;this.publish();}},
  scheduleTexture(){clearInterval(this._textureTimer);if(!this.data.dynamic)return;this._textureTimer=setInterval(()=>{this._phase+=.1;this.updateTexture();},100);},
  toggleDynamic(){this.setData({dynamic:!this.data.dynamic},()=>{this.scheduleTexture();this.publish();});},
  toggleSpecular(){this.setData({specular:!this.data.specular},()=>this.wake());},
  touchX(event,end=false){const points=end?event.changedTouches:event.touches;const point=points&&points[0];if(!point)return this._motion.target;
   return typeof point.x==='number'?point.x:point.clientX-this._left;},
  touchStart(event){if(!this._motion)return;const p=event.touches[0];const y=typeof p.y==='number'?p.y:p.clientY-this._top;if(y<130)return;this._tracking=true;this._evidence.touchEvents++;this._touchPending=this.now();motionCore.press(this._motion,this.touchX(event),this._touchPending);this.wake();},
  touchMove(event){if(!this._tracking)return;this._evidence.touchEvents++;this._touchPending=this.now();motionCore.move(this._motion,this.touchX(event),this._touchPending);this.wake();},
  touchEnd(event){if(!this._tracking)return;this._tracking=false;motionCore.release(this._motion,this.touchX(event,true));this.setData({selected:labels[this._motion.selected]});this.wake();},
  touchCancel(){if(!this._tracking)return;this._tracking=false;motionCore.release(this._motion,this._motion.target,true);this.setData({selected:labels[this._motion.selected]});this.wake();},
  sample(){if(!this._renderer||this.data.sampling)return;this._samples=[];this._uploadSamples=[];this._frameSamples=[];this._touchSamples=[];this._touchPending=0;this._frameTime=0;this.setData({sampling:true},()=>this.wake());
   const timer=setTimeout(()=>{if(this._dead)return;this.setData({sampling:false});this._evidence.sample={durationMs:6000,drawCount:this._samples.length,cpuSubmitP95Ms:this.p95(this._samples),uploadCpuP95Ms:this.p95(this._uploadSamples),frameIntervalP95Ms:this.p95(this._frameSamples),latestTouchToSubmitP95Ms:this.p95(this._touchSamples),timingPrecision:'Date.now millisecond; CPU submission only, NOT GPU/display latency',touchEvents:this._evidence.touchEvents};this.publish();},6000);this._timers.push(timer);
  },
  p95(values){if(!values.length)return null;const sorted=values.slice().sort((a,b)=>a-b);return sorted[Math.min(sorted.length-1,Math.floor(sorted.length*.95))];},
  publish(){if(this._dead||!this._evidence)return;const result={...this._evidence,renderer:this._renderer?this._renderer.diagnostics():null,physicalDevice:this._evidence.platform!=='devtools',capturedAt:new Date().toISOString()};this._result=result;this.setData({report:JSON.stringify(result,null,2)});},
  exportResults(){this.publish();wx.setClipboardData({data:JSON.stringify(this._result,null,2)});},
  stop(){clearInterval(this._textureTimer);if(this._raf&&this._canvas)this._canvas.cancelAnimationFrame(this._raf);this._raf=0;this._last=0;},
  cleanup(){this._dead=true;this.stop();this._timers.forEach(clearTimeout);if(this._renderer)this._renderer.destroy();}
 }
});
