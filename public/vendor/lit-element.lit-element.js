/**
 * Bundled by jsDelivr using Rollup v4.62.2 and esbuild v0.28.1.
 * Original file: /npm/lit-element@4.2.2/lit-element.js
 *
 * Do NOT use SRI with dynamically generated files! More information: https://www.jsdelivr.com/using-sri-with-dynamic-files
 */
import{ReactiveElement as o}from"./lit-reactive-element._esm.js";export*from"./lit-reactive-element._esm.js";import{render as i,noChange as d}from"./lit-html._esm.js";export*from"./lit-html._esm.js";const s=globalThis;class t extends o{constructor(){super(...arguments),this.renderOptions={host:this},this._$Do=void 0}createRenderRoot(){const e=super.createRenderRoot();return this.renderOptions.renderBefore??=e.firstChild,e}update(e){const r=this.render();this.hasUpdated||(this.renderOptions.isConnected=this.isConnected),super.update(e),this._$Do=i(r,this.renderRoot,this.renderOptions)}connectedCallback(){super.connectedCallback(),this._$Do?.setConnected(!0)}disconnectedCallback(){super.disconnectedCallback(),this._$Do?.setConnected(!1)}render(){return d}}t._$litElement$=!0,t.finalized=!0,s.litElementHydrateSupport?.({LitElement:t});const l=s.litElementPolyfillSupport;l?.({LitElement:t});const c={_$AK:(n,e,r)=>{n._$AK(e,r)},_$AL:n=>n._$AL};(s.litElementVersions??=[]).push("4.2.2");export{t as LitElement,c as _$LE};
//# sourceMappingURL=/sm/f6e82ee5f7ca120d2d8229a370cde0a077716322f427ace795bd55a80d8e3c8e.map