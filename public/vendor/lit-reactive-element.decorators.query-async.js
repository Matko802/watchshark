/**
 * Bundled by jsDelivr using Rollup v4.62.2 and esbuild v0.28.1.
 * Original file: /npm/@lit/reactive-element@2.1.2/decorators/query-async.js
 *
 * Do NOT use SRI with dynamically generated files! More information: https://www.jsdelivr.com/using-sri-with-dynamic-files
 */
const n=(r,t,e)=>(e.configurable=!0,e.enumerable=!0,Reflect.decorate&&typeof t!="object"&&Object.defineProperty(r,t,e),e);function o(r){return(t,e)=>n(t,e,{async get(){return await this.updateComplete,this.renderRoot?.querySelector(r)??null}})}export{o as queryAsync};
//# sourceMappingURL=/sm/668748f579d9e7e03b183768727b4fcba6ebd3d1d018a6e8a0265df6bd5da8a6.map