const P=require("../../js/pron.js");const R=require("./"+process.argv[2]);
const H=s=>s.replace(/[^가-힣A-Za-z]/g,"");
function lcs(a,b){const m=a.length,n=b.length;let prev=new Array(n+1).fill(0);for(let i=1;i<=m;i++){const cur=new Array(n+1).fill(0);for(let j=1;j<=n;j++)cur[j]=a[i-1]===b[j-1]?prev[j-1]+1:Math.max(prev[j],cur[j-1]);prev=cur}return prev[n]}
let rows=[];for(const r of R){const a=H(r.chunk),b=H(P.say(r.heard));const l=lcs(a,b);
 const tail=H(r.chunk).slice(-3), endHeard=b.slice(-4); rows.push({id:r.id,miss:a.length-l,len:a.length,sps:r.sps,endok:b.includes(tail)||endHeard.includes(tail.slice(-1)),chunk:r.chunk,heard:r.heard})}
const tot=rows.reduce((s,r)=>s+r.len,0),miss=rows.reduce((s,r)=>s+r.miss,0);
console.log(process.argv[2],"syllables",tot,"missing",miss,(miss/tot*100).toFixed(1)+"%","endBad",rows.filter(r=>!r.endok).length,"/",rows.length);
const n=+(process.argv[3]||0);rows.sort((x,y)=>y.miss/y.len-x.miss/x.len);for(const r of rows.slice(0,n))console.log(`\n[${r.id}] miss ${r.miss}/${r.len} sps ${r.sps}\n TEXT : ${r.chunk}\n HEARD: ${r.heard}`);
for(const r of rows.filter(r=>!r.endok).slice(0,6))console.log("\nEND? ",r.chunk.slice(-30),"| HEARD:",r.heard.slice(-30));
