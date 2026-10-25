// Analytic SDF optics REWRITE for portability. Not the upstream canonical map/shader.
// Neither this module nor motion.js reads window/document or captures a page.
const VERTEX = `attribute vec2 a_position; varying vec2 v_uv;
void main(){v_uv=a_position*.5+.5;gl_Position=vec4(a_position,0.,1.);}`;
const FRAGMENT = `precision highp float;
uniform sampler2D u_background; uniform vec2 u_resolution,u_center,u_size;
uniform float u_strength,u_dispersion,u_specular; varying vec2 v_uv;
float sdf(vec2 p,vec2 b,float r){vec2 q=abs(p)-b+r;return length(max(q,0.))+min(max(q.x,q.y),0.)-r;}
float field(vec2 p){return sdf(p,u_size*.5,min(u_size.y*.5,u_size.x*.5));}
void main(){
 vec2 pixel=v_uv*u_resolution; vec2 p=pixel-u_center;
 float d=field(p); vec4 base=texture2D(u_background,v_uv);
 float shell=sdf(pixel-vec2(u_resolution.x*.5,48.),vec2(u_resolution.x*.5-3.,34.),34.);
 if(shell<0.) base.rgb=mix(base.rgb,vec3(.91),.12);
 if(d>0.){gl_FragColor=base;return;}
 vec2 normal=normalize(vec2(field(p+vec2(.5,0.))-field(p-vec2(.5,0.)),field(p+vec2(0.,.5))-field(p-vec2(0.,.5)))+vec2(.00001));
 float rim=1.-smoothstep(0.,19.,-d);
 vec2 delta=normal*rim*rim*u_strength/u_resolution;
 float red=texture2D(u_background,v_uv-delta*(1.+u_dispersion)).r;
 float green=texture2D(u_background,v_uv-delta).g;
 float blue=texture2D(u_background,v_uv-delta*(1.-u_dispersion)).b;
 vec3 col=mix(vec3(red,green,blue),vec3(.96),.09);
 float edge=exp(-abs(d+1.2)*1.5)*u_specular;
 col+=edge*(.35+.65*abs(dot(normal,normalize(vec2(-.7,1.)))));
 gl_FragColor=vec4(mix(base.rgb,col,1.-smoothstep(-1.2,.3,d)),1.);
}`;
function makeTexture(width = 384, height = 192, phase = 0, monochrome = false) {
  const pixels = new Uint8Array(width * height * 4);
  const colors = monochrome ? [[238,238,238],[85,85,85],[250,250,250],[160,160,160]] : [[224,94,74],[61,137,150],[242,191,84],[81,82,110]];
  for(let y=0;y<height;y++) for(let x=0;x<width;x++) {
    const off=(y*width+x)*4, band=Math.floor((x+phase*42)/48), c=colors[((band%4)+4)%4];
    const grid=(x%48<2||y%32<2), marker=((x+Math.floor(phase*32))%110<38 && y%64>18 && y%64<25);
    const k=grid?.55:marker?.25:1;
    pixels[off]=Math.round(c[0]*k);pixels[off+1]=Math.round(c[1]*k);pixels[off+2]=Math.round(c[2]*k);pixels[off+3]=255;
  }
  return {pixels,width,height};
}
function createRenderer(gl) {
  if(!gl) throw new Error('WebGL context unavailable');
  const shaders=[];
  const compile=(kind,source)=>{const shader=gl.createShader(kind);gl.shaderSource(shader,source);gl.compileShader(shader);
    if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS)){const error=gl.getShaderInfoLog(shader);gl.deleteShader(shader);throw new Error(error);}
    shaders.push(shader);return shader;};
  const program=gl.createProgram();
  gl.attachShader(program,compile(gl.VERTEX_SHADER,VERTEX));gl.attachShader(program,compile(gl.FRAGMENT_SHADER,FRAGMENT));gl.linkProgram(program);
  if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(program));
  const buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]),gl.STATIC_DRAW);
  gl.useProgram(program);const attribute=gl.getAttribLocation(program,'a_position');gl.enableVertexAttribArray(attribute);gl.vertexAttribPointer(attribute,2,gl.FLOAT,false,0,0);
  const names=['background','resolution','center','size','strength','dispersion','specular'];
  const u=Object.fromEntries(names.map(name=>[name,gl.getUniformLocation(program,'u_'+name)]));
  const texture=gl.createTexture();gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
  let tw=0,th=0,uploads=0,draws=0;
  return {
    upload({pixels,width,height}) {gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);
      if(width!==tw||height!==th){gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,width,height,0,gl.RGBA,gl.UNSIGNED_BYTE,pixels);tw=width;th=height;}
      else gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,width,height,gl.RGBA,gl.UNSIGNED_BYTE,pixels);
      uploads++;const error=gl.getError();if(error!==gl.NO_ERROR)throw new Error('Texture upload GL error '+error);
    },
    draw(width,height,shape,{strength=13,dispersion=.12,specular=0}={}) {
      gl.viewport(0,0,gl.drawingBufferWidth,gl.drawingBufferHeight);gl.useProgram(program);
      gl.uniform1i(u.background,0);gl.uniform2f(u.resolution,width,height);gl.uniform2f(u.center,shape.x,48);
      gl.uniform2f(u.size,shape.width,shape.height);gl.uniform1f(u.strength,strength);gl.uniform1f(u.dispersion,dispersion);gl.uniform1f(u.specular,specular);
      gl.drawArrays(gl.TRIANGLES,0,6);draws++;
    },
    diagnostics(){return {shader:'compiled-and-linked',uploads,draws,textureSize:[tw,th],version:gl.getParameter(gl.VERSION),renderer:gl.getParameter(gl.RENDERER),glError:gl.getError()};},
    destroy(){gl.deleteTexture(texture);gl.deleteBuffer(buffer);gl.deleteProgram(program);shaders.forEach(s=>gl.deleteShader(s));}
  };
}

module.exports = { VERTEX, FRAGMENT, makeTexture, createRenderer };
