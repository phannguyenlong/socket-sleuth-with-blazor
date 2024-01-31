// ==UserScript==
// @name         Blazor Dump Script
// @namespace    http://tampermonkey.net/
// @version      2024-01-31
// @description  Extract blazor code
// @author       You
// @match        http://partner-stag.fpt.net/tl-tsd
// @icon         https://www.google.com/s2/favicons?sz=64&domain=fpt.net
// @grant        none
// @run-at         document-start
// ==/UserScript==

// replace string
let INSERT_FRAME_MATCH = "insertFrameRange(e,t,n,r,o,s,i){const a=r;for(let a=s;a<i;a++){const s=e.referenceFramesEntry(o,a);r+=this.insertFrame(e,t,n,r,o,s,a),a+=ae(e,s)}return r-a}";
let INSERT_FRAME_REPLACE = "insertFrameRange(e,t,n,r,o,s,i){console.info(n.outerHTML);const a=r;for(let a=s;a<i;a++){const s=e.referenceFramesEntry(o,a);r+=this.insertFrame(e,t,n,r,o,s,a),a+=ae(e,s)}return r-a}";
let INSERT_textContent_MATCH = "textContent(e){const t=bn(this.batchDataUint8,e+4);return this.stringReader.readString(t)}"
let INSERT_textContent_REPLACE = "textContent(e){const t=bn(this.batchDataUint8,e+4);console.info(this.stringReader.readString(t));return this.stringReader.readString(t)}"


window.addEventListener('beforescriptexecute', function(e) {
    // get js file blazor.server.js
    let src = e.target.src;
    if (src.search(/blazor.server\.js/) != -1) {
        e.preventDefault();
		e.stopPropagation();
        console.log("We found itttttttttt!");
        console.log(e.target.src);
        fetch("http://partner-stag.fpt.net/tl-tsd/_framework/blazor.server.js").then(res => res = res.text()).then(res => {
            let newScript = res;
            //========================replace here========================
            newScript = replaceStr(newScript, INSERT_FRAME_MATCH, INSERT_FRAME_REPLACE);
            newScript = replaceStr(newScript, INSERT_textContent_MATCH, INSERT_textContent_REPLACE);
            append(newScript);
        });
	};
})

////// append with new block function:
function replaceStr(source, matchTarget, replaceTarget) {
    let newSource = source.replace(matchTarget, replaceTarget);
    return newSource;
}

////// append with new block function:
function append(s) {
      document.head.appendChild(document.createElement('script'))
             .innerHTML = s.toString().replace(/^function.*{|}$/g, '');
}