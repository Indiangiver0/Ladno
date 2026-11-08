const root=page.children.find(n=>n.name==='SC-01 · Обзор / desktop');
const main=shell(root,'Обзор',0);
const account=rounded(box(main,'Account / unknown opening balance',1136,'HORIZONTAL',16,16,'surface'),12);
const al=box(account,'Account name',400,'VERTICAL',0,0);text(al,'Основной · KZT',400,'Label');text(account,'Начальный баланс не задан · баланс счёта неизвестен',672,'Body','muted');
const heroRow=box(main,'Budget and monthly totals',1136,'HORIZONTAL',0,24);
const budget=card(heroRow,'Budget / October',720,28,16,'dark');budget.strokes=[];
const bh=box(budget,'Budget heading',664,'HORIZONTAL',0,12);text(bh,'Бюджет октября',484,'Heading','white');badge(bh,'В пределах',168);
text(budget,'Остаток бюджета',664,'Body','white');text(budget,'50 000,00 KZT',664,'Display','white');
const pf=box(budget,'Plan and spent',664,'HORIZONTAL',0,24);for(const [label,value]of [['План бюджета','350 000,00 KZT'],['Потрачено','300 000,00 KZT']]){const c=box(pf,label,320,'VERTICAL',0,4);text(c,label,320,'Caption','white');raw(c,value,320,20,'white','SemiBold');}
progress(budget,664,6/7,'green',8);
text(budget,'Остаток бюджета — разница плана и расходов месяца, не баланс счёта',664,'Body','white');
const totals=box(heroRow,'Monthly summary',392,'VERTICAL',0,16);kpi(totals,'Доходы месяца','500 000,00 KZT','01.10–31.10.2026',392);kpi(totals,'Расходы месяца','300 000,00 KZT','01.10–31.10.2026',392);
const tip=card(totals,'Demo note',392,20,8,'tint');text(tip,'Ваш месяц — в одном месте',352,'Label','green');note(tip,'Синтетические данные для знакомства с приложением.',352);
const category=card(main,'Budget categories',1136,24,16);
const catHead=box(category,'Categories heading',1088,'HORIZONTAL',0,24);text(catHead,'Расходы по категориям',680,'Heading');text(catHead,'Факт / план · KZT',384,'Caption','muted').textAlignHorizontal='RIGHT';
const cats=box(category,'Category comparisons',1088,'HORIZONTAL',0,24);
for(const [label,spent,planned,ratio]of [['Жильё','150 000,00','150 000,00',1],['Продукты','120 000,00','150 000,00',0.8],['Транспорт','30 000,00','50 000,00',0.6]]){const c=box(cats,label,346.67,'VERTICAL',0,8);text(c,label,346.67,'Label');text(c,spent+' / '+planned+' KZT',346.67,'Body','muted');progress(c,346.67,ratio);}
const bottom=box(main,'Goals and debts / separate records',1136,'HORIZONTAL',0,24);
const goal=card(bottom,'Goal preview',720,24,12);const gh=box(goal,'Goal heading',672,'HORIZONTAL',0,24);text(gh,'Цели',400,'Heading');text(gh,'Все цели →',248,'Label','green').textAlignHorizontal='RIGHT';
text(goal,'Подушка безопасности',672,'Label');const ga=box(goal,'Goal progress values',672,'HORIZONTAL',0,24);text(ga,'180 000,00 KZT из 600 000,00 KZT',548,'Body');text(ga,'30%',100,'Heading','green').textAlignHorizontal='RIGHT';progress(goal,672,0.3);text(goal,'На 30.10.2026 · накоплено до октября',672,'Caption','muted');
const debts=card(bottom,'Debt preview',392,24,12);text(debts,'Долги · на 30.10.2026',344,'Heading');for(const[label,value]of[['Я должен','80 000,00 KZT'],['Мне должны','50 000,00 KZT']]){const r=box(debts,label,344,'HORIZONTAL',0,12);text(r,label,144,'Body','muted');text(r,value,188,'Label').textAlignHorizontal='RIGHT';}text(debts,'Все долги →',344,'Label','green');
text(main,'Цели и долги учитываются отдельно от счёта и бюджета.',1136,'Caption','muted');
return results([root]);
