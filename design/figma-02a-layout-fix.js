// Targeted repair after the first SC-01 composition screenshot.
const page=await figma.getNodeByIdAsync('0:1');await figma.setCurrentPageAsync(page);
for(const f of [{family:'Manrope',style:'Regular'},{family:'Manrope',style:'Medium'},{family:'Manrope',style:'SemiBold'},{family:'Inter',style:'Regular'}])await figma.loadFontAsync(f);
const root=await figma.getNodeByIdAsync('6:222');const ids=[];
for(const n of root.findAllWithCriteria({types:['FRAME']})){if(n.layoutMode==='HORIZONTAL'&&n.name!=='Progress / track'){n.primaryAxisSizingMode='FIXED';n.counterAxisSizingMode='AUTO';ids.push(n.id);}}
for(const t of root.findAllWithCriteria({types:['TEXT']})){if(t.parent.type==='INSTANCE')continue;t.textAutoResize='HEIGHT';ids.push(t.id);}
const tip=await figma.getNodeByIdAsync('7:95');const muted=await figma.variables.getVariableByIdAsync('VariableID:6:188');if(tip?.type==='TEXT'){tip.fills=[figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:0.4,g:0.44,b:0.41}},'color',muted)];ids.push(tip.id);}
return{mutatedNodeIds:ids,frame:{id:root.id,w:root.width,h:root.height},main:root.children[1].children.map(n=>({id:n.id,name:n.name,y:n.y,w:n.width,h:n.height}))};
