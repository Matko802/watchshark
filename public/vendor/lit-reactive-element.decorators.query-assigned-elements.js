/**
 * Bundled by jsDelivr using Rollup v4.62.2 and esbuild v0.28.1.
 * Original file: /npm/@lit/reactive-element@2.1.2/decorators/query-assigned-elements.js
 *
 * Do NOT use SRI with dynamically generated files! More information: https://www.jsdelivr.com/using-sri-with-dynamic-files
 */
const a=(t,r,e)=>(e.configurable=!0,e.enumerable=!0,Reflect.decorate&&typeof r!="object"&&Object.defineProperty(t,r,e),e);function i(t){return(r,e)=>{const{slot:n,selector:o}=t??{},c="slot"+(n?`[name=${n}]`:":not([name])");return a(r,e,{get(){const l=this.renderRoot?.querySelector(c),s=l?.assignedElements(t)??[];return o===void 0?s:s.filter(u=>u.matches(o))}})}}export{i as queryAssignedElements};
//# sourceMappingURL=/sm/82bef53d63885d27323f1364488e25a0e0f3c23e2fdb0e620e691134c27c3fb8.map