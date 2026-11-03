// Konstanta domain & definisi enum untuk Modul Perizinan Material SES Badak NGL
// Sesuai Master Blueprint Audio & Stage 01 - Stage 08.

enum SubModuleType {
  entry('ENTRY', 'Izin Masuk Barang (Material Entry Permit)'),
  exit('EXIT', 'Izin Keluar Barang (Material Exit Permit)'),
  gatePass('GATE_PASS', 'Gate Pass Aset Internal PT Badak NGL');

  final String code;
  final String label;
  const SubModuleType(this.code, this.label);

  static SubModuleType fromCode(String code) {
    return SubModuleType.values.firstWhere(
      (e) => e.code == code.toUpperCase(),
      orElse: () => SubModuleType.entry,
    );
  }
}

enum PermitBasis {
  po('PO', 'Purchase Order (Pengadaan Reguler)'),
  ca('CA', 'Contractor Agreement (Kontrak Proyek)'),
  facilityMemo('FACILITY_MEMO', 'Pengurus Fasilitas (Kantin/TojSera/Fuel Up)'),
  internalAsset('INTERNAL_ASSET', 'Aset Dinas Internal PT Badak NGL');

  final String code;
  final String label;
  const PermitBasis(this.code, this.label);

  static PermitBasis fromCode(String code) {
    return PermitBasis.values.firstWhere(
      (e) => e.code == code.toUpperCase(),
      orElse: () => PermitBasis.po,
    );
  }
}

enum DestinationZone {
  zone1('ZONE_1', 'Zone 1 (Area Proses / Train)', true, true, true),
  zone2('ZONE_2', 'Zone 2 (Kilang / Warehouse)', true, true, false),
  gedungPutih('GEDUNG_PUTIH', 'Gedung Putih (Area Administrasi)', true, false, false);

  final String code;
  final String label;
  final bool requiresClosedShoes;
  final bool requiresSafetyShoes;
  final bool requiresCoverall;

  const DestinationZone(
    this.code,
    this.label,
    this.requiresClosedShoes,
    this.requiresSafetyShoes,
    this.requiresCoverall,
  );

  static DestinationZone fromCode(String code) {
    return DestinationZone.values.firstWhere(
      (e) => e.code == code.toUpperCase(),
      orElse: () => DestinationZone.zone2,
    );
  }
}

enum PermitStatus {
  draft('DRAFT', 'Draft Permohonan'),
  submitted('SUBMITTED', 'Diajukan (Menunggu Review)'),
  inReviewSupv('IN_REVIEW_SUPV', 'Review Supervisor'),
  inReviewMgr('IN_REVIEW_MGR', 'Review Manager User'),
  inReviewProc('IN_REVIEW_PROC', 'Review Procurement'),
  inReviewSecAdm('IN_REVIEW_SEC_ADM', 'Verifikasi Admin Security'),
  waitingInspection('WAITING_INSPECTION', 'Menunggu Cek Fisik Posko'),
  approved('APPROVED', 'Izin Aktif / Disetujui'),
  rejected('REJECTED', 'Ditolak / Dibatalkan');

  final String code;
  final String label;
  const PermitStatus(this.code, this.label);

  static PermitStatus fromCode(String code) {
    return PermitStatus.values.firstWhere(
      (e) => e.code == code.toUpperCase(),
      orElse: () => PermitStatus.draft,
    );
  }
}

enum VisitCycleStatus {
  none('NONE', 'Belum Pernah Screening'),
  screened('SCREENED', 'Lulus Screening Posko'),
  checkedIn('CHECKED_IN', 'Di Dalam Kilang (Checked-In)'),
  checkedOut('CHECKED_OUT', 'Selesai Kunjungan (Checked-Out)'),
  rejected('REJECTED', 'Ditolak di Gerbang');

  final String code;
  final String label;
  const VisitCycleStatus(this.code, this.label);

  static VisitCycleStatus fromCode(String code) {
    return VisitCycleStatus.values.firstWhere(
      (e) => e.code == code.toUpperCase(),
      orElse: () => VisitCycleStatus.none,
    );
  }
}

enum ScreeningResult {
  approved('APPROVED', 'Lulus Pemeriksaan Fisik'),
  rejected('REJECTED', 'Tidak Layak (Cek Fisik Ditolak)'),
  bypassed('BYPASSED', 'Bypass (Stiker KKB Aktif)');

  final String code;
  final String label;
  const ScreeningResult(this.code, this.label);

  static ScreeningResult fromCode(String code) {
    return ScreeningResult.values.firstWhere(
      (e) => e.code == code.toUpperCase(),
      orElse: () => ScreeningResult.approved,
    );
  }
}

enum MandatoryDocType {
  memoKws('MEMO_KWS', 'Memo Permintaan Izin Masuk & KWS', true),
  softcopyPo('SOFTCOPY_PO', 'Softcopy Purchase Order (PO)', true),
  softcopyDo('SOFTCOPY_DO', 'Softcopy Delivery Order (DO)', true),
  stnk('STNK', 'STNK & Pajak Tahunan Aktif', true),
  sim('SIM', 'SIM Pengemudi / SIMPER', true),
  kir('KIR', 'Buku KIR Aktif (Khusus Angkutan Barang)', false),
  kkbSticker('KKB_STICKER', 'Bukti Stiker KKB/KKS Aktif', false);

  final String code;
  final String label;
  final bool isAlwaysMandatory;
  const MandatoryDocType(this.code, this.label, this.isAlwaysMandatory);
}
