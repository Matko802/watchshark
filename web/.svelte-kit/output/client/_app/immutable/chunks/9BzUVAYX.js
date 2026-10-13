const f=[...` 	
\r\f \v\uFEFF`];function h(s,i,u){var n=s==null?"":""+s;if(u){for(var t in u)if(u[t])n=n?n+" "+t:t;else if(n.length)for(var e=t.length,l=0;(l=n.indexOf(t,l))>=0;){var r=l+e;(l===0||f.includes(n[l-1]))&&(r===n.length||f.includes(n[r]))?n=(l===0?"":n.substring(0,l))+n.substring(r+1):l=r}}return n===""?null:n}function o(s,i){return s==null?null:String(s)}export{o as a,h as t};
