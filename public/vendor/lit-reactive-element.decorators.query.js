/**
 * Bundled by jsDelivr using Rollup v4.62.2 and esbuild v0.28.1.
 * Original file: /npm/@lit/reactive-element@2.1.2/decorators/query.js
 *
 * Do NOT use SRI with dynamically generated files! More information: https://www.jsdelivr.com/using-sri-with-dynamic-files
 */
const s=(o,r,e)=>(e.configurable=!0,e.enumerable=!0,Reflect.decorate&&typeof r!="object"&&Object.defineProperty(o,r,e),e);function h(o,r){return(e,n,i)=>{const l=u=>u.renderRoot?.querySelector(o)??null;if(r){const{get:u,set:c}=typeof n=="object"?e:i??(()=>{const t=Symbol();return{get(){return this[t]},set(a){this[t]=a}}})();return s(e,n,{get(){let t=u.call(this);return t===void 0&&(t=l(this),(t!==null||this.hasUpdated)&&c.call(this,t)),t}})}return s(e,n,{get(){return l(this)}})}}export{h as query};
//# sourceMappingURL=/sm/38153bf7d3e7076c230dd16b54f41bb08e452072cfafe75b5c53f098df037bba.map