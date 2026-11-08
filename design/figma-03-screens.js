const roots=[];
// SC-02: exactly four D1 operations. The validation example is not a saved row.
{
const root=page.children.find(n=>n.name==='SC-02 · Операции / desktop');roots.push(root);const main=shell(root,'Операции',1);
const sum=box(main,'Monthly totals',1136,'HORIZONTAL',0,24);kpi(sum,'Доходы месяца','500 000,00 KZT','01.10–31.10.2026',556);kpi(sum,'Расходы месяца','300 000,00 KZT','01.10–31.10.2026',556);
const list=card(main,'Operation list / D1',1136,24,20);const lh=box(list,'List heading',1088,'HORIZONTAL',0,24);text(lh,'История операций',700,'Heading');text(lh,'4 операции',364,'Caption','muted').textAlignHorizontal='RIGHT';
const filters=box(list,'Filters',1088,'HORIZONTAL',0,16);input(filters,'Поиск по категории','Найти операцию',400);const f=box(filters,'Type filter',320,'VERTICAL',0,10);text(f,'Тип операции',320,'Caption','muted');button(f,'Все типы',320,'Neutral');const a=box(filters,'Account filter',336,'VERTICAL',0,10);text(a,'Счёт',336,'Caption','muted');button(a,'Основной · KZT',336,'Neutral');
const headers=box(list,'Table column headers',1088,'HORIZONTAL',12,24,'canvas');for(const [v,w]of[['Дата',132],['Категория и тип',390],['Счёт',180],['Сумма',290]])text(headers,v,w,'Caption','muted');
for(const [date,type,name,amount,income]of[['25.10.2026','Доход','Зарплата','500 000,00 KZT',true],['20.10.2026','Расход','Транспорт','30 000,00 KZT',false],['12.10.2026','Расход','Продукты','120 000,00 KZT',false],['01.10.2026','Расход','Жильё','150 000,00 KZT',false]]){const row=box(list,'Operation / '+date+' / '+name,1088,'HORIZONTAL',12,24);row.counterAxisAlignItems='CENTER';text(row,date,132,'Body','muted');const desc=box(row,'Category and kind',390,'VERTICAL',0,4);text(desc,name,390,'Label');text(desc,type,390,'Caption',income?'green':'muted');text(row,'Основной',180,'Body','muted');text(row,amount,290,'Label',income?'green':'ink').textAlignHorizontal='RIGHT';rule(list,1088);}
note(main,'Фильтры меняют список операций. Бюджет всего месяца остаётся прежним.',1136);
const info=card(main,'Account information',1136,24,8,'tint');text(info,'Основной · KZT · начальный баланс не задан',1088,'Label','green');note(info,'Доходы и расходы показывают движение за месяц. Для баланса счёта нужен начальный остаток.',1088);
}
// SC-03: manually recorded goal, with an explicit independent-account explanation.
{
const root=page.children.find(n=>n.name==='SC-03 · Цели / desktop');roots.push(root);const main=shell(root,'Цели',2,'На 30.10.2026','Создать цель');
const intro=box(main,'Goal introduction',1136,'VERTICAL',0,8);text(intro,'Двигайтесь к важному в своём темпе',1136,'Heading');note(intro,'Отмечайте накопленное и следите за прогрессом каждой цели.',1136);
const row=box(main,'Goals and explanation',1136,'HORIZONTAL',0,24);
const goal=card(row,'Goal / Подушка безопасности',720,32,20);const gh=box(goal,'Goal title',656,'HORIZONTAL',0,24);text(gh,'Подушка безопасности',476,'Heading');badge(gh,'В процессе',156);
const progressArea=rounded(box(goal,'Goal progress callout',656,'VERTICAL',24,12,'tint'),12);text(progressArea,'30%',608,'Display','green');text(progressArea,'цели уже отмечено как накопленное',608,'Body','green');progress(progressArea,608,0.3);
text(goal,'Накоплено',656,'Caption','muted');text(goal,'180 000,00 KZT',656,'Display');const target=box(goal,'Goal target',656,'HORIZONTAL',0,24);text(target,'Целевая сумма',300,'Body','muted');text(target,'600 000,00 KZT',332,'Heading').textAlignHorizontal='RIGHT';rule(goal,656);text(goal,'Накоплено до октября · пополнений в октябре нет',656,'Caption','muted');button(goal,'Изменить накопленное',250,'Neutral');
const aside=box(row,'Goal guidance',392,'VERTICAL',0,24);const tip=card(aside,'Separate goal accounting',392,24,16,'dark');text(tip,'Учёт накоплений',344,'Heading','white');text(tip,'Изменение учёта целей и долгов не меняет счёт и бюджет',344,'Body','white');text(tip,'Отмеченная сумма помогает видеть прогресс. Движения денег вы записываете в операциях отдельно.',344,'Body','white');const next=card(aside,'Next goal prompt',392,24,12);text(next,'Что дальше?',344,'Heading');note(next,'Вы сможете добавить цель с названием и нужной суммой. Дата достижения необязательна.',344);button(next,'Создать цель',344);
text(main,'Концепция · демонстрационные данные. Прогресс описывает учёт, а не обещание результата.',1136,'Caption','muted');
}
// SC-04: each direction retains its own amount and dates, without netting.
{
const root=page.children.find(n=>n.name==='SC-04 · Долги / desktop');roots.push(root);const main=shell(root,'Долги',3,'На 30.10.2026','Записать долг');
text(main,'Два направления. Ясные договорённости.',1136,'Heading');
const switches=box(main,'Debt direction tabs',1136,'HORIZONTAL',0,24);const owe=card(switches,'Direction tab / Я должен / active',556,24,8,'dark');text(owe,'Я должен',508,'Label','white');text(owe,'80 000,00 KZT',508,'Display','white');text(owe,'1 активная запись',508,'Caption','white');const owed=card(switches,'Direction tab / Мне должны',556,24,8);text(owed,'Мне должны',508,'Label');text(owed,'50 000,00 KZT',508,'Display');text(owed,'1 активная запись',508,'Caption','muted');
const row=box(main,'Debt details and other direction',1136,'HORIZONTAL',0,24);
const debt=card(row,'Debt details / Я должен',720,28,20);const h=box(debt,'Debt heading',664,'HORIZONTAL',0,24);text(h,'Я должен · Пример А',472,'Heading');badge(h,'Открыт',168,'amber');text(debt,'Остаток долга',664,'Caption','muted');text(debt,'80 000,00 KZT',664,'Display');rule(debt,664);const due=box(debt,'Debt due date',664,'HORIZONTAL',0,24);text(due,'Срок возврата',320,'Body','muted');text(due,'15.11.2026',320,'Label').textAlignHorizontal='RIGHT';text(debt,'История возвратов не предоставлена',664,'Body','muted');text(debt,'Остаток учтён до октября. Возвратов в октябре нет.',664,'Caption','muted');button(debt,'Записать возврат',220,'Neutral');
const aside=box(row,'Other direction and explanation',392,'VERTICAL',0,24);const other=card(aside,'Debt summary / Мне должны',392,24,12);text(other,'Мне должны · Пример Б',344,'Heading');text(other,'50 000,00 KZT',344,'Money');text(other,'Срок 20.11.2026',344,'Body','muted');text(other,'История возвратов не предоставлена',344,'Caption','muted');text(other,'Остаток учтён до октября. Возвратов в октябре нет.',344,'Caption','muted');const rulecard=card(aside,'Debt accounting explanation',392,24,12,'tint');text(rulecard,'Учитываем отдельно',344,'Label','green');note(rulecard,'Изменение учёта целей и долгов не меняет счёт и бюджет',344);note(rulecard,'«Я должен» и «Мне должны» не вычитаются друг из друга.',344);
text(main,'Демо · текущие остатки на 30.10.2026. Исходные суммы и прошлые возвраты не заданы.',1136,'Caption','muted');
}
return results(roots);
