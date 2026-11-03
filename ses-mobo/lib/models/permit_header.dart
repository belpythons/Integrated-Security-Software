// Model data header pengajuan perizinan material
// Sesuai Master Blueprint Bab 3.2 & Stage 04.

import '../core/app_constants.dart';
import 'po_do_item.dart';
import 'vehicle_item.dart';
import 'driver_item.dart';

class MaterialPermitHeader {
  final String requestNo;
  final SubModuleType subModuleType;
  final PermitBasis permitBasis;
  final String? parentEntryReqNo;
  final String? exitReason;
  final String refDocNo;
  final String inputChannel; // 'INTRANET' atau 'INTERNET'
  final String? companyId;
  final String vendorName;
  final String targetDeptId;
  final String? targetSectionId;
  final DestinationZone destinationZone;
  final String specificLocation;
  final DateTime estArrivalDate;
  final String? estArrivalTime;
  final int? memoFileId;
  final int? caDocFileId;
  final bool hasExpiryWarning;
  final DateTime? validFrom;
  final DateTime? validTo;
  final String? qrCodeToken;
  final PermitStatus permitStatus;

  // Koleksi relasi anak
  final List<PoDoItem> poDoItems;
  final List<VehicleItem> vehicleItems;
  final List<DriverItem> driverItems;

  MaterialPermitHeader({
    required this.requestNo,
    required this.subModuleType,
    required this.permitBasis,
    this.parentEntryReqNo,
    this.exitReason,
    required this.refDocNo,
    this.inputChannel = 'INTRANET',
    this.companyId,
    required this.vendorName,
    required this.targetDeptId,
    this.targetSectionId,
    required this.destinationZone,
    required this.specificLocation,
    required this.estArrivalDate,
    this.estArrivalTime,
    this.memoFileId,
    this.caDocFileId,
    this.hasExpiryWarning = false,
    this.validFrom,
    this.validTo,
    this.qrCodeToken,
    this.permitStatus = PermitStatus.draft,
    this.poDoItems = const [],
    this.vehicleItems = const [],
    this.driverItems = const [],
  });

  Map<String, dynamic> toJson() {
    return {
      'request_no': requestNo,
      'sub_module_type': subModuleType.code,
      'permit_basis': permitBasis.code,
      'parent_entry_req_no': parentEntryReqNo,
      'exit_reason': exitReason,
      'ref_doc_no': refDocNo,
      'input_channel': inputChannel,
      'company_id': companyId,
      'vendor_name': vendorName,
      'target_dept_id': targetDeptId,
      'target_section_id': targetSectionId,
      'destination_zone': destinationZone.code,
      'specific_location': specificLocation,
      'est_arrival_date': estArrivalDate.toIso8601String(),
      'est_arrival_time': estArrivalTime,
      'memo_file_id': memoFileId,
      'ca_doc_file_id': caDocFileId,
      'has_expiry_warning': hasExpiryWarning ? 'Y' : 'N',
      'valid_from': validFrom?.toIso8601String(),
      'valid_to': validTo?.toIso8601String(),
      'qr_code_token': qrCodeToken,
      'permit_status': permitStatus.code,
      'po_do_items': poDoItems.map((e) => e.toJson()).toList(),
      'vehicle_items': vehicleItems.map((e) => e.toJson()).toList(),
      'driver_items': driverItems.map((e) => e.toJson()).toList(),
    };
  }

  factory MaterialPermitHeader.fromJson(Map<String, dynamic> json) {
    return MaterialPermitHeader(
      requestNo: json['request_no'] as String? ?? '',
      subModuleType: SubModuleType.fromCode(json['sub_module_type'] as String? ?? 'ENTRY'),
      permitBasis: PermitBasis.fromCode(json['permit_basis'] as String? ?? 'PO'),
      parentEntryReqNo: json['parent_entry_req_no'] as String?,
      exitReason: json['exit_reason'] as String?,
      refDocNo: json['ref_doc_no'] as String? ?? '',
      inputChannel: json['input_channel'] as String? ?? 'INTRANET',
      companyId: json['company_id'] as String?,
      vendorName: json['vendor_name'] as String? ?? '',
      targetDeptId: json['target_dept_id'] as String? ?? '',
      targetSectionId: json['target_section_id'] as String?,
      destinationZone: DestinationZone.fromCode(json['destination_zone'] as String? ?? 'ZONE_2'),
      specificLocation: json['specific_location'] as String? ?? '',
      estArrivalDate: DateTime.parse(json['est_arrival_date'] as String),
      estArrivalTime: json['est_arrival_time'] as String?,
      memoFileId: json['memo_file_id'] as int?,
      caDocFileId: json['ca_doc_file_id'] as int?,
      hasExpiryWarning: (json['has_expiry_warning'] as String? ?? 'N') == 'Y',
      validFrom: json['valid_from'] != null ? DateTime.parse(json['valid_from'] as String) : null,
      validTo: json['valid_to'] != null ? DateTime.parse(json['valid_to'] as String) : null,
      qrCodeToken: json['qr_code_token'] as String?,
      permitStatus: PermitStatus.fromCode(json['permit_status'] as String? ?? 'DRAFT'),
      poDoItems: (json['po_do_items'] as List<dynamic>?)
              ?.map((e) => PoDoItem.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      vehicleItems: (json['vehicle_items'] as List<dynamic>?)
              ?.map((e) => VehicleItem.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      driverItems: (json['driver_items'] as List<dynamic>?)
              ?.map((e) => DriverItem.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }

  List<String> validate({DateTime? referenceDate}) {
    final ref = referenceDate ?? DateTime.now();
    final today = DateTime(ref.year, ref.month, ref.day);
    final errors = <String>[];

    if (requestNo.trim().isEmpty) errors.add('Nomor permohonan wajib ada');
    if (vendorName.trim().isEmpty) errors.add('Nama perusahaan/vendor wajib diisi');
    if (targetDeptId.trim().isEmpty) errors.add('Departemen tujuan wajib dipilih');
    if (specificLocation.trim().isEmpty) errors.add('Sub-lokasi spesifik wajib diisi');

    // Aturan No-Backdate
    final cleanArrival = DateTime(estArrivalDate.year, estArrivalDate.month, estArrivalDate.day);
    if (cleanArrival.isBefore(today)) {
      errors.add('Estimasi tanggal kedatangan tidak boleh tanggal lampau (No Backdate)');
    }

    // Aturan Linkage Izin Keluar
    if (subModuleType == SubModuleType.exit) {
      if (parentEntryReqNo == null || parentEntryReqNo!.trim().isEmpty) {
        errors.add('Izin Keluar Barang wajib mereferensikan nomor izin masuk awal (Mandatory Linkage)');
      }
      if (exitReason == null || exitReason!.trim().isEmpty) {
        errors.add('Alasan pengeluaran barang wajib dicantumkan');
      }
    }

    // Validasi lampiran wajib
    if (memoFileId == null || memoFileId! <= 0) {
      errors.add('Berkas Memo Permintaan Izin Masuk & KWS wajib dilampirkan');
    }
    if (permitBasis == PermitBasis.ca && (caDocFileId == null || caDocFileId! <= 0)) {
      errors.add('Dokumen Contractor Agreement (CA) wajib diunggah untuk pengajuan kontrak');
    }

    return errors;
  }
}

