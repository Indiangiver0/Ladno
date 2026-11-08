(function (root) {
  'use strict';
  // QA ONLY. Wraps the real volatile preview adapter; never packaged into the APK.
  var request = root.PreviewAdapter.request, candidateIds = ['10000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000003'];
  var pending = candidateIds.map(function (id) { return {id:id,revision:1,status:'needs_review',source:'receipt',sourceLabel:'Чек',receivedAt:'2026-11-03T12:00:00Z',kind:'expense',amount:'5.00',currency:'KZT',scale:2,date:'2026-11-03',dateOrigin:'source',note:'',reasons:['POSSIBLE_DUPLICATE'],confidence:'uncertain',transactionId:null,possibleMatchIds:[]}; });
  var baseline, posted = [], actions = [];
  var ready = request('settings', {primaryCurrency:'KZT',zoneId:'Asia/Qyzylorda',datePolicy:'POST_TIME_IN_ZONE'}).then(function () {
    return request('saveTransaction',{id:null,expectedRevision:null,kind:'expense',date:'2026-11-03',amount:'5.00',currency:'KZT',categoryId:null,note:'QA existing expense'});
  }).then(function (result) { baseline=result.record.id; pending.forEach(function (c) { c.possibleMatchIds=[baseline]; }); });
  function fail(code,message) {var e=new Error(message);e.code=code;e.field=null;throw e;}
  root.PreviewAdapter.request = function (action,payload) { return ready.then(function () {
    if(action==='reviewCandidate') {
      var c=pending.find(function(c){return c.id===payload.id;});
      if(!c||c.status!=='needs_review'||c.revision!==payload.expectedRevision) fail('CONFLICT','QA материал уже изменён.');
      actions.push({action:action,decision:payload.decision,id:payload.id});
      if(payload.decision==='ignore'){c.status='ignored';c.revision+=1;return {candidate:c,record:null};}
      if(payload.decision==='post') {
        if(payload.confirmDistinct!==true) fail('VALIDATION','Подтвердите самостоятельность.');
        return request('saveTransaction',Object.assign({id:null,expectedRevision:null},payload.transaction)).then(function(result){c.status='posted';c.revision+=1;c.transactionId=result.record.id;posted.push(result.record.id);return {candidate:c,record:result.record};});
      }
      if(payload.decision==='link'){c.status='linked';c.revision+=1;c.transactionId=payload.transactionId;return {candidate:c,record:null};}
      fail('VALIDATION','Неизвестное решение.');
    }
    if(action==='saveCategory'&&payload.name==='QA server failure') fail('STORAGE_FAILED','QA ошибка сохранения категории.');
    return request(action,payload).then(function(result){
      if(action!=='state') return result;
      result.imports=JSON.parse(JSON.stringify(pending.filter(function(c){return c.status==='needs_review';})));
      result.records.forEach(function(r){if(posted.indexOf(r.id)!==-1)r.source='receipt';});
      result.jobs=[
        {jobId:'qa-failed',type:'scanCamera',status:'failed',error:{code:'IMAGE_INVALID',message:'QA: текст не распознан.',field:null},outcome:null},
        {jobId:'qa-cancel',type:'scanGallery',status:'cancelled',error:null,outcome:null},
        {jobId:'qa-posted',type:'scanCamera',status:'completed',error:null,outcome:{status:'posted',candidateId:'20000000-0000-4000-8000-000000000001',transactionId:baseline,month:'2026-11',currency:'KZT'}},
        {jobId:'qa-duplicate',type:'scanGallery',status:'completed',error:null,outcome:{status:'duplicate',candidateId:'20000000-0000-4000-8000-000000000001',transactionId:baseline,month:'2026-11',currency:'KZT'}},
        {jobId:'qa-review',type:'scanCamera',status:'completed',error:null,outcome:{status:'needs_review',candidateId:candidateIds[0],transactionId:null,month:'2026-11',currency:'KZT'}}
      ];
      var label=root.document.getElementById('qa-fixture-label');
      if(label)label.textContent='СИНТЕТИЧЕСКИЙ QA: не данные пользователя / не Android OCR. Неучтённых чеков: '+result.imports.length+'. Действия review: '+JSON.stringify(actions)+'.';
      return result;
    });
  }); };
}(window));
