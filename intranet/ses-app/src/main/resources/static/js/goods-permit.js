/* ============ Helpers ============ */
const TODAY=new Date();TODAY.setHours(0,0,0,0);
const iso=dt=>{const z=new Date(dt.getTime()-dt.getTimezoneOffset()*60000);return z.toISOString().slice(0,10)};
const d=n=>{const x=new Date(TODAY);x.setDate(x.getDate()+n);return iso(x)};
const T0=d(0);
const MON=['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
const fd=s=>{if(!s)return'–';const[y,m,dd]=s.slice(0,10).split('-');return `${dd} ${MON[+m-1]} ${y}`};
const fdt=s=>s?`${fd(s)}, ${s.slice(11,16)}`:'–';
const daysTo=s=>Math.round((new Date(s+'T00:00:00')-TODAY)/86400000);
const nowStamp=()=>{const n=new Date();return iso(n)+'T'+n.toTimeString().slice(0,5)};
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const $=s=>document.querySelector(s);

const LUCIDE_MAP = {
  home: 'home',
  add: 'plus',
  doc: 'file-text',
  docadd: 'file-plus',
  inbox: 'inbox',
  person: 'user',
  truck: 'truck',
  flow: 'git-merge',
  checklist: 'clipboard-check',
  users: 'users',
  gate: 'shield-check',
  menu: 'menu',
  x: 'x',
  check: 'check',
  warn: 'alert-triangle',
  info: 'info',
  err: 'alert-circle',
  ok: 'check-circle-2',
  upload: 'upload',
  qr: 'qr-code',
  trash: 'trash-2',
  edit: 'edit-3',
  refresh: 'refresh-cw',
  chev: 'chevron-right',
  search: 'search',
  shield: 'shield',
  cal: 'calendar',
  reset: 'rotate-ccw'
};

const ic = (n, s = 18, cls = '') => {
  const iconName = LUCIDE_MAP[n] || n;
  return `<i data-lucide="${iconName}" class="lucide-icon ${cls}" style="width:${s}px;height:${s}px;display:inline-flex;align-items:center;justify-content:center;vertical-align:middle;"></i>`;
};

/* ============ Seed data (mirrors SAP MM documents) ============ */
function seed(){
 const db={
  seq:127,
  vendors:{V1000:{code:'V1000',name:'PT Sinar Logistik Abadi'},V2000:{code:'V2000',name:'PT Abadi Niaga Sentosa'}},
  positions:[
   {code:'PRC-MGR-01',name:'Procurement Manager',holder:'Yulia Rahmawati',dept:'Procurement'},
   {code:'PRC-SPV-02',name:'Procurement Supervisor',holder:'Lina Marlina',dept:'Procurement'},
   {code:'WHS-SPV-01',name:'Warehouse Supervisor',holder:'Fajar Nugroho',dept:'Warehouse'},
   {code:'WHS-MGR-01',name:'Warehouse Manager',holder:'Teguh Santoso',dept:'Warehouse'},
   {code:'SEC-MGR-01',name:'Security Manager',holder:'Bambang Sutrisno',dept:'Security'},
  ],
  flow:[
   {id:1,seq:1,pos:'PRC-MGR-01',active:true},
   {id:2,seq:1,pos:'WHS-SPV-01',active:true},
   {id:3,seq:2,pos:'SEC-MGR-01',active:true},
  ],
  pos:{
   '4500012345':{vendor:'V1000',desc:'Spare parts – Q4 plant maintenance',plant:'WH01 · Main Warehouse',created:d(-21),
     items:[{line:10,mat:'100234',desc:'Ball Bearing SKF 6205-2RS',qty:200,uom:'PC'},{line:20,mat:'200511',desc:'Hydraulic Oil Shell Tellus S2 M46 209L',qty:12,uom:'DR'},{line:30,mat:'100877',desc:'V-Belt Bando B-52',qty:50,uom:'PC'}],
     dos:{'8000056001':{date:d(0),items:[{line:10,qty:120},{line:20,qty:6}]},'8000056002':{date:d(2),items:[{line:10,qty:80},{line:20,qty:6},{line:30,qty:50}]}}},
   '4500012388':{vendor:'V1000',desc:'Personal protective equipment',plant:'WH02 · Procurement Receiving',created:d(-14),
     items:[{line:10,mat:'300120',desc:'Safety Helmet MSA V-Gard, white',qty:150,uom:'PC'},{line:20,mat:'300245',desc:'Safety Shoes Cheetah 7012H, size 42',qty:60,uom:'PR'}],
     dos:{'8000056110':{date:d(1),items:[{line:10,qty:150},{line:20,qty:60}]}}},
   '4500013002':{vendor:'V1000',desc:'Office consumables',plant:'WH02 · Procurement Receiving',created:d(-6),
     items:[{line:10,mat:'400010',desc:'Copy Paper A4 80gsm',qty:100,uom:'RM'},{line:20,mat:'400032',desc:'Toner HP 26A CF226A',qty:12,uom:'PC'}],dos:{}},
   '4500012990':{vendor:'V1000',desc:'Electrical spare parts',plant:'WH01 · Main Warehouse',created:d(-9),
     items:[{line:10,mat:'500101',desc:'MCB Schneider 3P 32A',qty:40,uom:'PC'},{line:20,mat:'500230',desc:'Cable NYY 4x16mm²',qty:500,uom:'M'}],
     dos:{'8000056150':{date:d(3),items:[{line:10,qty:40},{line:20,qty:500}]}}},
   '4500019001':{vendor:'V2000',desc:'Cleaning chemicals',plant:'WH01 · Main Warehouse',created:d(-5),items:[{line:10,mat:'600010',desc:'Floor Cleaner 20L',qty:30,uom:'PC'}],dos:{'8000057001':{date:d(1),items:[{line:10,qty:30}]}}},
  },
  drivers:[
   {id:'DRV-0001',vendor:'V1000',name:'Budi Santoso',nik:'3175031204870001',phone:'0812-8833-1021',simNo:'8710-1204-000123',simType:'B1 Umum',simValid:d(410),files:{ktp:'KTP_Budi_Santoso.pdf',sim:'SIM_B1_Budi_Santoso.pdf',photo:'Photo_Budi.jpg'},history:[]},
   {id:'DRV-0002',vendor:'V1000',name:'Agus Prasetyo',nik:'3201150809900004',phone:'0813-1144-7720',simNo:'9008-0809-004512',simType:'B2 Umum',simValid:d(20),files:{ktp:'KTP_Agus_Prasetyo.pdf',sim:'SIM_B2_Agus.pdf',photo:'Photo_Agus.jpg'},history:[]},
   {id:'DRV-0003',vendor:'V1000',name:'Hendra Wijaya',nik:'3603122302880007',phone:'0857-2210-9981',simNo:'8802-2302-009876',simType:'B1 Umum',simValid:d(-15),files:{ktp:'KTP_Hendra.pdf',sim:'SIM_B1_Hendra.pdf',photo:''},history:[]},
   {id:'DRV-0004',vendor:'V1000',name:'Rudi Hartono',nik:'3174070511850003',phone:'0811-9087-3345',simNo:'8505-0511-001877',simType:'B1 Umum',simValid:d(700),files:{ktp:'KTP_Rudi_Hartono.pdf',sim:'SIM_B1_Rudi.pdf',photo:'Photo_Rudi.jpg'},history:[]},
  ],
  vehicles:[
   {id:'VEH-0001',vendor:'V1000',plate:'B 9123 KXT',type:'CDD (Colt Diesel Double)',brand:'Mitsubishi Colt Diesel FE 74',year:2021,stnkNo:'09812734',taxValid:d(150),stnkValid:d(900),kirNo:'JKT 12034 KIR',kirValid:d(90),files:{stnk:'STNK_B9123KXT.pdf',tax:'Tax_2026_B9123KXT.pdf',kir:'KIR_B9123KXT.pdf',photo:'B9123KXT.jpg'},history:[]},
   {id:'VEH-0002',vendor:'V1000',plate:'B 9876 FGH',type:'CDE (Colt Diesel Engkel)',brand:'Isuzu Elf NMR 71',year:2020,stnkNo:'08711245',taxValid:d(25),stnkValid:d(400),kirNo:'JKT 11873 KIR',kirValid:d(60),files:{stnk:'STNK_B9876FGH.pdf',tax:'Tax_2025_B9876FGH.pdf',kir:'KIR_B9876FGH.pdf',photo:''},history:[]},
   {id:'VEH-0003',vendor:'V1000',plate:'B 9555 UYT',type:'Wingbox',brand:'Hino 500 FG 235',year:2019,stnkNo:'07612399',taxValid:d(200),stnkValid:d(300),kirNo:'BKS 07721 KIR',kirValid:d(-5),files:{stnk:'STNK_B9555UYT.pdf',tax:'Tax_B9555UYT.pdf',kir:'KIR_B9555UYT.pdf',photo:''},history:[]},
   {id:'VEH-0004',vendor:'V1000',plate:'B 9031 TRQ',type:'CDE (Colt Diesel Engkel)',brand:'Toyota Dyna 130 HT',year:2022,stnkNo:'10233871',taxValid:d(260),stnkValid:d(1200),kirNo:'JKT 13310 KIR',kirValid:d(140),files:{stnk:'STNK_B9031TRQ.pdf',tax:'Tax_B9031TRQ.pdf',kir:'KIR_B9031TRQ.pdf',photo:'B9031TRQ.jpg'},history:[]},
  ],
  checklist:[
   {id:1,name:'Tires & tread depth',cat:'Vehicle',mandatory:true,photo:true,active:true},
   {id:2,name:'Brakes & hand brake',cat:'Vehicle',mandatory:true,photo:false,active:true},
   {id:3,name:'Head, brake & turn signal lights',cat:'Vehicle',mandatory:true,photo:false,active:true},
   {id:4,name:'Fire extinguisher (APAR)',cat:'Safety',mandatory:true,photo:true,active:true},
   {id:5,name:'First aid kit (P3K)',cat:'Safety',mandatory:false,photo:false,active:true},
   {id:6,name:'Wheel chocks',cat:'Safety',mandatory:true,photo:true,active:true},
   {id:7,name:'Cargo secured & covered',cat:'Cargo',mandatory:true,photo:true,active:true},
   {id:8,name:'Oil / fuel leakage',cat:'Vehicle',mandatory:false,photo:true,active:true},
   {id:9,name:'Driver PPE (helmet, vest, safety shoes)',cat:'Driver',mandatory:true,photo:true,active:true},
   {id:10,name:'Driver fit to work (not fatigued)',cat:'Driver',mandatory:true,photo:false,active:true},
   {id:11,name:'Reverse alarm',cat:'Vehicle',mandatory:false,photo:false,active:false},
  ],
  secUsers:[
   {id:'SEC-01',name:'Dedi Kurniawan',emp:'EMP-20211',user:'dedi.k',gate:'Gate 1 · Main Gate',shift:'Morning',active:true,last:d(0)+'T06:01'},
   {id:'SEC-02',name:'Slamet Riyadi',emp:'EMP-20234',user:'slamet.r',gate:'Gate 2 · Warehouse Gate',shift:'Morning',active:true,last:d(0)+'T06:05'},
   {id:'SEC-03',name:'Wahyu Hidayat',emp:'EMP-20188',user:'wahyu.h',gate:'Gate 1 · Main Gate',shift:'Night',active:true,last:d(-1)+'T22:02'},
   {id:'SEC-04',name:'Rina Oktaviani',emp:'EMP-20301',user:'rina.o',gate:'Gate 2 · Warehouse Gate',shift:'Afternoon',active:false,last:d(-30)+'T14:00'},
  ],
  requests:[
   mkReq('IMB-2026-000123','Approved',[{po:'4500012345',dos:['8000056001','8000056002']}],d(-1),d(2),['DRV-0001','DRV-0002'],['VEH-0001','VEH-0002'],d(-4),[['A',d(-3)+'T09:12'],['A',d(-3)+'T10:40'],['A',d(-2)+'T08:05']]),
   mkReq('IMB-2026-000121','Approved',[{po:'4500012388',dos:['8000056110']}],d(0),d(1),['DRV-0004'],['VEH-0004'],d(-5),[['A',d(-4)+'T11:00'],['A',d(-4)+'T13:20'],['A',d(-3)+'T09:00']]),
   mkReq('IMB-2026-000124','In Approval',[{po:'4500012990',dos:['8000056150']}],d(3),d(3),['DRV-0001'],['VEH-0001'],d(-1),[['A',d(0)+'T08:30'],['P'],['W']]),
   mkReq('IMB-2026-000125','Approved',[{po:'4500013002',dos:[]}],d(4),d(6),['DRV-0004'],['VEH-0004'],d(-2),[['A',d(-1)+'T10:00'],['A',d(-1)+'T10:30'],['A',d(0)+'T07:50']]),
   mkReq('IMB-2026-000118','Approved',[{po:'4500012345',dos:['8000056001']}],d(-12),d(-10),['DRV-0002'],['VEH-0002'],d(-16),[['A',d(-15)+'T09:00'],['A',d(-15)+'T09:30'],['A',d(-14)+'T08:00']]),
   mkReq('IMB-2026-000120','Rejected',[{po:'4500012388',dos:['8000056110']}],d(-3),d(-3),['DRV-0003'],['VEH-0003'],d(-8),[['R',d(-7)+'T14:10','DO 8000056110 quantity under revision by Purchasing. Please resubmit after the DO is re-issued.'],['P'],['W']]),
   mkReq('IMB-2026-000126','Draft',[{po:'4500012990',dos:['8000056150']}],d(3),d(3),[],[],d(0),null),
  ],
  gate:[
   {id:1,time:d(-1)+'T07:32',req:'IMB-2026-000123',drv:'DRV-0001',veh:'VEH-0001',type:'Check-in',gate:'Gate 1 · Main Gate',officer:'Wahyu Hidayat',result:'Fit',insp:'10/10 Present & Fit'},
   {id:2,time:d(-1)+'T10:05',req:'IMB-2026-000123',drv:'DRV-0001',veh:'VEH-0001',type:'Check-out',gate:'Gate 1 · Main Gate',officer:'Wahyu Hidayat'},
   {id:3,time:d(0)+'T06:55',req:'IMB-2026-000123',drv:'DRV-0002',veh:'VEH-0002',type:'Check-in rejected',gate:'Gate 1 · Main Gate',officer:'Dedi Kurniawan',result:'Not Fit',insp:'8/10 · Tires: Present, Not Fit · Wheel chocks: Not Present',remarks:'Rear tires worn below limit and no wheel chocks. Replace tires before re-entry.'},
   {id:4,time:d(0)+'T07:40',req:'IMB-2026-000123',drv:'DRV-0001',veh:'VEH-0001',type:'Check-in',gate:'Gate 1 · Main Gate',officer:'Dedi Kurniawan',result:'Fit',insp:'10/10 Present & Fit'},
   {id:5,time:d(0)+'T08:05',req:'IMB-2026-000121',drv:'DRV-0004',veh:'VEH-0004',type:'Check-in',gate:'Gate 2 · Warehouse Gate',officer:'Slamet Riyadi',result:'Fit',insp:'9/10 · First aid kit: Present, Not Fit'},
   {id:6,time:d(0)+'T09:15',req:'IMB-2026-000123',drv:'DRV-0001',veh:'VEH-0001',type:'Check-out',gate:'Gate 1 · Main Gate',officer:'Dedi Kurniawan'},
  ],
 };
 return enrich(db);
}
function mkReq(id,status,docs,start,end,drivers,vehicles,created,appr){
 const R={id,vendor:'V1000',status,docs,start,end,drivers,vehicles,created,remarks:'',items:[],approvals:null,history:[{at:created+'T09:00',who:'Andi Wijaya (Vendor)',act:'Created'}]};
 R._seedItems=true;
 if(appr){
  const steps=[{seq:1,pos:'PRC-MGR-01'},{seq:1,pos:'WHS-SPV-01'},{seq:2,pos:'SEC-MGR-01'}];
  R.approvals=steps.map((s,i)=>{const a=appr[i]||['W'];return{...s,status:{A:'Approved',R:'Rejected',P:'Pending',W:'Waiting'}[a[0]],at:a[1]||null,comment:a[2]||''}});
  R.history.push({at:created+'T09:20',who:'Andi Wijaya (Vendor)',act:'Submitted'});
  R.approvals.forEach(a=>{if(a.at)R.history.push({at:a.at,who:posLabel0(a.pos),act:a.status,comment:a.comment})});
 }
 return R;
}
function enrich(db){
 const many={};['8000056201','8000056202','8000056203','8000056204','8000056205','8000056206','8000056207','8000056208','8000056209','8000056210'].forEach((n,i)=>many[n]={date:d(1+i),items:[{line:10,qty:20},{line:20,qty:10}]});
 db.pos['4500012400']={vendor:'V1000',desc:'Packaging material – monthly call-off',plant:'WH01 · Main Warehouse',created:d(-30),items:[{line:10,mat:'700110',desc:'Carton Box 60x40x40 cm',qty:200,uom:'PC'},{line:20,mat:'700215',desc:'Stretch Film 50cm x 300m',qty:100,uom:'RL'}],dos:many};
 db.pos['4500012455']={vendor:'V1000',desc:'Lubricants – plant maintenance',plant:'WH01 · Main Warehouse',created:d(-3),items:[{line:10,mat:'200540',desc:'Grease Shell Gadus S2 V220 18kg',qty:20,uom:'PL'}],dos:{'8000056300':{date:d(2),items:[{line:10,qty:20}]}}};
 const vd={verif:'Verified',verifBy:'Maya Sari',verifAt:d(-40)+'T10:00',change:null,changeRej:null};
 db.drivers.forEach(o=>Object.assign(o,vd));db.vehicles.forEach(o=>Object.assign(o,vd));
 const mk=(id,name,nik,phone,simNo,simType,days,extra)=>({id,vendor:'V1000',name,nik,phone,simNo,simType,simValid:d(days),files:{ktp:`KTP_${name.split(' ')[0]}.pdf`,sim:`SIM_${name.split(' ')[0]}.pdf`,photo:''},history:[],...vd,...extra});
 db.drivers.push(mk('DRV-0005','Wawan Setiawan','3172021003890002','0812-7766-1200','8903-1003-002211','B1 Umum',520),
  mk('DRV-0006','Eko Saputra','3275011507920005','0821-3344-5566','9207-1507-007781','B2 Umum',300),
  mk('DRV-0007','Yusuf Maulana','3201032208910006','0857-9988-1122','9108-2208-003390','B1 Umum',640),
  mk('DRV-0008','Joko Susilo','3174052706880008','0813-5544-7788','8806-2706-005561','B1 Umum',800,{verif:'Pending',submitted:d(0)+'T08:20',verifBy:null}),
  mk('DRV-0009','Dimas Pratama','3603011112950001','0896-1122-3344','9512-1112-001234','B1',380,{verif:'Rejected',submitted:d(-2)+'T09:00',verifNote:'SIM type B1 (non-commercial) is not allowed for trucks. Upload a B1 Umum licence.'}));
 const mv=(id,plate,type,brand,year,t,s5,k,extra)=>({id,vendor:'V1000',plate,type,brand,year,stnkNo:String(10000000+year*7),taxValid:d(t),stnkValid:d(s5),kirNo:'JKT '+(14000+year)+' KIR',kirValid:d(k),files:{stnk:`STNK_${plate.replace(/ /g,'')}.pdf`,tax:`Tax_${plate.replace(/ /g,'')}.pdf`,kir:`KIR_${plate.replace(/ /g,'')}.pdf`,photo:''},history:[],...vd,...extra});
 db.vehicles.push(mv('VEH-0005','B 9712 PLK','Fuso','Mitsubishi Fuso Fighter FM 65',2021,210,700,120),
  mv('VEH-0006','B 9208 JTR','CDD (Colt Diesel Double)','Hino Dutro 130 MDL',2023,330,1300,170),
  mv('VEH-0007','B 9440 NMS','Wingbox','Isuzu Giga FVM 34',2022,280,1100,150,{verif:'Pending',submitted:d(0)+'T07:55',verifBy:null}));
 const v2=db.vehicles.find(v=>v.id==='VEH-0002');v2.change={at:d(0)+'T08:12',data:{taxValid:d(390)},files:{tax:'Tax_2026_B9876FGH_renewed.pdf'}};
 return db;
}
function posLabel0(c){return{'PRC-MGR-01':'Yulia Rahmawati · Procurement Manager','WHS-SPV-01':'Fajar Nugroho · Warehouse Supervisor','SEC-MGR-01':'Bambang Sutrisno · Security Manager'}[c]||c}

/* ============ State ============ */
const STORE='gep-web-v4';
let DB;
try{const raw=localStorage.getItem(STORE);DB=raw?JSON.parse(raw):null;if(DB&&DB._day!==T0)DB=null}catch(e){DB=null}
if(!DB){DB=seed();DB._day=T0;DB.requests.forEach(r=>{if(r._seedItems){r.items=buildItems(r.docs);delete r._seedItems}})}
function save(){try{localStorage.setItem(STORE,JSON.stringify(DB))}catch(e){}}

const ROLES=[
 {k:'vendor',label:'Vendor · PT Sinar Logistik Abadi',user:'Andi Wijaya',ini:'AW'},
 {k:'appr:PRC-MGR-01',label:'Approver · Procurement Manager',user:'Yulia Rahmawati',ini:'YR'},
 {k:'appr:WHS-SPV-01',label:'Approver · Warehouse Supervisor',user:'Fajar Nugroho',ini:'FN'},
 {k:'appr:SEC-MGR-01',label:'Approver · Security Manager',user:'Bambang Sutrisno',ini:'BS'},
 {k:'admin',label:'Admin · Security Administrator',user:'Maya Sari',ini:'MS'},
];
const cfg = window.SES_CONFIG || {};
const S = {
  mod: 'gep',
  role: cfg.activeRole || 'vendor',
  view: cfg.initialView || 'home',
  detail: null,
  tab: 'overview',
  form: null,
  filter: { q: '', status: '', plant: '' },
  drvFilter: { q: '', verif: '' },
  vehFilter: { q: '', verif: '' },
  gateFilter: { date: T0, gate: '', type: '' },
  navOpen: false,
  errs: null
};
const role = () => {
  const c = window.SES_CONFIG || {};
  const r = (ROLES.find(x => x.k === S.role) || {
    k: S.role,
    label: c.roleLabel || 'User',
    user: c.displayName || c.username || 'User',
    ini: (c.displayName || c.username || 'U').slice(0, 2).toUpperCase()
  });
  if (c.displayName) r.user = c.displayName;
  return r;
};
const isVendor=()=>S.role==='vendor', isAdmin=()=>S.role==='admin', myPos=()=>S.role.startsWith('appr:')?S.role.slice(5):null;
const pos=c=>DB.positions.find(p=>p.code===c)||{code:c,name:c,holder:'–'};
const drv=id=>DB.drivers.find(x=>x.id===id);
const veh=id=>DB.vehicles.find(x=>x.id===id);
const reqById=id=>DB.requests.find(r=>r.id===id);

/* ============ Domain logic ============ */
function buildItems(docs){
 const out=[];
 docs.forEach(dc=>{const P=DB.pos[dc.po];if(!P)return;
  if(dc.dos.length){dc.dos.forEach(dn=>{const D=P.dos[dn];D.items.forEach(it=>{const pl=P.items.find(x=>x.line===it.line);out.push({key:dc.po+'/'+dn+'/'+it.line,po:dc.po,do:dn,line:it.line,mat:pl.mat,desc:pl.desc,qty:it.qty,docQty:it.qty,uom:pl.uom,nonpo:false})})})}
  else P.items.forEach(pl=>out.push({key:dc.po+'//'+pl.line,po:dc.po,do:'',line:pl.line,mat:pl.mat,desc:pl.desc,qty:pl.qty,docQty:pl.qty,uom:pl.uom,nonpo:false}));
 });
 return out;
}
const DRV_DOCS=[{k:'simValid',label:'SIM (driving licence)'}];
const VEH_DOCS=[{k:'taxValid',label:'STNK annual tax'},{k:'stnkValid',label:'STNK 5-year'},{k:'kirValid',label:'KIR'}];
function docIssues(o,kind,by){return (kind==='drv'?DRV_DOCS:VEH_DOCS).filter(x=>o[x.k]<by).map(x=>({...x,date:o[x.k]}))}
function healthTag(o,kind){
 const docs=kind==='drv'?DRV_DOCS:VEH_DOCS;
 const exp=docs.filter(x=>daysTo(o[x.k])<0), soon=docs.filter(x=>{const n=daysTo(o[x.k]);return n>=0&&n<=30});
 if(exp.length)return `<span class="tag err">Expired · ${exp.map(x=>x.label).join(', ')}</span>`;
 if(soon.length)return `<span class="tag warn">Expires in ${Math.min(...soon.map(x=>daysTo(o[x.k])))} days</span>`;
 return '<span class="tag ok">Valid</span>';
}
function dateCell(s){const n=daysTo(s);const cls=n<0?'err':n<=30?'warn':'';return `<span class="num ${cls?'':''}" style="${cls?`color:var(--${cls});font-weight:600`:''}">${fd(s)}</span>`}

function currentSeq(R){if(!R.approvals)return null;const p=R.approvals.filter(a=>a.status!=='Approved'&&a.status!=='Skipped');return p.length?Math.min(...p.map(a=>a.seq)):null}
function validity(R){
 if(R.status!=='Approved')return null;
 if(T0<R.start)return{t:'Scheduled',c:'info'};
 if(T0>R.end)return{t:'Expired',c:''};
 return{t:'Active',c:'ok'};
}
function statusTag(R){
 const m={'Draft':'','In Approval':'info','Approved':'ok','Rejected':'err','Returned':'warn'};
 let h=`<span class="tag ${m[R.status]}">${R.status}</span>`;
 const v=validity(R);if(v)h+=` <span class="tag plain ${v.c}">QR ${v.t}</span>`;
 return h;
}
function pendingFor(p){return DB.requests.filter(R=>R.status==='In Approval'&&R.approvals.some(a=>a.pos===p&&a.status==='Pending'))}
function itemsSummary(R){return `${R.items.length} item${R.items.length===1?'':'s'}`}
function reqPOs(R){return R.docs.map(x=>x.po).join(', ')}
function reqDOs(R){const a=R.docs.flatMap(x=>x.dos);return a.length?a.join(', '):'–'}

function submitReq(R){
 const steps=DB.flow.filter(f=>f.active).sort((a,b)=>a.seq-b.seq);
 const minSeq=Math.min(...steps.map(s=>s.seq));
 R.approvals=steps.map(s=>({seq:s.seq,pos:s.pos,status:s.seq===minSeq?'Pending':'Waiting',at:null,comment:''}));
 R.status='In Approval';
 R.history.push({at:nowStamp(),who:'Andi Wijaya (Vendor)',act:R._resub?'Resubmitted':'Submitted'});
 delete R._resub;
}
function decide(R,p,act,comment){
 const step=R.approvals.find(a=>a.pos===p&&a.status==='Pending');if(!step)return;
 step.at=nowStamp();step.comment=comment||'';
 const who=pos(p).holder+' · '+pos(p).name;
 if(act==='approve'){
  step.status='Approved';
  R.history.push({at:step.at,who,act:'Approved',comment});
  const cur=currentSeq(R);
  if(cur===null){R.status='Approved';R.history.push({at:step.at,who:'System',act:'QR code issued'})}
  else R.approvals.forEach(a=>{if(a.seq===cur&&a.status==='Waiting')a.status='Pending'});
 }else{
  step.status=act==='reject'?'Rejected':'Returned';
  R.approvals.forEach(a=>{if(a.status==='Pending'&&a!==step)a.status='Cancelled'});
  R.status=act==='reject'?'Rejected':'Returned';
  R.history.push({at:step.at,who,act:act==='reject'?'Rejected':'Returned for revision',comment});
 }
}

/* ============ Shell ============ */
function navItems(){
 if(S.mod==='badge')return bNav();
 if(isVendor())return[['home','Home','home'],['create','Create Request','docadd'],['requests','My Requests','doc'],'Master Data',['drivers','Drivers','person'],['vehicles','Vehicles','truck']];
 if(isAdmin())return[['home','Home','home'],['requests','All Requests','doc'],['verify','Master Verification','shield',verifQueue('drv').length+verifQueue('veh').length],'Configuration',['flow','Approval Flow','flow'],['checklist','Inspection Checklist','checklist'],['secusers','Security Users','users'],'Monitoring',['gatelog','Gate Log','gate']];
 return[['home','Home','home'],['inbox','My Inbox','inbox',pendingFor(myPos()).length],['requests','All Requests','doc']];
}
function render() {
  const R = role();
  const navHtml = navItems().map(n => {
    if (typeof n === 'string') return `<div class="gep-pill-separator"></div>`;
    const [v, label, icon, badge] = n;
    const active = S.view === v || (S.view === 'detail' && (S.from === v || (!S.from && v === 'requests')));
    return `<button type="button" class="gep-pill-tab ${active ? 'active' : ''}" onclick="go('${v}')">${ic(icon, 16)}<span>${label}</span>${badge ? `<span class="gep-pill-badge">${badge}</span>` : ''}</button>`;
  }).join('');

  const host = document.getElementById('gep-app');
  if (!host) return;
  host.innerHTML = `
    <div class="gep-top-container">
      <div class="gep-pill-navbar">${navHtml}</div>
      <div id="main" class="gep-main-content">${view()}</div>
    </div>
  `;
  afterRender();
}
const ADMIN_SEC_VIEWS = ['flow', 'checklist', 'secusers', 'verify', 'gatelog'];
function go(v,extra){if(isVendor()&&ADMIN_SEC_VIEWS.indexOf(v)!==-1){toast('Akses Ditolak: Halaman ini hanya untuk Administrator & Security');v='home'}S.view=v;S.navOpen=false;S.errs=null;if(v==='create'&&!(extra&&extra.keep)){S.form=newForm();S.ctab='docs'}
 if(v==='b-create'&&!(extra&&extra.keep)){S.bf=bNewForm();S.bctab='gen';S.berrs=null}Object.assign(S,extra||{});render();window.scrollTo(0,0)}
function switchRole(k){S.role=k;go('home')}
function switchMod(m){S.mod=m;S.role=(m==='badge'?BROLES:ROLES)[0].k;go('home')}
function resetDemo(){S.dialog={type:'confirm',title:'Reset demo data',text:'All changes you made in this prototype will be discarded and the sample data restored.',ok:'Reset',fn:()=>{try{localStorage.removeItem(STORE)}catch(e){};DB=seed();DB._day=T0;DB.requests.forEach(r=>{if(r._seedItems){r.items=buildItems(r.docs);delete r._seedItems}});DB.badge=seedBadge();save();go('home');toast('Demo data restored')}};dlg()}
let tt;function toast(m){const t=$('#toast');t.textContent=m;t.classList.add('on');clearTimeout(tt);tt=setTimeout(()=>t.classList.remove('on'),2600)}
function openReq(id,from){S.detail=id;S.tab='overview';go('detail',{from:from||S.view})}

function view(){
 if(isVendor()&&ADMIN_SEC_VIEWS.indexOf(S.view)!==-1){return '<div class="p-8 text-center text-slate-500 font-semibold">Akses Ditolak: Halaman ini hanya untuk Administrator & Security</div>';}
 switch(S.view){
  case 'home':return S.mod==='badge'?bHome():vHome();
  case 'create':return vCreate();
  case 'requests':return vRequests();
  case 'inbox':return vInbox();
  case 'detail':return vDetail();
  case 'drivers':return vDrivers();
  case 'vehicles':return vVehicles();
  case 'flow':return vFlow();
  case 'checklist':return vChecklist();
  case 'secusers':return vSecUsers();
  case 'gatelog':return vGate();
  case 'verify':return vVerify();
 }
 if(S.view.startsWith('b-'))return bView();
 return '';
}
function ph(title,sub,crumbs,actions,extra=''){
 return `<div class="ph">
  ${crumbs?`<div class="crumbs">${crumbs}</div>`:''}
  <div class="ph-row">
    <div style="min-width:0">
      <h1>${title}</h1>
      ${sub?`<div class="sub">${sub}</div>`:''}
    </div>
    ${actions?`<div class="ph-actions">${actions}</div>`:''}
  </div>
  ${extra}
 </div>`;
}

/* ============ Home ============ */
function tile(t,s,n,icn,on,warn){
 return `<button type="button" class="tile" onclick="${on}">
  <div class="tt">${t}</div>
  <div class="ts">${s}</div>
  <div class="tf">
    <span class="tn num ${warn?'warn':''}">${n??''}</span>
    ${ic(icn,22)}
  </div>
 </button>`;
}
function vHome(){
 const r=role();
 const hello=ph(`Good ${new Date().getHours()<12?'morning':new Date().getHours()<17?'afternoon':'evening'}, ${r.user.split(' ')[0]}`,`${r.label} · ${fd(T0)}`);
 if(isVendor()){
  const mine=DB.requests.filter(x=>x.vendor==='V1000');
  const c=s=>mine.filter(x=>x.status===s).length;
  const active=mine.filter(x=>validity(x)?.t==='Active');
  const expDocs=[...DB.drivers.filter(x=>x.vendor==='V1000').flatMap(o=>DRV_DOCS.filter(k=>daysTo(o[k.k])<=30).map(k=>({o,k,kind:'Driver',name:o.name}))),
   ...DB.vehicles.filter(x=>x.vendor==='V1000').flatMap(o=>VEH_DOCS.filter(k=>daysTo(o[k.k])<=30).map(k=>({o,k,kind:'Vehicle',name:o.plate})))].sort((a,b)=>a.o[a.k.k]<b.o[b.k.k]?-1:1);
  return hello+`<div class="pbody">
  <div class="grp-title">Goods Entry Permit</div>
  <div class="tiles">
   ${tile('Create Request','New entry permit','', 'docadd',"go('create')")}
   ${tile('My Requests','Draft',c('Draft'),'doc',"S.filter.status='Draft';go('requests')")}
   ${tile('My Requests','In approval',c('In Approval'),'inbox',"S.filter.status='In Approval';go('requests')")}
   ${tile('Active Permits','QR valid today',active.length,'qr',"S.filter.status='Approved';go('requests')")}
   ${tile('Returned / Rejected','Needs your action',c('Rejected')+c('Returned'),'warn',"S.filter.status='Rejected';go('requests')",true)}
  </div>
  <div class="grp-title">Master Data</div>
  <div class="tiles">
   ${tile('Drivers','Registered drivers',DB.drivers.filter(x=>x.vendor==='V1000').length,'person',"go('drivers')")}
   ${tile('Vehicles','Registered vehicles',DB.vehicles.filter(x=>x.vendor==='V1000').length,'truck',"go('vehicles')")}
  </div>
  <div class="grid g2">
   <div class="card"><div class="card-h"><h3>Documents expiring within 30 days</h3><span class="tag warn plain">${expDocs.length}</span></div>
    ${expDocs.length?`<div class="tw"><table class="t"><thead><tr><th>Master</th><th>Document</th><th>Valid until</th><th></th></tr></thead><tbody>
    ${expDocs.map(e=>`<tr><td><b>${esc(e.name)}</b><div class="small muted">${e.kind}</div></td><td>${e.k.label}</td><td>${dateCell(e.o[e.k.k])}<div class="small muted">${daysTo(e.o[e.k.k])<0?'Expired':daysTo(e.o[e.k.k])+' days left'}</div></td><td class="r">${e.o.change?'<span class="tag warn plain">Renewal pending</span>':`<button class="btn sm" onclick="renewDlg('${e.kind==='Driver'?'drv':'veh'}','${e.o.id}','${e.k.k}')">Renew</button>`}</td></tr>`).join('')}</tbody></table></div>`:'<div class="empty">All documents are valid for the next 30 days.</div>'}
   </div>
   <div class="card"><div class="card-h"><h3>Recent requests</h3><div class="acts"><button class="btn ghost sm" onclick="go('requests')">Show all</button></div></div>
    <div class="tw"><table class="t"><tbody>${mine.slice().sort((a,b)=>b.created.localeCompare(a.created)).slice(0,5).map(x=>`<tr class="click" onclick="openReq('${x.id}','requests')"><td><b class="mono">${x.id}</b><div class="small muted">${fd(x.start)} – ${fd(x.end)}</div></td><td>${statusTag(x)}</td><td class="nav">${ic('chev',16)}</td></tr>`).join('')}</tbody></table></div>
   </div>
  </div></div>`;
 }
 if(isAdmin()){
  const todayLog=DB.gate.filter(g=>g.time.startsWith(T0));
  const inside=insideNow();
  return hello+`<div class="pbody">
  <div class="grp-title">Master Data</div>
  <div class="tiles">
   ${tile('Verify Drivers','Waiting for verification',verifQueue('drv').length,'person',"S.vtab='drv';go('verify')",verifQueue('drv').length>0)}
   ${tile('Verify Vehicles','Waiting for verification',verifQueue('veh').length,'truck',"S.vtab='veh';go('verify')",verifQueue('veh').length>0)}
  </div>
  <div class="grp-title">Configuration</div>
  <div class="tiles">
   ${tile('Approval Flow','Active steps',DB.flow.filter(f=>f.active).length,'flow',"go('flow')")}
   ${tile('Inspection Checklist','Active parameters',DB.checklist.filter(c=>c.active).length,'checklist',"go('checklist')")}
   ${tile('Security Users','Mobile app users',DB.secUsers.filter(u=>u.active).length,'users',"go('secusers')")}
  </div>
  <div class="grp-title">Monitoring</div>
  <div class="tiles">
   ${tile('Gate Log','Events today',todayLog.length,'gate',"go('gatelog')")}
   ${tile('On Site Now','Vehicles inside',inside.length,'truck',"go('gatelog')")}
   ${tile('Rejected Check-ins','Today',todayLog.filter(g=>g.type==='Check-in rejected').length,'warn',"S.gateFilter.type='Check-in rejected';go('gatelog')",true)}
   ${tile('All Requests','In approval',DB.requests.filter(r=>r.status==='In Approval').length,'doc',"S.filter.status='In Approval';go('requests')")}
  </div></div>`;
 }
 const p=myPos(), pend=pendingFor(p);
 return hello+`<div class="pbody">
  <div class="tiles">
   ${tile('My Inbox','Waiting for your approval',pend.length,'inbox',"go('inbox')",pend.length>0)}
   ${tile('Approved by me','Last 30 days',DB.requests.filter(R=>R.approvals?.some(a=>a.pos===p&&a.status==='Approved')).length,'check',"go('requests')")}
   ${tile('All Requests','Goods entry permits',DB.requests.filter(r=>r.status!=='Draft').length,'doc',"go('requests')")}
  </div>
  <div class="card"><div class="card-h"><h3>Waiting for you</h3><span class="muted small">${pos(p).code} · ${pos(p).name}</span></div>${inboxTable(pend)}</div>
 </div>`;
}
function insideNow(){
 const st={};DB.gate.filter(g=>g.time.startsWith(T0)).sort((a,b)=>a.time.localeCompare(b.time)).forEach(g=>{const k=g.req+'|'+g.drv+'|'+g.veh;if(g.type==='Check-in')st[k]=g;else if(g.type==='Check-out')delete st[k]});
 return Object.values(st);
}

/* ============ Requests list ============ */
function vRequests(){
 const f=S.filter;
 let L=DB.requests.slice();
 if(isVendor())L=L.filter(x=>x.vendor==='V1000');else L=L.filter(x=>x.status!=='Draft');
 if(f.status)L=L.filter(x=>f.status==='Rejected'?['Rejected','Returned'].includes(x.status):x.status===f.status);
 if(f.plant)L=L.filter(x=>x.docs.some(dc=>DB.pos[dc.po]?.plant===f.plant));
 if(f.q){const q=f.q.toLowerCase();L=L.filter(x=>(x.id+reqPOs(x)+reqDOs(x)+DB.vendors[x.vendor].name).toLowerCase().includes(q))}
 L.sort((a,b)=>b.id.localeCompare(a.id));
 return ph(isVendor()?'My Requests':'All Requests',`${L.length} goods entry permit${L.length===1?'':'s'}`,null,isVendor()?`<button class="btn em" onclick="go('create')">${ic('add',16)} Create Request</button>`:'')+
 `<div class="pbody"><div class="card">
  <div class="filterbar">
   <div class="field" style="width:240px"><label for="fq">Search</label><input id="fq" class="inp" placeholder="No. Izin, PO, atau DO..." value="${esc(f.q)}" oninput="S.filter.q=this.value;rerenderKeep('fq')"></div>
   <div class="field"><label for="fp">Receiving Plant</label><select id="fp" class="inp" onchange="S.filter.plant=this.value;render()"><option value="">Semua Plant</option><option value="WH01 – Main Warehouse" ${f.plant==='WH01 – Main Warehouse'?'selected':''}>WH01 – Main Warehouse</option><option value="WH02 – Procurement Receiving" ${f.plant==='WH02 – Procurement Receiving'?'selected':''}>WH02 – Procurement Receiving</option></select></div>
   <div class="field"><label for="fs">Status</label><select id="fs" class="inp" onchange="S.filter.status=this.value;render()">${['','Draft','In Approval','Approved','Rejected'].filter(s=>isVendor()||s!=='Draft').map(s=>`<option value="${s}" ${f.status===s?'selected':''}>${s===''?'Semua Status':s==='Rejected'?'Rejected / Returned':s}</option>`).join('')}</select></div>
   ${f.status||f.q||f.plant?`<button class="btn ghost" onclick="S.filter={q:'',status:'',plant:''};render()">Clear filters</button>`:''}
  </div>
  <div class="tw"><table class="t"><thead><tr><th>Request</th>${isVendor()?'':'<th>Vendor</th>'}<th>PO</th><th>DO</th><th>Delivery period</th><th>Drivers / Vehicles</th><th>Status</th><th></th></tr></thead><tbody>
  ${L.map(x=>`<tr class="click" onclick="openReq('${x.id}','requests')"><td><b class="mono">${x.id}</b><div class="small muted">Created ${fd(x.created)}</div></td>${isVendor()?'':`<td>${DB.vendors[x.vendor].name}</td>`}<td class="mono">${reqPOs(x)}</td><td class="mono">${reqDOs(x)}</td><td class="num">${fd(x.start)} – ${fd(x.end)}</td><td class="num">${x.drivers.length} / ${x.vehicles.length}</td><td>${statusTag(x)}</td><td class="nav">${ic('chev',16)}</td></tr>`).join('')||`<tr><td colspan="8" class="empty">No requests match the filter.</td></tr>`}
  </tbody></table></div></div></div>`;
}
function rerenderKeep(id){const el=document.getElementById(id);const p=el.selectionStart;render();const n=document.getElementById(id);n.focus();try{n.setSelectionRange(p,p)}catch(e){}}

/* ============ Inbox ============ */
function inboxTable(L){
 if(!L.length)return '<div class="empty">Nothing waiting for your approval.</div>';
 return `<div class="tw"><table class="t"><thead><tr><th>Request</th><th>Vendor</th><th>PO / DO</th><th>Delivery period</th><th>Waiting since</th><th></th></tr></thead><tbody>
 ${L.map(x=>{const last=x.history[x.history.length-1];return `<tr class="click" onclick="openReq('${x.id}','inbox')"><td><b class="mono">${x.id}</b><div class="small muted">${itemsSummary(x)}</div></td><td>${DB.vendors[x.vendor].name}</td><td class="mono small">${reqPOs(x)}<br>${reqDOs(x)}</td><td class="num">${fd(x.start)} – ${fd(x.end)}</td><td class="num">${fdt(last.at)}</td><td class="nav">${ic('chev',16)}</td></tr>`}).join('')}
 </tbody></table></div>`;
}
function vInbox(){const p=myPos();const L=pendingFor(p);return ph('My Inbox',`${pos(p).code} · ${pos(p).name} · ${L.length} waiting`)+`<div class="pbody"><div class="card">${inboxTable(L)}</div></div>`}

/* ============ Request detail ============ */
function vDetail(){
 const R=reqById(S.detail);if(!R)return'';
 const V=DB.vendors[R.vendor];
 const p=myPos();const myStep=p&&R.status==='In Approval'&&R.approvals.find(a=>a.pos===p&&a.status==='Pending');
 const v=validity(R);
 let acts='';
 if(isVendor()){
  if(R.status==='Draft')acts=`<button class="btn" onclick="delDraft('${R.id}')">${ic('trash',16)} Delete</button><button class="btn em" onclick="editReq('${R.id}')">${ic('edit',16)} Edit</button>`;
  if(['Rejected','Returned'].includes(R.status))acts=`<button class="btn em" onclick="editReq('${R.id}',true)">${ic('edit',16)} Revise &amp; Resubmit</button>`;
 }
 if(R.status==='Approved')acts+=`<button class="btn ${isVendor()?'em':''}" onclick="S.tab='qr';render()">${ic('qr',16)} Show QR Code</button>`;
 const gl=DB.gate.filter(g=>g.req===R.id);
 const tabs=[['overview','Documents & Items'],['people','Drivers & Vehicles'],['approval','Approval'],['gate','Gate Log',gl.length],['history','History']];
 if(R.status==='Approved')tabs.splice(1,0,['qr','QR Code']);
 const back=S.from==='inbox'?'My Inbox':S.from==='home'?'Home':isVendor()?'My Requests':'All Requests';
 const header=ph(`<span class="mono">${R.id}</span>`,`${V.name} · Goods Entry Permit`,`<a onclick="go('${S.from||'requests'}')">${back}</a><span>/</span><span>${R.id}</span>`,acts,
 `<div class="kpis">
   <div class="kpi"><div class="k">Status</div><div class="v">${statusTag(R)}</div></div>
   <div class="kpi"><div class="k">Delivery period</div><div class="v num">${fd(R.start)} – ${fd(R.end)}</div></div>
   <div class="kpi"><div class="k">Purchase orders</div><div class="v mono">${reqPOs(R)}</div></div>
   <div class="kpi"><div class="k">Delivery orders</div><div class="v mono">${reqDOs(R)}</div></div>
   <div class="kpi"><div class="k">Items</div><div class="v num">${R.items.length}${R.items.some(i=>i.nonpo)?` <span class="tag warn plain">${R.items.filter(i=>i.nonpo).length} Non-PO</span>`:''}</div></div>
   <div class="kpi"><div class="k">Drivers / Vehicles</div><div class="v num">${R.drivers.length} / ${R.vehicles.length}</div></div>
 </div>
 <div class="tabs" role="tablist">${tabs.map(t=>`<button class="tab ${S.tab===t[0]?'on':''}" onclick="S.tab='${t[0]}';render()">${t[1]}${t[2]?`<span class="cnt">(${t[2]})</span>`:''}</button>`).join('')}</div>`);
 let body='';
 const banner=[];
 if(R.status==='Rejected'||R.status==='Returned'){const s=R.approvals.find(a=>['Rejected','Returned'].includes(a.status));banner.push(`<div class="msg err">${ic('err')}<div><b>${R.status==='Rejected'?'Rejected':'Returned for revision'} by ${pos(s.pos).holder} (${pos(s.pos).name})</b><br>${esc(s.comment)}</div></div>`)}
 if(myStep)banner.push(`<div class="msg info">${ic('info')}<div>This request is waiting for your approval as <b>${pos(p).name}</b> (step ${myStep.seq}). Review the documents, items, drivers and vehicles, then decide in the footer bar.</div></div>`);
 const issues=liveIssues(R);
 if(R.status==='Approved'&&issues.length&&v?.t!=='Expired')banner.push(`<div class="msg warn">${ic('warn')}<div><b>Some master documents expire before the delivery period ends.</b> Security will not be able to select these at check-in until the vendor renews them in master data.<ul>${issues.map(i=>`<li>${i}</li>`).join('')}</ul></div></div>`);
 if(S.tab==='overview'){
  body=`<div class="card"><div class="card-h"><h3>Reference documents</h3></div><div class="tw"><table class="t"><thead><tr><th>PO number</th><th>Description</th><th>Receiving plant</th><th>Delivery orders</th><th class="r">Actions</th></tr></thead><tbody>
   ${R.docs.map(dc=>{const P=DB.pos[dc.po];return `<tr><td class="mono"><b>${dc.po}</b></td><td>${esc(P.desc)}</td><td>${P.plant}</td><td class="num">${dc.dos.length?`${dc.dos.length} DO`:'<span class="muted">PO only, no DO</span>'}</td><td class="r"><button class="btn sm" onclick="doDetail('${dc.po}','${R.id}')">Detail</button></td></tr>`}).join('')}
   </tbody></table></div></div>
   <div class="card"><div class="card-h"><h3>Items to enter</h3><span class="muted small">${R.items.length} lines</span></div>${itemsReadTable(R.items)}</div>
   ${R.remarks?`<div class="card"><div class="card-h"><h3>Remarks</h3></div><div class="card-b">${esc(R.remarks)}</div></div>`:''}`;
 }
 if(S.tab==='people'){
  body=`<div class="card"><div class="card-h"><h3>Drivers</h3><span class="muted small">Current master data · renewals apply automatically</span></div><div class="tw"><table class="t nw"><thead><tr><th>Name</th><th>NIK</th><th>SIM</th><th>SIM valid until</th><th>Verification</th><th>Status for this period</th></tr></thead><tbody>
  ${R.drivers.map(id=>{const o=drv(id);const iss=docIssues(o,'drv',R.end);return `<tr><td><b>${esc(o.name)}</b><div class="small muted">${o.phone}</div></td><td class="mono">${o.nik}</td><td><span class="mono">${o.simNo}</span><div class="small muted">${o.simType}</div></td><td>${dateCell(o.simValid)}</td><td>${verifTag(o)}</td><td>${iss.length?`<span class="tag err">Expires before ${fd(R.end)}</span>`:'<span class="tag ok">Valid</span>'}</td></tr>`}).join('')||'<tr><td colspan="6" class="empty">No driver selected.</td></tr>'}</tbody></table></div></div>
  <div class="card"><div class="card-h"><h3>Vehicles</h3></div><div class="tw"><table class="t nw"><thead><tr><th>Plate</th><th>Type / Model</th><th>STNK annual tax</th><th>STNK 5-year</th><th>KIR</th><th>Verification</th><th>Status for this period</th></tr></thead><tbody>
  ${R.vehicles.map(id=>{const o=veh(id);const iss=docIssues(o,'veh',R.end);return `<tr><td><b class="mono">${o.plate}</b></td><td>${esc(o.type)}<div class="small muted">${esc(o.brand)} · ${o.year}</div></td><td>${dateCell(o.taxValid)}</td><td>${dateCell(o.stnkValid)}</td><td>${dateCell(o.kirValid)}<div class="small muted mono">${o.kirNo}</div></td><td>${verifTag(o)}</td><td>${iss.length?`<span class="tag err">${iss.map(i=>i.label).join(', ')} expires</span>`:'<span class="tag ok">Valid</span>'}</td></tr>`}).join('')||'<tr><td colspan="7" class="empty">No vehicle selected.</td></tr>'}</tbody></table></div></div>`;
 }
 if(S.tab==='approval'){
  body=R.approvals?`<div class="card"><div class="card-h"><h3>Approval</h3><span class="muted small">Same sequence = parallel, all must approve</span></div>${apprTable(R.approvals,true)}</div>`
  :'<div class="card"><div class="empty">The approval flow starts when the request is submitted.</div></div>';
 }
 if(S.tab==='qr'){
  body=`<div class="card"><div class="card-b"><div class="qrbox"><div class="qr" id="qrEl"></div>
   <div style="display:flex;flex-direction:column;gap:12px;min-width:0">
    <div><div class="sec-title mono">${R.id}</div><div class="muted">Show this QR code to Security at the gate. The code stays the same for the whole delivery period.</div></div>
    <dl class="kv"><dt>Vendor</dt><dd>${V.name}</dd><dt>Valid</dt><dd class="num">${fd(R.start)} – ${fd(R.end)} ${v?`<span class="tag ${v.c}">${v.t}</span>`:''}</dd>
    <dt>Drivers</dt><dd>${R.drivers.map(id=>esc(drv(id).name)).join(', ')}</dd><dt>Vehicles</dt><dd class="mono">${R.vehicles.map(id=>veh(id).plate).join(', ')}</dd></dl>
    <div class="msg info">${ic('info')}<div>Only the drivers and vehicles listed here can check in. Each first entry of the day requires a gate inspection.</div></div>
   </div></div></div></div>`;
 }
 if(S.tab==='gate'){
  body=`<div class="card">${gateTable(gl,true)}</div>`;
 }
 if(S.tab==='history'){
  body=`<div class="card"><div class="card-b"><div class="timeline">${R.history.slice().reverse().map(h=>`<div class="tl"><div class="b ${/Approved|issued|Submitted|Resubmitted/.test(h.act)?(h.act.includes('Submit')?'info':'ok'):/Reject|Return/.test(h.act)?'err':''}"></div><div><b>${h.act}</b> <span class="muted small">· ${fdt(h.at)}</span><div class="small muted">${esc(h.who)}</div>${h.comment?`<div style="margin-top:4px">${esc(h.comment)}</div>`:''}</div></div>`).join('')}</div></div></div>`;
 }
 const foot=myStep?`<div class="footer"><div class="sp"></div><button class="btn" onclick="decideDlg('return')">Return for Revision</button><button class="btn neg" onclick="decideDlg('reject')">Reject</button><button class="btn pos em" onclick="decideDlg('approve')">${ic('check',16)} Approve</button></div>`:'';
 return header+`<div class="pbody">${banner.join('')}${body}</div>`+foot;
}
function liveIssues(R){
 const out=[];
 R.drivers.forEach(id=>{const o=drv(id);docIssues(o,'drv',R.end).forEach(i=>out.push(`${esc(o.name)}: ${i.label} valid until ${fd(i.date)}`))});
 R.vehicles.forEach(id=>{const o=veh(id);docIssues(o,'veh',R.end).forEach(i=>out.push(`${o.plate}: ${i.label} valid until ${fd(i.date)}`))});
 return out;
}
function apTag(s){return `<span class="tag ${{Approved:'ok',Rejected:'err',Returned:'warn',Pending:'info'}[s]||''}">${s}</span>`}
function itemsReadTable(items){
 return `<div class="tw"><table class="t"><thead><tr><th>Source</th><th>Material</th><th>Description</th><th class="r">Qty</th><th>UoM</th><th class="r">Doc qty</th></tr></thead><tbody>
 ${items.map(i=>`<tr><td>${i.nonpo?'<span class="tag warn plain">Non-PO</span>':`<span class="mono small">${i.po}${i.do?' / '+i.do:''} · ${i.line}</span>`}</td><td class="mono">${i.mat||'–'}</td><td>${esc(i.desc)}${i.nonpo&&i.reason?`<div class="small muted">Reason: ${esc(i.reason)}</div>`:''}</td><td class="r num"><b>${i.qty}</b></td><td>${i.uom}</td><td class="r num muted">${i.nonpo?'–':i.docQty}</td></tr>`).join('')}</tbody></table></div>`;
}
function gateTable(L,compact){
 if(!L.length)return '<div class="empty">No gate activity recorded.</div>';
 return `<div class="tw"><table class="t"><thead><tr><th>Time</th>${compact?'':'<th>Request</th>'}<th>Event</th><th>Driver</th><th>Vehicle</th><th>Gate</th><th>Officer</th><th>Inspection</th></tr></thead><tbody>
 ${L.slice().sort((a,b)=>b.time.localeCompare(a.time)).map(g=>`<tr><td class="num">${fdt(g.time)}</td>${compact?'':`<td><a class="mono" style="color:var(--brand);cursor:pointer" onclick="openReq('${g.req}','gatelog')">${g.req}</a></td>`}
 <td><span class="tag ${g.type==='Check-in'?'ok':g.type==='Check-out'?'':'err'}">${g.type}</span>${g.fast?'<div class="small muted">Re-entry, inspected earlier today</div>':''}</td><td>${esc(drv(g.drv)?.name)}</td><td class="mono">${veh(g.veh)?.plate}</td><td>${g.gate}</td><td>${g.officer}</td>
 <td>${g.result?`<span class="tag ${g.result==='Fit'?'ok':'err'} plain">${g.result}</span> <span class="small muted">${esc(g.insp||'')}</span>${g.remarks?`<div class="small" style="color:var(--err)">${esc(g.remarks)}</div>`:''}`:'<span class="muted">–</span>'}</td></tr>`).join('')}</tbody></table></div>`;
}
function decideDlg(act){
 const t={approve:['Approve request','Approve','pos'],reject:['Reject request','Reject','neg'],return:['Return for revision','Return','']}[act];
 S.dialog={type:'decide',act,title:t[0],ok:t[1],cls:t[2]};dlg();
}
function delDraft(id){S.dialog={type:'confirm',title:'Delete draft',text:`Delete draft ${id}? This cannot be undone.`,ok:'Delete',fn:()=>{DB.requests=DB.requests.filter(r=>r.id!==id);save();go('requests');toast('Draft deleted')}};dlg()}

/* ============ Create / edit request ============ */
let UID=0;
function newForm(){return{docs:[],drafts:[{uid:++UID,input:'',err:''}],items:[],removed:[],start:'',end:'',drivers:[],vehicles:[],remarks:'',editId:null,autoDates:true}}
function editReq(id,resub){
 const R=reqById(id);
 S.form={docs:JSON.parse(JSON.stringify(R.docs)),drafts:[],items:JSON.parse(JSON.stringify(R.items)),removed:[],start:R.start,end:R.end,drivers:[...R.drivers],vehicles:[...R.vehicles],remarks:R.remarks,editId:id,resub:!!resub,autoDates:false};
 S.ctab='docs';go('create',{keep:true});
}
function poError(n){
 if(!/^\d{10}$/.test(n))return 'Enter a 10-digit PO number, for example 4500012345.';
 const P=DB.pos[n];
 if(!P)return `PO ${n} does not exist in SAP. Check the number on your purchase order.`;
 if(P.vendor!=='V1000')return `PO ${n} is issued to another vendor and cannot be used by PT Sinar Logistik Abadi.`;
 if(S.form.docs.some(x=>x.po===n))return `PO ${n} is already in this request.`;
 return '';
}
function addPORow(){S.form.drafts.push({uid:++UID,input:'',err:''});render();const els=document.querySelectorAll('[data-po-in]');els[els.length-1]?.focus()}
function delPORow(uid){S.form.drafts=S.form.drafts.filter(x=>x.uid!==uid);render()}
function checkPORow(uid){
 const F=S.form;const r=F.drafts.find(x=>x.uid===uid);if(!r)return;const n=r.input.trim();
 r.err=poError(n);if(r.err)return render();
 F.drafts=F.drafts.filter(x=>x!==r);
 const dos=Object.keys(DB.pos[n].dos);F.docs.push({po:n,dos:dos.slice()});refreshItems();render();
 toast(`PO ${n} added${dos.length?` with ${dos.length} delivery order${dos.length>1?'s':''}`:', no delivery order yet'}`);
}
function poHelp(uid){S.dialog={type:'pohelp',uid,q:''};dlg();focusQ()}
function pickPO(po){const uid=S.dialog.uid;closeDlg();const r=S.form.drafts.find(x=>x.uid===uid);if(r){r.input=po;checkPORow(uid)}}
function doDetail(po,rid){const dc=(rid?reqById(rid).docs:S.form.docs).find(x=>x.po===po);S.dialog={type:'dos',po,rid,sel:[...dc.dos],q:''};dlg()}
function applyDOs(){const D=S.dialog;const dc=S.form.docs.find(x=>x.po===D.po);dc.dos=D.sel.slice();closeDlg();refreshItems();render();toast(`${dc.dos.length} delivery order${dc.dos.length===1?'':'s'} selected for PO ${D.po}`)}
function removePO(po){S.form.docs=S.form.docs.filter(x=>x.po!==po);refreshItems();render()}
function refreshItems(){
 const F=S.form;const prev={};F.items.forEach(i=>{if(!i.nonpo)prev[i.key]=i});
 const auto=buildItems(F.docs).map(i=>prev[i.key]?{...i,qty:prev[i.key].qty}:i);
 F.items=[...auto.filter(i=>!F.removed.includes(i.key)),...F.items.filter(i=>i.nonpo)];
 if(F.autoDates){const ds=F.docs.flatMap(dc=>dc.dos.map(x=>DB.pos[dc.po].dos[x].date)).sort();if(ds.length){F.start=ds[0]<T0?T0:ds[0];F.end=ds[ds.length-1]<F.start?F.start:ds[ds.length-1]}}
}
function delItem(k){const F=S.form;const it=F.items.find(i=>i.key===k);if(!it.nonpo)F.removed.push(k);F.items=F.items.filter(i=>i.key!==k);render()}
function restoreItems(){S.form.removed=[];refreshItems();render();toast('Removed document items restored')}
function addItem(){S.form.items.push({key:'np'+Date.now(),nonpo:true,mat:'',desc:'',qty:1,uom:'PC',reason:''});render();const els=document.querySelectorAll('[data-np-desc]');els[els.length-1]?.focus()}
function setItem(k,f,v){const it=S.form.items.find(i=>i.key===k);it[f]=f==='qty'?Number(v):v}
function removeLine(list,id){S.form[list]=S.form[list].filter(x=>x!==id);render()}
function mHelp(kind){S.dialog={type:'mhelp',kind,q:'',sel:[]};dlg();focusQ()}
function addPicked(){const D=S.dialog;const list=D.kind==='drv'?'drivers':'vehicles';S.form[list]=[...S.form[list],...D.sel];closeDlg();render();toast(`${D.sel.length} ${D.kind==='drv'?'driver':'vehicle'}${D.sel.length>1?'s':''} added`)}
function focusQ(){const q=document.getElementById('vhq');if(q){q.focus();const n=q.value.length;try{q.setSelectionRange(n,n)}catch(e){}}}

function validateForm(){
 const F=S.form,E=[];
 F.drafts.filter(r=>r.input.trim()).forEach(r=>E.push(['docs',`PO line "${r.input.trim()}" is not checked yet. Press Check or remove the line.`]));
 if(!F.docs.length)E.push(['docs','Add at least one valid PO.']);
 F.docs.forEach(dc=>{if(Object.keys(DB.pos[dc.po].dos).length&&!dc.dos.length)E.push(['docs',`Select at least one delivery order for PO ${dc.po} in Detail, or remove the PO.`])});
 if(!F.items.length)E.push(['items','The request has no items. Restore document items or add an item.']);
 F.items.forEach(i=>{
  if(!(i.qty>0))E.push(['items',`Quantity must be greater than 0 for "${i.desc||'new item'}".`]);
  if(i.nonpo&&!i.desc.trim())E.push(['items','Enter a description for every Non-PO item.']);
  if(i.nonpo&&!i.reason.trim())E.push(['items',`Give a reason for Non-PO item "${i.desc||'new item'}".`]);
 });
 if(!F.start||!F.end)E.push(['period','Set the delivery start and end date.']);
 else{if(F.end<F.start)E.push(['period','The end date is before the start date.']);if(F.start<T0)E.push(['period','The start date cannot be in the past.'])}
 if(!F.drivers.length)E.push(['drivers','Add at least one driver.']);
 if(!F.vehicles.length)E.push(['vehicles','Add at least one vehicle.']);
 if(F.end){
  F.drivers.forEach(id=>{const o=drv(id);docIssues(o,'drv',F.end).forEach(i=>E.push(['drivers',`${o.name}: ${i.label} expires ${fd(i.date)}, before the delivery end date ${fd(F.end)}.`]))});
  F.vehicles.forEach(id=>{const o=veh(id);docIssues(o,'veh',F.end).forEach(i=>E.push(['vehicles',`${o.plate}: ${i.label} expires ${fd(i.date)}, before the delivery end date ${fd(F.end)}.`]))});
 }
 return E;
}
function saveForm(submit){
 const F=S.form;
 if(submit){const E=validateForm();if(E.length){S.errs=E;S.ctab=E[0][0];render();window.scrollTo(0,0);return}}
 else if(!F.docs.length){S.errs=[['docs','Add at least one PO before saving a draft.']];S.ctab='docs';return render()}
 let R=F.editId&&reqById(F.editId);
 if(!R){R={id:'IMB-2026-'+String(++DB.seq).padStart(6,'0'),vendor:'V1000',status:'Draft',created:T0,history:[{at:nowStamp(),who:'Andi Wijaya (Vendor)',act:'Created'}],approvals:null};DB.requests.push(R)}
 Object.assign(R,{docs:F.docs,items:F.items,start:F.start,end:F.end,drivers:F.drivers,vehicles:F.vehicles,remarks:F.remarks});
 if(submit){if(F.resub)R._resub=true;submitReq(R)}else if(F.resub){R.status='Draft'}
 save();S.detail=R.id;S.tab=submit?'approval':'overview';
 go('detail',{from:'requests'});
 toast(submit?`${R.id} submitted for approval`:`${R.id} saved as draft`);
}
const CTABS=[['docs','Purchase Orders'],['items','Items'],['period','Delivery Period'],['drivers','Drivers'],['vehicles','Vehicles'],['review','Review & Submit']];
function ctab(k){S.ctab=k;render();window.scrollTo(0,0)}
function vCreate(){
 const F=S.form;const E=S.errs||[];const has=k=>E.some(e=>e[0]===k);
 const cnt={docs:F.docs.length,items:F.items.length,drivers:F.drivers.length,vehicles:F.vehicles.length};
 const title=F.editId?(F.resub?`Revise ${F.editId}`:`Edit ${F.editId}`):'Create Goods Entry Permit';
 const i=CTABS.findIndex(t=>t[0]===S.ctab);
 const header=ph(title,'PT Sinar Logistik Abadi · Vendor V1000',`<a onclick="go('home')">Home</a><span>/</span><span>${F.editId||'New request'}</span>`,'',
 `<div class="kpis">
  <div class="kpi"><div class="k">Status</div><div class="v"><span class="tag">${F.editId?reqById(F.editId).status:'New'}</span></div></div>
  <div class="kpi"><div class="k">Purchase orders</div><div class="v num">${F.docs.length} PO · ${F.docs.reduce((a,x)=>a+x.dos.length,0)} DO</div></div>
  <div class="kpi"><div class="k">Items</div><div class="v num">${F.items.length}</div></div>
  <div class="kpi"><div class="k">Delivery period</div><div class="v num">${F.start&&F.end?fd(F.start)+' – '+fd(F.end):'–'}</div></div>
  <div class="kpi"><div class="k">Drivers / Vehicles</div><div class="v num">${F.drivers.length} / ${F.vehicles.length}</div></div>
 </div>
 <div class="tabs" role="tablist">${CTABS.map(t=>`<button class="tab ${S.ctab===t[0]?'on':''}" role="tab" onclick="ctab('${t[0]}')">${t[1]}${cnt[t[0]]!==undefined?`<span class="cnt">(${cnt[t[0]]})</span>`:''}${has(t[0])?'<span class="terr">!</span>':''}</button>`).join('')}</div>`);
 const errBox=E.length?`<div class="msg err" id="errbox">${ic('err')}<div><b>Fix ${E.length} issue${E.length>1?'s':''} before submitting</b><ul>${E.map(e=>`<li><a class="lnk" onclick="ctab('${e[0]}')">${CTABS.find(t=>t[0]===e[0])[1]}</a>: ${esc(e[1])}</li>`).join('')}</ul></div></div>`:'';
 const body={docs:cDocs,items:cItems,period:cPeriod,drivers:()=>cMaster('drv'),vehicles:()=>cMaster('veh'),review:cReview}[S.ctab]();
 return header+`<div class="pbody">${errBox}${F.resub?`<div class="msg info">${ic('info')}<div>After you resubmit, the approval flow starts again from step 1.</div></div>`:''}${body}</div>
 <div class="footer">${E.length?`<span class="tag err">${E.length} issue${E.length>1?'s':''}</span>`:''}
  <button class="btn ghost" ${i===0?'disabled':''} onclick="ctab('${CTABS[Math.max(0,i-1)][0]}')">Previous</button><button class="btn ghost" ${i===CTABS.length-1?'disabled':''} onclick="ctab('${CTABS[Math.min(CTABS.length-1,i+1)][0]}')">Next</button>
  <div class="sp"></div><button class="btn ghost" onclick="go(S.form.editId?'requests':'home')">Cancel</button><button class="btn" onclick="saveForm(false)">Save as Draft</button><button class="btn em" onclick="saveForm(true)">${F.resub?'Resubmit':'Submit'}</button></div>`;
}
function cDocs(){
 const F=S.form;
 const rows=F.docs.map(dc=>{const P=DB.pos[dc.po];const tot=Object.keys(P.dos).length;return `<tr>
  <td class="mono"><b>${dc.po}</b></td><td>${esc(P.desc)}</td><td>${P.plant}</td>
  <td class="num">${tot?`${dc.dos.length} of ${tot} selected`:'<span class="muted">PO only, no DO</span>'}${tot&&!dc.dos.length?' <span class="tag err plain">None</span>':''}</td>
  <td><span class="tag ok">Valid</span></td>
  <td class="r nw"><button class="btn sm" onclick="doDetail('${dc.po}')">Detail</button> <button class="iconbtn" style="display:inline-grid;vertical-align:middle" title="Remove PO" onclick="removePO('${dc.po}')">${ic('trash',16)}</button></td></tr>`}).join('');
 const drafts=F.drafts.map(r=>`<tr>
  <td><div class="vh"><input data-po-in class="inp mono ${r.err?'e':''}" inputmode="numeric" maxlength="10" placeholder="45000xxxxx" aria-label="PO number" value="${esc(r.input)}" oninput="S.form.drafts.find(x=>x.uid===${r.uid}).input=this.value" onkeydown="if(event.key==='Enter'){event.preventDefault();checkPORow(${r.uid})}"><button title="Search PO" aria-label="Search PO" onclick="poHelp(${r.uid})">${ic('search',16)}</button></div></td>
  <td colspan="3">${r.err?`<span style="color:var(--err)">${esc(r.err)}</span>`:'<span class="muted small">Type the PO number and press Enter, or use search.</span>'}</td>
  <td>${r.err?'<span class="tag err">Invalid</span>':'<span class="tag">Not checked</span>'}</td>
  <td class="r nw"><button class="btn sm em" onclick="checkPORow(${r.uid})">Check</button> <button class="iconbtn" style="display:inline-grid;vertical-align:middle" title="Remove line" onclick="delPORow(${r.uid})">${ic('trash',16)}</button></td></tr>`).join('');
 return `<div class="card"><div class="card-h"><h3>Purchase Orders (${F.docs.length})</h3><span class="muted small">Only POs issued to your vendor account are accepted. Open Detail to choose delivery orders.</span><div class="acts"><button class="btn sm em" onclick="addPORow()">${ic('add',14)} Add PO</button></div></div>
 <div class="tw"><table class="t"><thead><tr><th style="width:260px">PO number</th><th>Description</th><th>Receiving plant</th><th>Delivery orders</th><th>Status</th><th class="r">Actions</th></tr></thead><tbody>
 ${rows+drafts||'<tr><td colspan="6" class="empty">No PO yet. Click Add PO to start.</td></tr>'}</tbody></table></div>
 <div class="card-b small muted" style="border-top:1px solid var(--line-soft)">Sample numbers: 4500012345 (2 DOs), 4500012400 (10 DOs), 4500013002 (no DO), 4500019001 (other vendor).</div></div>`;
}
function cItems(){
 const F=S.form;
 return `<div class="card"><div class="card-h"><h3>Items to Enter (${F.items.length})</h3><span class="muted small">Filled from the selected DOs. Adjust quantities, remove lines or add Non-PO items.</span>
  <div class="acts">${F.removed.length?`<button class="btn ghost sm" onclick="restoreItems()">${ic('refresh',14)} Restore removed (${F.removed.length})</button>`:''}<button class="btn sm" onclick="addItem()">${ic('add',14)} Add Item</button></div></div>
  ${F.items.length?`<div class="tw"><table class="t"><thead><tr><th>PO / DO · Item</th><th>Material</th><th style="min-width:220px">Description</th><th class="r">Qty</th><th>UoM</th><th class="r">Doc qty</th><th></th></tr></thead><tbody>
  ${F.items.map(i=>`<tr><td>${i.nonpo?'<span class="tag warn plain">Non-PO</span>':`<span class="mono small">${i.po}${i.do?'<br>DO '+i.do:''} · ${i.line}</span>`}</td>
   <td class="mono">${i.nonpo?'–':i.mat}</td>
   <td>${i.nonpo?`<input data-np-desc class="inp sm" placeholder="Item description" aria-label="Item description" value="${esc(i.desc)}" oninput="setItem('${i.key}','desc',this.value)"><input class="inp sm" style="margin-top:6px" placeholder="Reason (why not in PO)" aria-label="Reason" value="${esc(i.reason)}" oninput="setItem('${i.key}','reason',this.value)">`:esc(i.desc)}</td>
   <td class="r"><input class="inp sm num" style="width:90px;text-align:right" type="number" min="0" aria-label="Quantity" value="${i.qty}" onchange="setItem('${i.key}','qty',this.value);render()">${!i.nonpo&&i.qty>i.docQty?'<div class="small" style="color:var(--warn)">Above doc qty</div>':''}</td>
   <td>${i.nonpo?`<select class="inp sm" style="width:80px" aria-label="Unit" onchange="setItem('${i.key}','uom',this.value)">${['PC','BOX','PR','KG','L','M','RM','DR','SET'].map(u=>`<option ${u===i.uom?'selected':''}>${u}</option>`).join('')}</select>`:i.uom}</td>
   <td class="r num muted">${i.nonpo?'–':i.docQty}</td>
   <td class="r"><button class="iconbtn" title="Remove line" onclick="delItem('${i.key}')">${ic('trash',16)}</button></td></tr>`).join('')}</tbody></table></div>`:'<div class="empty">Add a PO to list its items, or add a Non-PO item.</div>'}
 </div>`;
}
function cPeriod(){
 const F=S.form;const has=(S.errs||[]).some(e=>e[0]==='period');
 return `<div class="card"><div class="card-h"><h3>Delivery Period</h3><span class="muted small">Proposed from the DO delivery dates. The QR code is valid only within this period.</span></div>
  <div class="card-b"><div class="inline">
   <div class="field" style="width:200px"><label for="dS">Start date <span class="req">*</span></label><input id="dS" type="date" class="inp ${has?'e':''}" min="${T0}" value="${F.start}" onchange="S.form.start=this.value;S.form.autoDates=false;render()"></div>
   <div class="field" style="width:200px"><label for="dE">End date <span class="req">*</span></label><input id="dE" type="date" class="inp ${has?'e':''}" min="${F.start||T0}" value="${F.end}" onchange="S.form.end=this.value;S.form.autoDates=false;render()"></div>
   ${F.start&&F.end&&F.end>=F.start?`<span class="tag info plain">${daysTo(F.end)-daysTo(F.start)+1} day${F.end===F.start?'':'s'}</span>`:''}
  </div></div>
  ${F.docs.some(d=>d.dos.length)?`<div class="tw"><table class="t"><thead><tr><th>DO</th><th>PO</th><th>DO delivery date</th></tr></thead><tbody>${F.docs.flatMap(dc=>dc.dos.map(x=>`<tr><td class="mono">${x}</td><td class="mono">${dc.po}</td><td class="num">${fd(DB.pos[dc.po].dos[x].date)}</td></tr>`)).join('')}</tbody></table></div>`:''}
 </div>`;
}
function cMaster(kind){
 const F=S.form;const isD=kind==='drv';const list=isD?F.drivers:F.vehicles;const by=F.end||F.start;
 const head=isD?'<th>Name</th><th>NIK</th><th>SIM</th><th>SIM valid until</th>':'<th>Plate</th><th>Type / Model</th><th>STNK tax</th><th>STNK 5-yr</th><th>KIR</th>';
 const rows=list.map(id=>{const o=isD?drv(id):veh(id);const iss=by?docIssues(o,kind,by):[];
  const cells=isD?`<td><b>${esc(o.name)}</b><div class="small muted">${o.phone}</div></td><td class="mono">${o.nik}</td><td><span class="mono">${o.simNo}</span><div class="small muted">${o.simType}</div></td><td>${dateCell(o.simValid)}</td>`
   :`<td><b class="mono">${o.plate}</b></td><td>${esc(o.type)}<div class="small muted">${esc(o.brand)} · ${o.year}</div></td><td>${dateCell(o.taxValid)}</td><td>${dateCell(o.stnkValid)}</td><td>${dateCell(o.kirValid)}</td>`;
  return `<tr>${cells}<td>${iss.length?`<span class="tag err">${iss.map(i=>i.label).join(', ')} expires before ${fd(by)}</span>`:'<span class="tag ok">Valid</span>'}</td><td class="r"><button class="iconbtn" title="Remove" onclick="removeLine('${isD?'drivers':'vehicles'}','${id}')">${ic('trash',16)}</button></td></tr>`}).join('');
 return `<div class="card"><div class="card-h"><h3>${isD?'Drivers':'Vehicles'} (${list.length})</h3><span class="muted small">Search your verified ${isD?'drivers':'vehicles'}. Documents must be valid until ${by?fd(by):'the end date'}.</span><div class="acts"><button class="btn sm em" onclick="mHelp('${kind}')">${ic('add',14)} Add ${isD?'Driver':'Vehicle'}</button></div></div>
 <div class="tw"><table class="t nw"><thead><tr>${head}<th>Check</th><th></th></tr></thead><tbody>${rows||`<tr><td colspan="${isD?6:7}" class="empty">No ${isD?'driver':'vehicle'} added. Click Add ${isD?'Driver':'Vehicle'} to search your verified master data.</td></tr>`}</tbody></table></div>
 <div class="card-b small muted" style="border-top:1px solid var(--line-soft)">New ${isD?'drivers':'vehicles'} are registered in <a class="lnk" onclick="go('${isD?'drivers':'vehicles'}')">Master Data</a> and must be verified by the Security Administrator before they can be added here.</div></div>`;
}
function cReview(){
 const F=S.form;
 return `<div class="card"><div class="card-h"><h3>Summary</h3></div><div class="card-b"><div class="grid g4">
   <div><div class="small muted">Documents</div><b>${F.docs.length} PO · ${F.docs.reduce((a,x)=>a+x.dos.length,0)} DO</b></div>
   <div><div class="small muted">Items</div><b>${F.items.length} lines${F.items.some(i=>i.nonpo)?` · ${F.items.filter(i=>i.nonpo).length} Non-PO`:''}</b></div>
   <div><div class="small muted">Period</div><b class="num">${F.start?fd(F.start)+' – '+fd(F.end):'–'}</b></div>
   <div><div class="small muted">Drivers / Vehicles</div><b>${F.drivers.length} / ${F.vehicles.length}</b></div>
  </div></div></div>
  <div class="card"><div class="card-h"><h3>Remarks for approvers</h3></div><div class="card-b"><textarea id="rmk" class="inp" aria-label="Remarks" placeholder="Optional, e.g. unloading needs forklift" oninput="S.form.remarks=this.value">${esc(F.remarks)}</textarea></div></div>
  <div class="card"><div class="card-h"><h3>Approval</h3><span class="muted small">Same sequence = parallel</span></div>${apprTable(DB.flow.filter(f=>f.active).sort((a,b)=>a.seq-b.seq).map(f=>({seq:f.seq,pos:f.pos})),false)}</div>`;
}
function apprTable(steps,withStatus){
 return `<div class="tw"><table class="t"><thead><tr><th>Seq</th><th>Position code</th><th>Position</th><th>Holder</th>${withStatus?'<th>Status</th><th>Decided at</th><th>Comment</th>':''}</tr></thead><tbody>
 ${steps.map(a=>{const P=pos(a.pos);return `<tr><td class="num">${a.seq}</td><td class="mono">${a.pos}</td><td>${P.name}</td><td>${P.holder}</td>${withStatus?`<td>${apTag(a.status)}</td><td class="num">${fdt(a.at)}</td><td>${esc(a.comment)||'<span class="muted">–</span>'}</td>`:''}</tr>`}).join('')}</tbody></table></div>`;
}

/* ============ Master data ============ */
const DRVF=[['name','Full name'],['nik','NIK'],['phone','Mobile number'],['simType','SIM type'],['simNo','SIM number'],['simValid','SIM valid until',1]];
const VEHF=[['plate','Plate number'],['type','Vehicle type'],['brand','Brand / model'],['year','Year'],['stnkNo','STNK number'],['taxValid','STNK annual tax valid until',1],['stnkValid','STNK 5-year valid until',1],['kirNo','KIR number'],['kirValid','KIR valid until',1]];
const DRVFILES=[['ktp','KTP'],['sim','SIM'],['photo','Driver photo']];
const VEHFILES=[['stnk','STNK'],['tax','Annual tax receipt (SKPD)'],['kir','KIR certificate / card'],['photo','Vehicle photo']];
function verifTag(o){
 let h=o.verif==='Verified'?'<span class="tag ok">Verified</span>':o.verif==='Pending'?'<span class="tag info">Pending verification</span>':'<span class="tag err">Rejected</span>';
 if(o.change)h+=' <span class="tag warn plain">Update pending</span>';
 else if(o.changeRej)h+=' <span class="tag err plain">Update rejected</span>';
 const note=o.verif==='Rejected'?o.verifNote:(!o.change&&o.changeRej?o.changeRej.note:'');
 return h+(note?`<div class="small" style="color:var(--err);margin-top:3px;white-space:normal;max-width:260px">${esc(note)}</div>`:'');
}
function vDrivers(){
 const df = S.drvFilter = S.drvFilter || { q: '', verif: '' };
 let L = DB.drivers.filter(x => x.vendor === 'V1000');
 if (df.verif) L = L.filter(x => x.verif === df.verif);
 if (df.q) {
   const q = df.q.toLowerCase();
   L = L.filter(o => (o.name + o.nik + o.simNo + (o.phone||'')).toLowerCase().includes(q));
 }
 return ph('Drivers',`${L.length} registered drivers · PT Sinar Logistik Abadi`,null,`<button class="btn em" onclick="drvDlg()">${ic('add',16)} Register Driver</button>`)+
 `<div class="pbody"><div class="msg info">${ic('info')}<div>New drivers and every document change are checked by the <b>Security Administrator</b> before use. Only verified drivers can be added to a request. While an update waits for verification, the current verified data stays in use. NIK cannot be changed; register a new driver instead.</div></div>
 <div class="card">
  <div class="filterbar">
   <div class="field" style="width:260px"><label for="dfq">Search</label><input id="dfq" class="inp" placeholder="Cari nama, NIK, no. SIM..." value="${esc(df.q)}" oninput="S.drvFilter.q=this.value;rerenderKeep('dfq')"></div>
   <div class="field"><label for="dfv">Status Verifikasi</label><select id="dfv" class="inp" onchange="S.drvFilter.verif=this.value;render()"><option value="">Semua Status</option><option value="Verified" ${df.verif==='Verified'?'selected':''}>Verified</option><option value="Pending" ${df.verif==='Pending'?'selected':''}>Pending Verification</option><option value="Rejected" ${df.verif==='Rejected'?'selected':''}>Rejected</option></select></div>
   ${df.q || df.verif ? `<button class="btn ghost" onclick="S.drvFilter={q:'',verif:''};render()">Clear filters</button>` : ''}
  </div>
  <div class="tw"><table class="t"><thead><tr><th>Name</th><th>NIK</th><th>SIM number</th><th>Type</th><th>SIM valid until</th><th>Documents</th><th>Validity</th><th>Verification</th><th></th></tr></thead><tbody>
  ${L.map(o=>`<tr class="click" onclick="drvDlg('${o.id}')"><td><b>${esc(o.name)}</b><div class="small muted">${o.id} · ${o.phone}</div></td><td class="mono">${o.nik}</td><td class="mono">${o.simNo}</td><td>${o.simType}</td><td>${dateCell(o.simValid)}</td><td class="small">${DRVFILES.filter(k=>o.files[k[0]]).length}/3</td><td>${o.verif==='Verified'?healthTag(o,'drv'):'<span class="muted">–</span>'}</td><td>${verifTag(o)}</td><td class="nav">${ic('chev',16)}</td></tr>`).join('') || '<tr><td colspan="9" class="empty">Tidak ada data driver yang sesuai filter.</td></tr>'}
  </tbody></table></div></div></div>`;
}
function vVehicles(){
 const vf = S.vehFilter = S.vehFilter || { q: '', verif: '' };
 let L = DB.vehicles.filter(x => x.vendor === 'V1000');
 if (vf.verif) L = L.filter(x => x.verif === vf.verif);
 if (vf.q) {
   const q = vf.q.toLowerCase();
   L = L.filter(o => (o.plate + o.type + (o.brand||'')).toLowerCase().includes(q));
 }
 return ph('Vehicles',`${L.length} registered vehicles · PT Sinar Logistik Abadi`,null,`<button class="btn em" onclick="vehDlg()">${ic('add',16)} Register Vehicle</button>`)+
 `<div class="pbody"><div class="msg info">${ic('info')}<div>New vehicles and every renewal of STNK tax, STNK 5-year or KIR are checked by the <b>Security Administrator</b> before use. The current verified data stays in use until the update is verified. The plate number cannot be changed; register a new vehicle instead.</div></div>
 <div class="card">
  <div class="filterbar">
   <div class="field" style="width:260px"><label for="vfq">Search</label><input id="vfq" class="inp" placeholder="Cari plat nomor, tipe, merk..." value="${esc(vf.q)}" oninput="S.vehFilter.q=this.value;rerenderKeep('vfq')"></div>
   <div class="field"><label for="vfv">Status Verifikasi</label><select id="vfv" class="inp" onchange="S.vehFilter.verif=this.value;render()"><option value="">Semua Status</option><option value="Verified" ${vf.verif==='Verified'?'selected':''}>Verified</option><option value="Pending" ${vf.verif==='Pending'?'selected':''}>Pending Verification</option><option value="Rejected" ${vf.verif==='Rejected'?'selected':''}>Rejected</option></select></div>
   ${vf.q || vf.verif ? `<button class="btn ghost" onclick="S.vehFilter={q:'',verif:''};render()">Clear filters</button>` : ''}
  </div>
  <div class="tw"><table class="t"><thead><tr><th>Plate</th><th>Type / Model</th><th>STNK annual tax</th><th>STNK 5-year</th><th>KIR</th><th>Documents</th><th>Validity</th><th>Verification</th><th></th></tr></thead><tbody>
  ${L.map(o=>`<tr class="click" onclick="vehDlg('${o.id}')"><td><b class="mono">${o.plate}</b><div class="small muted">${o.id}</div></td><td>${esc(o.type)}<div class="small muted">${esc(o.brand)} · ${o.year}</div></td><td>${dateCell(o.taxValid)}</td><td>${dateCell(o.stnkValid)}</td><td>${dateCell(o.kirValid)}<div class="small muted mono">${o.kirNo}</div></td><td class="small">${VEHFILES.filter(k=>o.files[k[0]]).length}/4</td><td>${o.verif==='Verified'?healthTag(o,'veh'):'<span class="muted">–</span>'}</td><td>${verifTag(o)}</td><td class="nav">${ic('chev',16)}</td></tr>`).join('') || '<tr><td colspan="9" class="empty">Tidak ada data kendaraan yang sesuai filter.</td></tr>'}
  </tbody></table></div></div></div>`;
}
function upl(id,label,val,req){return `<div class="field"><label>${label}${req?' <span class="req">*</span>':''}</label><div class="upl ${val?'has':''}">${ic(val?'ok':'upload',18)}<span class="fn">${val?esc(val):'<span class="muted">No file. PDF, JPG or PNG, max 5 MB</span>'}</span><label class="btn sm" for="${id}">${val?'Replace':'Upload'}</label><input type="file" id="${id}" accept=".pdf,image/*" onchange="pickFile('${id}',this)"></div></div>`}
function pickFile(id,inp){const f=inp.files[0];if(!f)return;S.dialog.files[id.replace(/^f_/,'')]=f.name;dlg()}
function withChange(o){const c=JSON.parse(JSON.stringify(o));if(o.change){Object.assign(c,o.change.data);c.files={...o.files,...o.change.files}}return c}
function drvDlg(id){const o=id?drv(id):null;const c=o?withChange(o):null;S.dialog={type:'drv',id,o:c||{name:'',nik:'',phone:'',simNo:'',simType:'B1 Umum',simValid:''},files:c?{...c.files}:{ktp:'',sim:'',photo:''}};dlg()}
function vehDlg(id){const o=id?veh(id):null;const c=o?withChange(o):null;S.dialog={type:'veh',id,o:c||{plate:'',type:'CDE (Colt Diesel Engkel)',brand:'',year:2023,stnkNo:'',taxValid:'',stnkValid:'',kirNo:'',kirValid:''},files:c?{...c.files}:{stnk:'',tax:'',kir:'',photo:''}};dlg()}
function renewDlg(kind,id,field){S.dialog={type:'renew',kind,id,field,files:{renew:''},date:''};dlg()}
function masterBanner(o){
 if(!o)return '';
 if(o.change)return `<div class="msg warn">${ic('warn')}<div>An update submitted ${fdt(o.change.at)} is waiting for Security Administrator verification. The form shows the submitted values. Saving again replaces that update.</div></div>`;
 if(o.verif==='Pending')return `<div class="msg info">${ic('info')}<div>Waiting for Security Administrator verification since ${fdt(o.submitted)}.</div></div>`;
 if(o.verif==='Rejected')return `<div class="msg err">${ic('err')}<div><b>Verification rejected:</b> ${esc(o.verifNote)}<br>Correct the data or documents and save to resubmit.</div></div>`;
 return '';
}
/* Saves a master record: new and rejected records go to verification; changes on verified records wait as a pending update */
function submitMaster(kind,D){
 const list=kind==='drv'?DB.drivers:DB.vehicles;const F=kind==='drv'?DRVF:VEHF;const label=kind==='drv'?'Driver':'Vehicle';
 const o=D.o;
 if(!D.id){list.push({...o,id:(kind==='drv'?'DRV-':'VEH-')+String(list.length+1).padStart(4,'0'),vendor:'V1000',files:D.files,history:[],verif:'Pending',submitted:nowStamp(),change:null});return toast(`${label} submitted. The Security Administrator will verify the documents.`)}
 const cur=list.find(x=>x.id===D.id);
 if(cur.verif!=='Verified'){Object.assign(cur,o,{files:D.files,verif:'Pending',submitted:nowStamp(),verifNote:''});return toast(`${label} resubmitted for verification`)}
 const data={},files={};
 F.forEach(([k])=>{if(String(o[k])!==String(cur[k]))data[k]=o[k]});
 Object.keys(D.files).forEach(k=>{if(D.files[k]!==cur.files[k])files[k]=D.files[k]});
 if(!Object.keys(data).length&&!Object.keys(files).length){cur.change=null;return toast('No changes to submit')}
 cur.change={at:nowStamp(),data,files};cur.changeRej=null;
 toast('Update submitted for verification. Current data stays in use until verified.');
}

/* ============ Admin: master verification ============ */
function verifQueue(kind){return(kind==='drv'?DB.drivers:DB.vehicles).filter(o=>o.verif==='Pending'||o.change)}
function vVerify(){
 const k=S.vtab||'drv';const L=verifQueue(k);const F=k==='drv'?DRVF:VEHF;
 return ph('Master Verification','Check vendor drivers and vehicles against their documents before they can be used in requests','','',
 `<div class="tabs" role="tablist">${[['drv','Drivers'],['veh','Vehicles']].map(t=>`<button class="tab ${k===t[0]?'on':''}" onclick="S.vtab='${t[0]}';render()">${t[1]}<span class="cnt">(${verifQueue(t[0]).length})</span></button>`).join('')}</div>`)+
 `<div class="pbody"><div class="card">${L.length?`<div class="tw"><table class="t"><thead><tr><th>Request type</th><th>${k==='drv'?'Driver':'Vehicle'}</th><th>Vendor</th><th>Submitted</th><th>What to check</th><th class="r"></th></tr></thead><tbody>
 ${L.map(o=>{const isNew=o.verif==='Pending';const what=isNew?'All data and documents':[...Object.keys(o.change.data).map(x=>F.find(f=>f[0]===x)[1]),...Object.keys(o.change.files).map(x=>(k==='drv'?DRVFILES:VEHFILES).find(f=>f[0]===x)[1]+' file')].join(', ');
  return `<tr class="click" onclick="verifyDlg('${k}','${o.id}')"><td>${isNew?(o.verifNote!==undefined&&o.history?.length?'<span class="tag info">Resubmission</span>':'<span class="tag info">New registration</span>'):'<span class="tag warn">Document update</span>'}</td>
  <td><b class="${k==='veh'?'mono':''}">${esc(k==='drv'?o.name:o.plate)}</b><div class="small muted">${o.id}</div></td><td>${DB.vendors[o.vendor].name}</td><td class="num">${fdt(isNew?o.submitted:o.change.at)}</td><td class="small">${esc(what)}</td><td class="r"><button class="btn sm em" onclick="event.stopPropagation();verifyDlg('${k}','${o.id}')">Review</button></td></tr>`}).join('')}
 </tbody></table></div>`:'<div class="empty">Nothing waiting for verification.</div>'}</div></div>`;
}
function verifyDlg(kind,id){S.dialog={type:'verify',kind,id,prev:null,ok:false,note:''};dlg()}
function docPreview(kind,o,fk){
 const lbl=(kind==='drv'?DRVFILES:VEHFILES).find(f=>f[0]===fk)[1];
 const rows={ktp:[['Name',o.name],['NIK',o.nik]],sim:[['Name',o.name],['SIM number',o.simNo],['Type',o.simType],['Valid until',fd(o.simValid)]],
  stnk:[['Plate',o.plate],['STNK number',o.stnkNo],['Brand / model',o.brand],['Valid until',fd(o.stnkValid)]],tax:[['Plate',o.plate],['Tax paid until',fd(o.taxValid)]],
  kir:[['Plate',o.plate],['KIR number',o.kirNo],['Valid until',fd(o.kirValid)]],photo:[]}[fk];
 return `<div class="docprev"><div class="dp-h">${ic('doc',16)} ${esc(o.files[fk]||'No file')}</div>
  <div class="dp-page"><div class="dp-t">${lbl}</div>${rows.length?`<dl class="kv">${rows.map(r=>`<dt>${r[0]}</dt><dd>${esc(r[1])}</dd>`).join('')}</dl>`:`<div class="dp-img">${ic(kind==='drv'?'person':'truck',48)}</div>`}</div>
  <div class="small muted">Prototype placeholder. The real system shows the uploaded file here, so the verifier can compare it with the entered data.</div></div>`;
}
function doVerify(act){
 const D=S.dialog;const o=(D.kind==='drv'?drv:veh)(D.id);const note=($('#vnote')?.value||'').trim();
 if(act==='reject'&&!note)return dErr('dErr',['Enter the reason so the vendor knows what to fix.']);
 if(act==='verify'&&!D.ok)return dErr('dErr',['Confirm that you checked the documents against the data.']);
 const who=role().user;const name=D.kind==='drv'?o.name:o.plate;
 if(o.change){
  if(act==='verify'){const F=D.kind==='drv'?DRVF:VEHF;Object.entries(o.change.data).forEach(([k,v])=>{const f=F.find(x=>x[0]===k);if(f[2])(o.history=o.history||[]).unshift({at:nowStamp(),doc:f[1],from:o[k],to:v})});Object.assign(o,o.change.data);Object.assign(o.files,o.change.files);o.changeRej=null}
  else o.changeRej={at:nowStamp(),note};
  o.change=null;
 }else{o.verif=act==='verify'?'Verified':'Rejected';o.verifNote=act==='verify'?'':note}
 o.verifBy=who;o.verifAt=nowStamp();
 save();closeDlg();render();toast(act==='verify'?`${name} verified`:`${name} rejected and returned to vendor`);
}

/* ============ Admin: approval flow ============ */
function vFlow(){
 const steps=DB.flow.slice().sort((a,b)=>a.seq-b.seq||a.id-b.id);
 return ph('Approval Flow','Goods Entry Permit · approval steps by position code',null,`<button class="btn" onclick="addStep()">${ic('add',16)} Add Step</button><button class="btn em" onclick="save();toast('Approval flow saved. It applies to requests submitted from now on.')">Save</button>`)+
 `<div class="pbody">
 <div class="msg info">${ic('info')}<div>Steps with the <b>same sequence number run in parallel</b> and all of them must approve before the next sequence starts. Different numbers run in order. Requests already in approval keep the flow they were submitted with.</div></div>
 <div class="card"><div class="card-h"><h3>Steps</h3><span class="muted small">${steps.length} configured</span></div><div class="tw"><table class="t"><thead><tr><th style="width:110px">Sequence</th><th>Position code</th><th>Position</th><th>Current holder</th><th>Department</th><th>Active</th><th></th></tr></thead><tbody>
 ${steps.map(s=>{const P=pos(s.pos);return `<tr class="${s.active?'':'dis'}"><td><input class="inp sm num" type="number" min="1" max="9" style="width:70px" value="${s.seq}" aria-label="Sequence" onchange="setStep(${s.id},'seq',+this.value)"></td>
 <td><select class="inp sm mono" style="width:150px" aria-label="Position code" onchange="setStep(${s.id},'pos',this.value)">${DB.positions.map(p=>`<option value="${p.code}" ${p.code===s.pos?'selected':''}>${p.code}</option>`).join('')}</select></td>
 <td>${P.name}</td><td>${P.holder}</td><td>${P.dept}</td><td><label class="switch"><input type="checkbox" ${s.active?'checked':''} aria-label="Active" onchange="setStep(${s.id},'active',this.checked)"><span></span></label></td>
 <td class="r"><button class="iconbtn" title="Delete step" onclick="DB.flow=DB.flow.filter(x=>x.id!==${s.id});render()">${ic('trash',16)}</button></td></tr>`}).join('')}
 </tbody></table></div></div>
 <div class="card"><div class="card-h"><h3>Position master</h3><span class="muted small">From HR organisation structure</span></div><div class="tw"><table class="t"><thead><tr><th>Position code</th><th>Position</th><th>Holder</th><th>Department</th></tr></thead><tbody>${DB.positions.map(p=>`<tr><td class="mono">${p.code}</td><td>${p.name}</td><td>${p.holder}</td><td>${p.dept}</td></tr>`).join('')}</tbody></table></div></div>
 </div>`;
}
function setStep(id,k,v){const s=DB.flow.find(x=>x.id===id);s[k]=v;render()}
function addStep(){const mx=Math.max(0,...DB.flow.map(f=>f.seq));DB.flow.push({id:Date.now(),seq:mx+1,pos:'WHS-MGR-01',active:true});render()}

/* ============ Admin: checklist ============ */
const OPTS=['Present & Fit','Present, Not Fit','Not Present'];
function vChecklist(){
 const L=DB.checklist;
 return ph('Inspection Checklist','Parameters Security checks at every first check-in of the day',null,`<button class="btn em" onclick="clDlg()">${ic('add',16)} Add Parameter</button>`)+
 `<div class="pbody">
 <div class="card"><div class="card-h"><h3>Answer options</h3><span class="muted small">Same for every parameter</span></div><div class="card-b"><div class="pill-opts"><span class="tag ok">Present &amp; Fit</span><span class="tag warn">Present, Not Fit</span><span class="tag err">Not Present</span></div>
 <p class="small muted" style="margin:10px 0 0">Mandatory parameters must be answered <b>Present &amp; Fit</b>. If not, the app warns the officer, but the officer still makes the final Fit / Not Fit decision and must enter remarks when rejecting.</p></div></div>
 <div class="card"><div class="tw"><table class="t"><thead><tr><th>#</th><th>Parameter</th><th>Category</th><th>Mandatory</th><th>Photo evidence</th><th>Active</th><th></th></tr></thead><tbody>
 ${L.map((c,i)=>`<tr class="${c.active?'':'dis'}"><td class="num muted">${i+1}</td><td><b>${esc(c.name)}</b></td><td><span class="tag plain">${c.cat}</span></td>
 <td><label class="switch"><input type="checkbox" ${c.mandatory?'checked':''} aria-label="Mandatory" onchange="setCl(${c.id},'mandatory',this.checked)"><span></span></label></td>
 <td><label class="switch"><input type="checkbox" ${c.photo?'checked':''} aria-label="Photo required" onchange="setCl(${c.id},'photo',this.checked)"><span></span></label></td>
 <td><label class="switch"><input type="checkbox" ${c.active?'checked':''} aria-label="Active" onchange="setCl(${c.id},'active',this.checked)"><span></span></label></td>
 <td class="r"><button class="iconbtn" title="Edit" onclick="clDlg(${c.id})">${ic('edit',16)}</button></td></tr>`).join('')}
 </tbody></table></div></div></div>`;
}
function setCl(id,k,v){DB.checklist.find(c=>c.id===id)[k]=v;save();render()}
function clDlg(id){const c=id?DB.checklist.find(x=>x.id===id):null;S.dialog={type:'cl',id,o:c?{...c}:{name:'',cat:'Vehicle',mandatory:true,photo:false,active:true}};dlg()}

/* ============ Admin: security users ============ */
function vSecUsers(){
 return ph('Security Users','Only these users can sign in to the Security mobile app',null,`<button class="btn em" onclick="suDlg()">${ic('add',16)} Add User</button>`)+
 `<div class="pbody"><div class="card"><div class="tw"><table class="t"><thead><tr><th>Name</th><th>Employee ID</th><th>Username</th><th>Default gate</th><th>Shift</th><th>Last sign-in</th><th>Active</th><th></th></tr></thead><tbody>
 ${DB.secUsers.map(u=>`<tr class="${u.active?'':'dis'}"><td><b>${esc(u.name)}</b></td><td class="mono">${u.emp}</td><td class="mono">${u.user}</td><td>${u.gate}</td><td>${u.shift}</td><td class="num">${fdt(u.last)}</td>
 <td><label class="switch"><input type="checkbox" ${u.active?'checked':''} aria-label="Active" onchange="DB.secUsers.find(x=>x.id==='${u.id}').active=this.checked;save();render()"><span></span></label></td>
 <td class="r"><button class="iconbtn" title="Edit" onclick="suDlg('${u.id}')">${ic('edit',16)}</button></td></tr>`).join('')}
 </tbody></table></div></div></div>`;
}
function suDlg(id){const u=id?DB.secUsers.find(x=>x.id===id):null;S.dialog={type:'su',id,o:u?{...u}:{name:'',emp:'',user:'',gate:'Gate 1 · Main Gate',shift:'Morning',active:true}};dlg()}

/* ============ Admin: gate log ============ */
function vGate(){
 const f=S.gateFilter;
 let L=DB.gate.filter(g=>(!f.date||g.time.startsWith(f.date))&&(!f.gate||g.gate===f.gate)&&(!f.type||g.type===f.type));
 if(f.q){
   const q=f.q.toLowerCase();
   L=L.filter(g=>(g.req + (drv(g.drv)?.name||'') + (veh(g.veh)?.plate||'') + (g.officer||'')).toLowerCase().includes(q));
 }
 const inside=insideNow();
 return ph('Gate Log','Check-in and check-out events from the Security mobile app')+
 `<div class="pbody">
 <div class="card"><div class="card-h"><h3>On site now</h3><span class="tag info plain">${inside.length}</span></div>${inside.length?`<div class="tw"><table class="t"><thead><tr><th>Since</th><th>Request</th><th>Vendor</th><th>Driver</th><th>Vehicle</th><th>Gate</th></tr></thead><tbody>${inside.map(g=>`<tr class="click" onclick="openReq('${g.req}','gatelog')"><td class="num">${g.time.slice(11)}</td><td class="mono">${g.req}</td><td>${DB.vendors[reqById(g.req).vendor].name}</td><td>${esc(drv(g.drv).name)}</td><td class="mono">${veh(g.veh).plate}</td><td>${g.gate}</td></tr>`).join('')}</tbody></table></div>`:'<div class="empty">No vendor vehicle inside.</div>'}</div>
 <div class="card"><div class="filterbar">
  <div class="field" style="width:240px"><label for="gq">Search</label><input id="gq" class="inp" placeholder="Cari request, driver, plat..." value="${esc(f.q||'')}" oninput="S.gateFilter.q=this.value;rerenderKeep('gq')"></div>
  <div class="field"><label for="gd">Date</label><input id="gd" type="date" class="inp" value="${f.date}" onchange="S.gateFilter.date=this.value;render()"></div>
  <div class="field"><label for="gg">Gate</label><select id="gg" class="inp" onchange="S.gateFilter.gate=this.value;render()"><option value="">All gates</option>${['Gate 1 · Main Gate','Gate 2 · Warehouse Gate'].map(g=>`<option ${f.gate===g?'selected':''}>${g}</option>`).join('')}</select></div>
  <div class="field"><label for="gt">Event</label><select id="gt" class="inp" onchange="S.gateFilter.type=this.value;render()"><option value="">All events</option>${['Check-in','Check-out','Check-in rejected'].map(g=>`<option ${f.type===g?'selected':''}>${g}</option>`).join('')}</select></div>
  ${f.date||f.gate||f.type||f.q?`<button class="btn ghost" onclick="S.gateFilter={date:T0,gate:'',type:'',q:''};render()">Clear filters</button>`:''}
 </div>${gateTable(L)}</div></div>`;
}

/* ============ Dialogs ============ */
function dlg(){
 const D=S.dialog;const host=$('#dlg');
 const keepY=host.querySelector('.dlg-b')?.scrollTop||0, keepT=host.querySelector('.dlg-b .tw')?.scrollTop||0, same=host.dataset.t===(D&&D.type);
 if(!D){host.innerHTML='';host.dataset.t='';return}
 let h='',title=D.title||'',foot='',wide=false;
 if(D.type==='confirm'){h=`<p style="margin:0">${esc(D.text)}</p>`;foot=`<button class="btn ghost" onclick="closeDlg()">Cancel</button><button class="btn em neg" onclick="const f=S.dialog.fn;closeDlg();f()">${D.ok}</button>`}
 if(D.type==='decide'){
  const R=reqById(S.detail);const need=D.act!=='approve';
  h=`<dl class="kv"><dt>Request</dt><dd class="mono">${R.id}</dd><dt>Vendor</dt><dd>${DB.vendors[R.vendor].name}</dd><dt>Your step</dt><dd>${pos(myPos()).code} · ${pos(myPos()).name}</dd></dl>
  <div class="field"><label for="dc">Comment${need?' <span class="req">*</span>':' (optional)'}</label><textarea id="dc" class="inp" placeholder="${need?'Explain what the vendor needs to fix':'Optional note'}"></textarea></div>
  <div id="dcErr"></div>`;
  foot=`<button class="btn ghost" onclick="closeDlg()">Cancel</button><button class="btn em ${D.cls}" onclick="doDecide()">${D.ok}</button>`;
 }
 if(D.type==='drv'){
  const o=D.o,ed=!!D.id;title=ed?`Driver ${o.name}`:'Register Driver';wide=true;
  h=`${masterBanner(ed&&drv(D.id))}${ed?`<div class="msg info">${ic('info')}<div>NIK is locked. Changes and renewed documents are sent to the Security Administrator for verification.</div></div>`:''}
  <div class="grid g2">
   <div class="field"><label for="d_name">Full name (as on KTP) <span class="req">*</span></label><input id="d_name" class="inp" value="${esc(o.name)}" oninput="S.dialog.o.name=this.value"></div>
   <div class="field"><label for="d_nik">NIK <span class="req">*</span></label><input id="d_nik" class="inp mono" maxlength="16" inputmode="numeric" value="${esc(o.nik)}" ${ed?'readonly':''} oninput="S.dialog.o.nik=this.value"></div>
   <div class="field"><label for="d_phone">Mobile number <span class="req">*</span></label><input id="d_phone" class="inp" value="${esc(o.phone)}" oninput="S.dialog.o.phone=this.value"></div>
   <div class="field"><label for="d_simtype">SIM type <span class="req">*</span></label><select id="d_simtype" class="inp" onchange="S.dialog.o.simType=this.value">${['A','A Umum','B1','B1 Umum','B2','B2 Umum'].map(t=>`<option ${t===o.simType?'selected':''}>${t}</option>`).join('')}</select></div>
   <div class="field"><label for="d_simno">SIM number <span class="req">*</span></label><input id="d_simno" class="inp mono" value="${esc(o.simNo)}" oninput="S.dialog.o.simNo=this.value"></div>
   <div class="field"><label for="d_simvalid">SIM valid until <span class="req">*</span></label><input id="d_simvalid" type="date" class="inp" value="${o.simValid}" onchange="S.dialog.o.simValid=this.value"></div>
  </div>
  <div class="grid g3">${upl('f_ktp','KTP',D.files.ktp,true)}${upl('f_sim','SIM',D.files.sim,true)}${upl('f_photo','Driver photo',D.files.photo)}</div>
  ${ed&&o.history?.length?`<div><div class="small muted" style="margin-bottom:4px">Renewal history</div>${o.history.map(x=>`<div class="small">${fdt(x.at)} · ${x.doc}: ${fd(x.from)} → ${fd(x.to)}</div>`).join('')}</div>`:''}
  <div id="dErr"></div>`;
  foot=`<button class="btn ghost" onclick="closeDlg()">Cancel</button><button class="btn em" onclick="saveDrv()">${ed?'Submit for Verification':'Register'}</button>`;
 }
 if(D.type==='veh'){
  const o=D.o,ed=!!D.id;title=ed?`Vehicle ${o.plate}`:'Register Vehicle';wide=true;
  h=`${masterBanner(ed&&veh(D.id))}${ed?`<div class="msg info">${ic('info')}<div>The plate number is locked. Changes and renewed documents are sent to the Security Administrator for verification.</div></div>`:''}
  <div class="grid g3">
   <div class="field"><label for="v_plate">Plate number <span class="req">*</span></label><input id="v_plate" class="inp mono" placeholder="B 1234 ABC" value="${esc(o.plate)}" ${ed?'readonly':''} oninput="S.dialog.o.plate=this.value.toUpperCase()"></div>
   <div class="field"><label for="v_type">Vehicle type <span class="req">*</span></label><select id="v_type" class="inp" onchange="S.dialog.o.type=this.value">${['Pick-up','Blind Van','CDE (Colt Diesel Engkel)','CDD (Colt Diesel Double)','Fuso','Wingbox','Tronton','Trailer'].map(t=>`<option ${t===o.type?'selected':''}>${t}</option>`).join('')}</select></div>
   <div class="field"><label for="v_brand">Brand / model <span class="req">*</span></label><input id="v_brand" class="inp" placeholder="Isuzu Elf NMR 71" value="${esc(o.brand)}" oninput="S.dialog.o.brand=this.value"></div>
   <div class="field"><label for="v_year">Year</label><input id="v_year" type="number" class="inp" value="${o.year}" oninput="S.dialog.o.year=+this.value"></div>
   <div class="field"><label for="v_stnkno">STNK number <span class="req">*</span></label><input id="v_stnkno" class="inp mono" value="${esc(o.stnkNo)}" oninput="S.dialog.o.stnkNo=this.value"></div>
   <div class="field"><label for="v_kirno">KIR number <span class="req">*</span></label><input id="v_kirno" class="inp mono" value="${esc(o.kirNo)}" oninput="S.dialog.o.kirNo=this.value"></div>
   <div class="field"><label for="v_tax">STNK annual tax valid until <span class="req">*</span></label><input id="v_tax" type="date" class="inp" value="${o.taxValid}" onchange="S.dialog.o.taxValid=this.value"></div>
   <div class="field"><label for="v_stnk">STNK 5-year valid until <span class="req">*</span></label><input id="v_stnk" type="date" class="inp" value="${o.stnkValid}" onchange="S.dialog.o.stnkValid=this.value"></div>
   <div class="field"><label for="v_kir">KIR valid until <span class="req">*</span></label><input id="v_kir" type="date" class="inp" value="${o.kirValid}" onchange="S.dialog.o.kirValid=this.value"></div>
  </div>
  <div class="grid g2">${upl('f_stnk','STNK',D.files.stnk,true)}${upl('f_tax','Annual tax receipt (SKPD)',D.files.tax,true)}${upl('f_kir','KIR certificate / card',D.files.kir,true)}${upl('f_photo','Vehicle photo (front, plate visible)',D.files.photo)}</div>
  ${ed&&o.history?.length?`<div><div class="small muted" style="margin-bottom:4px">Renewal history</div>${o.history.map(x=>`<div class="small">${fdt(x.at)} · ${x.doc}: ${fd(x.from)} → ${fd(x.to)}</div>`).join('')}</div>`:''}
  <div id="dErr"></div>`;
  foot=`<button class="btn ghost" onclick="closeDlg()">Cancel</button><button class="btn em" onclick="saveVeh()">${ed?'Submit for Verification':'Register'}</button>`;
 }
 if(D.type==='renew'){
  const o=D.kind==='drv'?drv(D.id):veh(D.id);const k=(D.kind==='drv'?DRV_DOCS:VEH_DOCS).find(x=>x.k===D.field);
  title=`Renew ${k.label}`;
  h=`<dl class="kv"><dt>${D.kind==='drv'?'Driver':'Vehicle'}</dt><dd>${esc(D.kind==='drv'?o.name:o.plate)}</dd><dt>Current validity</dt><dd>${dateCell(o[D.field])}</dd></dl>
  <div class="field"><label for="rn_d">New valid until <span class="req">*</span></label><input id="rn_d" type="date" class="inp" min="${T0}" value="${D.date}" onchange="S.dialog.date=this.value"></div>
  ${upl('f_renew','New document',D.files.renew,true)}
  <div class="msg info">${ic('info')}<div>The renewal is checked by the Security Administrator. Once verified, every request using this ${D.kind==='drv'?'driver':'vehicle'} uses the new date without new request approval.</div></div><div id="dErr"></div>`;
  foot=`<button class="btn ghost" onclick="closeDlg()">Cancel</button><button class="btn em" onclick="doRenew()">Submit Renewal</button>`;
 }
 if(D.type==='cl'){
  const o=D.o;title=D.id?'Edit Parameter':'Add Parameter';
  h=`<div class="field"><label for="c_name">Parameter <span class="req">*</span></label><input id="c_name" class="inp" value="${esc(o.name)}" placeholder="e.g. Seat belt" oninput="S.dialog.o.name=this.value"></div>
  <div class="field"><label for="c_cat">Category</label><select id="c_cat" class="inp" onchange="S.dialog.o.cat=this.value">${['Vehicle','Safety','Cargo','Driver','Document'].map(t=>`<option ${t===o.cat?'selected':''}>${t}</option>`).join('')}</select></div>
  <label class="chk"><input type="checkbox" ${o.mandatory?'checked':''} onchange="S.dialog.o.mandatory=this.checked"> Mandatory, must be Present &amp; Fit</label>
  <label class="chk"><input type="checkbox" ${o.photo?'checked':''} onchange="S.dialog.o.photo=this.checked"> Photo evidence required</label>
  <label class="chk"><input type="checkbox" ${o.active?'checked':''} onchange="S.dialog.o.active=this.checked"> Active</label><div id="dErr"></div>`;
  foot=`${D.id?`<button class="btn ghost neg" style="margin-right:auto" onclick="DB.checklist=DB.checklist.filter(c=>c.id!==${D.id});save();closeDlg();render()">Delete</button>`:''}<button class="btn ghost" onclick="closeDlg()">Cancel</button><button class="btn em" onclick="saveCl()">Save</button>`;
 }
 if(D.type==='su'){
  const o=D.o;title=D.id?`Edit ${o.name}`:'Add Security User';
  h=`<div class="grid g2">
  <div class="field"><label for="u_name">Full name <span class="req">*</span></label><input id="u_name" class="inp" value="${esc(o.name)}" oninput="S.dialog.o.name=this.value"></div>
  <div class="field"><label for="u_emp">Employee ID <span class="req">*</span></label><input id="u_emp" class="inp mono" value="${esc(o.emp)}" oninput="S.dialog.o.emp=this.value"></div>
  <div class="field"><label for="u_user">Username <span class="req">*</span></label><input id="u_user" class="inp mono" value="${esc(o.user)}" oninput="S.dialog.o.user=this.value"></div>
  <div class="field"><label for="u_gate">Default gate</label><select id="u_gate" class="inp" onchange="S.dialog.o.gate=this.value">${['Gate 1 · Main Gate','Gate 2 · Warehouse Gate'].map(g=>`<option ${g===o.gate?'selected':''}>${g}</option>`).join('')}</select></div>
  <div class="field"><label for="u_shift">Shift</label><select id="u_shift" class="inp" onchange="S.dialog.o.shift=this.value">${['Morning','Afternoon','Night'].map(g=>`<option ${g===o.shift?'selected':''}>${g}</option>`).join('')}</select></div>
  </div><div class="msg info">${ic('info')}<div>The user receives a one-time activation code by SMS and sets a PIN on first sign-in to the mobile app.</div></div><div id="dErr"></div>`;
  foot=`<button class="btn ghost" onclick="closeDlg()">Cancel</button><button class="btn em" onclick="saveSu()">Save</button>`;
 }
 const chkS='style="width:16px;height:16px;accent-color:var(--brand)"';
 if(D.type==='pohelp'){
  title='Select Purchase Order';wide=true;
  const q=D.q.toLowerCase();
  const L=Object.entries(DB.pos).filter(([n,P])=>P.vendor==='V1000'&&(!q||(n+P.desc+P.plant).toLowerCase().includes(q)));
  h=`<div class="searchbar">${ic('search',16)}<input id="vhq" class="inp" placeholder="Search PO number, description or plant" aria-label="Search" value="${esc(D.q)}" oninput="S.dialog.q=this.value;dlg();focusQ()"></div>
  <div class="small muted">Open POs of PT Sinar Logistik Abadi · ${L.length} found</div>
  <div class="tw"><table class="t"><thead><tr><th>PO number</th><th>Description</th><th>Receiving plant</th><th>Created</th><th class="r">DOs</th></tr></thead><tbody>
  ${L.map(([n,P])=>{const added=S.form.docs.some(x=>x.po===n);return `<tr class="${added?'dis':'click'}" ${added?'':`onclick="pickPO('${n}')"`}><td class="mono"><b>${n}</b>${added?' <span class="tag plain">Added</span>':''}</td><td>${esc(P.desc)}</td><td>${P.plant}</td><td class="num">${fd(P.created)}</td><td class="r num">${Object.keys(P.dos).length}</td></tr>`}).join('')||'<tr><td colspan="5" class="empty">No PO matches your search.</td></tr>'}</tbody></table></div>`;
  foot=`<button class="btn ghost" onclick="closeDlg()">Cancel</button>`;
 }
 if(D.type==='dos'){
  const P=DB.pos[D.po];const ro=!!D.rid;wide=true;title=`PO ${D.po} · Delivery Orders`;
  const all=Object.entries(P.dos);const q=D.q.toLowerCase();
  const L=all.filter(([n,x])=>(ro?D.sel.includes(n):true)&&(!q||(n+fd(x.date)).toLowerCase().includes(q)));
  const allOn=all.length&&all.every(([n])=>D.sel.includes(n));
  h=`<dl class="kv"><dt>Description</dt><dd>${esc(P.desc)}</dd><dt>Receiving plant</dt><dd>${P.plant}</dd></dl>
  ${all.length?`${all.length>4?`<div class="searchbar">${ic('search',16)}<input id="vhq" class="inp" placeholder="Search DO number or date" aria-label="Search" value="${esc(D.q)}" oninput="S.dialog.q=this.value;dlg();focusQ()"></div>`:''}
  <div class="tw"><table class="t"><thead><tr>${ro?'':`<th style="width:40px"><input type="checkbox" aria-label="Select all" ${allOn?'checked':''} onchange="S.dialog.sel=this.checked?Object.keys(DB.pos[S.dialog.po].dos):[];dlg()" ${chkS}></th>`}<th>DO number</th><th>Delivery date</th><th>Items</th></tr></thead><tbody>
  ${L.map(([n,x])=>{const on=D.sel.includes(n);return `<tr class="${on&&!ro?'sel':''}">${ro?'':`<td><input type="checkbox" aria-label="Select DO ${n}" ${on?'checked':''} onchange="const s=S.dialog.sel;S.dialog.sel=s.includes('${n}')?s.filter(v=>v!=='${n}'):[...s,'${n}'];dlg()" ${chkS}></td>`}<td class="mono"><b>${n}</b></td><td class="num">${fd(x.date)}</td><td class="small">${x.items.map(it=>{const pl=P.items.find(p=>p.line===it.line);return `${esc(pl.desc)}: <b>${it.qty} ${pl.uom}</b>`}).join('<br>')}</td></tr>`}).join('')||'<tr><td colspan="4" class="empty">No DO matches your search.</td></tr>'}</tbody></table></div>`
  :`<div class="msg info">${ic('info')}<div>No delivery order has been issued for this PO yet. Open PO quantities are used.</div></div>`}`;
  foot=ro?`<button class="btn em" onclick="closeDlg()">Close</button>`:`<span class="small muted" style="margin-right:auto;align-self:center">${D.sel.length} of ${all.length} selected</span><button class="btn ghost" onclick="closeDlg()">Cancel</button><button class="btn em" onclick="applyDOs()">Apply</button>`;
 }
 if(D.type==='mhelp'){
  const isD=D.kind==='drv';wide=true;title=isD?'Add Driver':'Add Vehicle';
  const F=S.form;const by=F.end||F.start;const q=D.q.toLowerCase();
  const src=(isD?DB.drivers:DB.vehicles).filter(o=>o.vendor==='V1000');
  const ver=src.filter(o=>o.verif==='Verified');const notVer=src.length-ver.length;
  const L=ver.filter(o=>!q||(isD?o.name+o.nik+o.simNo:o.plate+o.type+o.brand).toLowerCase().includes(q));
  const inList=isD?F.drivers:F.vehicles;
  h=`<div class="searchbar">${ic('search',16)}<input id="vhq" class="inp" placeholder="${isD?'Search name, NIK or SIM number':'Search plate, type or model'}" aria-label="Search" value="${esc(D.q)}" oninput="S.dialog.q=this.value;dlg();focusQ()"></div>
  <div class="small muted">${L.length} verified ${isD?'driver':'vehicle'}${L.length===1?'':'s'}${by?` · documents checked against ${fd(by)}`:''}${notVer?` · ${notVer} not shown because verification is pending or rejected`:''}</div>
  <div class="tw"><table class="t nw"><thead><tr><th style="width:40px"></th>${isD?'<th>Name</th><th>NIK</th><th>SIM</th><th>SIM valid until</th>':'<th>Plate</th><th>Type / Model</th><th>STNK tax</th><th>STNK 5-yr</th><th>KIR</th>'}<th>Check</th></tr></thead><tbody>
  ${L.map(o=>{const added=inList.includes(o.id);const iss=by?docIssues(o,D.kind,by):[];const dis=added||iss.length>0;const on=D.sel.includes(o.id);
   const cells=isD?`<td><b>${esc(o.name)}</b></td><td class="mono">${o.nik}</td><td><span class="mono">${o.simNo}</span> <span class="small muted">${o.simType}</span></td><td>${dateCell(o.simValid)}</td>`:`<td><b class="mono">${o.plate}</b></td><td>${esc(o.type)}<div class="small muted">${esc(o.brand)}</div></td><td>${dateCell(o.taxValid)}</td><td>${dateCell(o.stnkValid)}</td><td>${dateCell(o.kirValid)}</td>`;
   return `<tr class="${dis?'dis':'click'} ${on?'sel':''}" ${dis?'':`onclick="const s=S.dialog.sel;S.dialog.sel=s.includes('${o.id}')?s.filter(v=>v!=='${o.id}'):[...s,'${o.id}'];dlg()"`}><td><input type="checkbox" tabindex="-1" aria-label="Select" ${on||added?'checked':''} ${dis?'disabled':''} ${chkS.slice(0,-1)};pointer-events:none"></td>${cells}<td>${added?'<span class="tag plain">Already added</span>':iss.length?`<span class="tag err">${iss.map(i=>i.label).join(', ')} expires before ${fd(by)}</span>`:'<span class="tag ok">OK</span>'}</td></tr>`}).join('')||`<tr><td colspan="${isD?6:7}" class="empty">No verified ${isD?'driver':'vehicle'} matches your search.</td></tr>`}</tbody></table></div>`;
  foot=`<span class="small muted" style="margin-right:auto;align-self:center">${D.sel.length} selected</span><button class="btn ghost" onclick="closeDlg()">Cancel</button><button class="btn em" ${D.sel.length?'':'disabled'} onclick="addPicked()">Add${D.sel.length?` (${D.sel.length})`:''}</button>`;
 }
 if(D.type==='verify'){
  const isD=D.kind==='drv';const o=(isD?drv:veh)(D.id);const upd=!!o.change;wide=true;
  const nv=withChange(o);const F=isD?DRVF:VEHF;const FL=isD?DRVFILES:VEHFILES;
  title=`${upd?'Verify Update':'Verify Registration'} · ${esc(isD?o.name:o.plate)}`;
  const changed=k=>upd&&k in o.change.data, fchanged=k=>upd&&k in o.change.files;
  h=`<dl class="kv"><dt>Vendor</dt><dd>${DB.vendors[o.vendor].name}</dd><dt>Submitted</dt><dd>${fdt(upd?o.change.at:o.submitted)}</dd></dl>
  <div class="vgrid"><div style="display:flex;flex-direction:column;gap:12px;min-width:0">
   <div class="tw" style="max-height:none"><table class="t"><thead><tr><th>Field</th>${upd?'<th>Current (verified)</th>':''}<th>${upd?'Submitted':'Value'}</th></tr></thead><tbody>
   ${F.map(([k,l,dt])=>`<tr><td class="muted">${l}</td>${upd?`<td>${dt?fd(o[k]):esc(o[k])}</td>`:''}<td class="${changed(k)?'diff':''}">${dt?fd(nv[k]):esc(nv[k])}</td></tr>`).join('')}</tbody></table></div>
   <div><div class="small muted" style="margin-bottom:6px">Documents</div><div class="doclist">${FL.map(([k,l])=>`<div class="docrow ${D.prev===k?'on':''}">${ic('doc',16)}<div class="fn"><b>${l}</b><div class="small muted">${esc(nv.files[k]||'Not uploaded')}</div></div>${fchanged(k)?'<span class="tag warn plain">New</span>':''}${nv.files[k]?`<button class="btn sm" onclick="S.dialog.prev='${k}';dlg()">View</button>`:''}</div>`).join('')}</div></div>
  </div><div style="min-width:0">${D.prev?docPreview(D.kind,nv,D.prev):`<div class="dp-page" style="display:grid;place-items:center;color:var(--muted);text-align:center;min-height:220px">Select View on a document to compare it with the data.</div>`}</div></div>
  <label class="chk"><input type="checkbox" ${D.ok?'checked':''} onchange="S.dialog.ok=this.checked"> I have checked every document against the entered data</label>
  <div class="field"><label for="vnote">Note to vendor (required when rejecting)</label><textarea id="vnote" class="inp" oninput="S.dialog.note=this.value">${esc(D.note)}</textarea></div><div id="dErr"></div>`;
  foot=`<button class="btn ghost" onclick="closeDlg()">Cancel</button><button class="btn neg" onclick="doVerify('reject')">Reject</button><button class="btn pos em" onclick="doVerify('verify')">${ic('check',16)} Verify</button>`;
 }
 const bx=S.mod==='badge'&&bDlg(D);if(bx){title=bx.title;h=bx.h;foot=bx.foot;wide=!!bx.wide}
 host.innerHTML=`<div class="ov" onclick="if(event.target===this)closeDlg()"><div class="dlg ${wide?'wide':''}" role="dialog" aria-modal="true" aria-label="${esc(title.replace(/<[^>]+>/g,''))}"><div class="dlg-h"><h3>${title}</h3><button class="iconbtn" style="margin-left:auto" onclick="closeDlg()" aria-label="Close">${ic('x',18)}</button></div><div class="dlg-b">${h}</div><div class="dlg-f">${foot}</div></div></div>`;
 host.dataset.t=D.type;
 if(same){const b=host.querySelector('.dlg-b');if(b)b.scrollTop=keepY;const t=host.querySelector('.dlg-b .tw');if(t)t.scrollTop=keepT}
 if (window.lucide && typeof window.lucide.createIcons === 'function') {
   window.lucide.createIcons();
 }
}
function closeDlg(){S.dialog=null;dlg()}
function dErr(id,list){document.getElementById(id).innerHTML=list.length?`<div class="msg err">${ic('err')}<div><ul style="margin:0">${list.map(e=>`<li>${esc(e)}</li>`).join('')}</ul></div></div>`:''}
function doDecide(){
 const D=S.dialog;const c=$('#dc').value.trim();
 if(D.act!=='approve'&&!c){dErr('dcErr',['Enter a comment so the vendor knows what to fix.']);return}
 const R=reqById(S.detail);decide(R,myPos(),D.act,c);save();closeDlg();
 toast(D.act==='approve'?(R.status==='Approved'?`${R.id} fully approved. QR code issued.`:`${R.id} approved. Moved to the next step.`):`${R.id} ${D.act==='reject'?'rejected':'returned to vendor'}`);
 S.tab='approval';render();
}
function saveDrv(){
 const D=S.dialog,o=D.o,E=[];
 if(!o.name.trim())E.push('Enter the full name.');
 if(!/^\d{16}$/.test(o.nik))E.push('NIK must be 16 digits.');
 if(!D.id&&DB.drivers.some(x=>x.nik===o.nik))E.push('A driver with this NIK is already registered.');
 if(!o.phone.trim())E.push('Enter the mobile number.');
 if(!o.simNo.trim())E.push('Enter the SIM number.');
 if(!o.simValid)E.push('Enter the SIM valid-until date.');
 if(!D.files.ktp||!D.files.sim)E.push('Upload KTP and SIM.');
 if(E.length)return dErr('dErr',E);
 submitMaster('drv',D);save();S.dialog=null;dlg();render();
}
function saveVeh(){
 const D=S.dialog,o=D.o,E=[];
 if(!/^[A-Z]{1,2} ?\d{1,4} ?[A-Z]{0,3}$/.test(o.plate.trim()))E.push('Enter a valid plate number, for example B 1234 ABC.');
 if(!D.id&&DB.vehicles.some(x=>x.plate.replace(/ /g,'')===o.plate.replace(/ /g,'')))E.push('This plate number is already registered.');
 if(!o.brand.trim())E.push('Enter brand and model.');
 if(!o.stnkNo.trim()||!o.kirNo.trim())E.push('Enter STNK and KIR numbers.');
 if(!o.taxValid||!o.stnkValid||!o.kirValid)E.push('Enter all three validity dates.');
 if(!D.files.stnk||!D.files.tax||!D.files.kir)E.push('Upload STNK, tax receipt and KIR.');
 if(E.length)return dErr('dErr',E);
 submitMaster('veh',D);save();S.dialog=null;dlg();render();
}
function doRenew(){
 const D=S.dialog;const E=[];
 if(!D.date||D.date<T0)E.push('Enter a new valid-until date from today onwards.');
 if(!D.files.renew)E.push('Upload the renewed document.');
 if(E.length)return dErr('dErr',E);
 const o=D.kind==='drv'?drv(D.id):veh(D.id);const k=(D.kind==='drv'?DRV_DOCS:VEH_DOCS).find(x=>x.k===D.field);
 const fk={simValid:'sim',taxValid:'tax',stnkValid:'stnk',kirValid:'kir'}[D.field];
 const ch=o.change||{data:{},files:{}};ch.at=nowStamp();ch.data[D.field]=D.date;ch.files[fk]=D.files.renew;o.change=ch;o.changeRej=null;
 save();closeDlg();render();toast(`${k.label} renewal submitted for verification`);
}
function saveCl(){const D=S.dialog;if(!D.o.name.trim())return dErr('dErr',['Enter the parameter name.']);if(D.id)Object.assign(DB.checklist.find(c=>c.id===D.id),D.o);else DB.checklist.push({...D.o,id:Date.now()});save();closeDlg();render();toast('Parameter saved')}
function saveSu(){const D=S.dialog,o=D.o;if(!o.name.trim()||!o.emp.trim()||!o.user.trim())return dErr('dErr',['Fill in name, employee ID and username.']);if(D.id)Object.assign(DB.secUsers.find(u=>u.id===D.id),o);else DB.secUsers.push({...o,id:'SEC-'+Date.now(),last:null});save();closeDlg();render();toast('Security user saved')}

function afterRender(){
 const q=document.getElementById('qrEl');
 if(q&&window.QRCode){q.innerHTML='';new QRCode(q,{text:S.detail,width:200,height:200,colorDark:'#000000',colorLight:'#ffffff',correctLevel:QRCode.CorrectLevel.M})}
 else if(q)q.innerHTML=`<div style="width:200px;height:200px;display:grid;place-items:center;color:#1d2d3e" class="mono">${S.detail}</div>`;

 if (window.lucide && typeof window.lucide.createIcons === 'function') {
   window.lucide.createIcons();
 }
}
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&S.dialog)closeDlg()});
render();