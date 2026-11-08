// Pure data-model execution only. No DOM, browser or UI actions.
const fs=require('fs'),vm=require('vm'),path=require('path');
const root=path.resolve(__dirname,'../../..');
const ctx={window:{document:{},BigInt:BigInt}};
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(root,'android-app/app/src/main/assets/www/view.js'),'utf8'),ctx);
const input=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
const output=input.map(test=>({id:test.id,actual:ctx.window.AppView.chartModel(test.state)}));
ctx.window.BigInt=undefined;
output.push({id:'NO-BIGINT',actual:ctx.window.AppView.chartModel(input[0].state)});
fs.writeFileSync(process.argv[3],JSON.stringify(output,null,2)+'\n');
console.log('Pure chart models executed: '+output.length);
