// Model data personil pengemudi dan kenek
// Sesuai Master Blueprint Bab 3.5 & Stage 04.

class DriverItem {
  final String driverItemId;
  final String requestNo;
  final String personnelRole; // 'DRIVER' atau 'HELPER'
  final String driverOriginType; // 'KKB_CONTRACTOR' atau 'EXTERNAL_NON_KKB'
  final String? badgeNo;
  final String? nikKtp;
  final String driverName;
  final int idCardFileId;
  final String? licenseType; // 'SIMPER', 'SIM_A', 'SIM_B1', 'SIM_B2'
  final String? licenseNo;
  final DateTime? simExpiryDate;
  final int? simFileId;

  DriverItem({
    required this.driverItemId,
    required this.requestNo,
    required this.personnelRole,
    required this.driverOriginType,
    this.badgeNo,
    this.nikKtp,
    required this.driverName,
    required this.idCardFileId,
    this.licenseType,
    this.licenseNo,
    this.simExpiryDate,
    this.simFileId,
  });

  bool get isDriver => personnelRole.toUpperCase() == 'DRIVER';
  bool get isKkbContractor => driverOriginType.toUpperCase() == 'KKB_CONTRACTOR';

  Map<String, dynamic> toJson() {
    return {
      'driver_item_id': driverItemId,
      'request_no': requestNo,
      'personnel_role': personnelRole,
      'driver_origin_type': driverOriginType,
      'badge_no': badgeNo,
      'nik_ktp': nikKtp,
      'driver_name': driverName,
      'id_card_file_id': idCardFileId,
      'license_type': licenseType,
      'license_no': licenseNo,
      'sim_expiry_date': simExpiryDate?.toIso8601String(),
      'sim_file_id': simFileId,
    };
  }

  factory DriverItem.fromJson(Map<String, dynamic> json) {
    return DriverItem(
      driverItemId: json['driver_item_id'] as String? ?? '',
      requestNo: json['request_no'] as String? ?? '',
      personnelRole: json['personnel_role'] as String? ?? 'DRIVER',
      driverOriginType: json['driver_origin_type'] as String? ?? 'EXTERNAL_NON_KKB',
      badgeNo: json['badge_no'] as String?,
      nikKtp: json['nik_ktp'] as String?,
      driverName: json['driver_name'] as String? ?? '',
      idCardFileId: json['id_card_file_id'] as int? ?? 0,
      licenseType: json['license_type'] as String?,
      licenseNo: json['license_no'] as String?,
      simExpiryDate: json['sim_expiry_date'] != null ? DateTime.parse(json['sim_expiry_date'] as String) : null,
      simFileId: json['sim_file_id'] as int?,
    );
  }

  List<String> validate({DateTime? referenceDate}) {
    final ref = referenceDate ?? DateTime.now();
    final today = DateTime(ref.year, ref.month, ref.day);
    final errors = <String>[];

    if (driverName.trim().isEmpty) errors.add('Nama pengemudi/kenek wajib diisi');
    if (idCardFileId <= 0) errors.add('Scan berkas identitas (KTP/Badge) wajib diunggah');

    // Validasi identitas KKB Kontraktor vs Mobil Luar
    if (isKkbContractor) {
      if (badgeNo == null || badgeNo!.trim().isEmpty) {
        errors.add('Nomor Badge wajib diisi untuk pengemudi KKB Kontraktor');
      }
    } else {
      if (nikKtp == null || nikKtp!.trim().length != 16) {
        errors.add('Nomor KTP wajib 16 digit untuk pengemudi eksternal');
      }
    }

    // Validasi kewajiban SIM untuk peran Driver
    if (isDriver) {
      if (licenseNo == null || licenseNo!.trim().isEmpty) {
        errors.add('Nomor lisensi SIM wajib diisi untuk pengemudi');
      }
      if (simExpiryDate == null) {
        errors.add('Masa berlaku SIM wajib diisi untuk pengemudi');
      } else {
        final cleanSim = DateTime(simExpiryDate!.year, simExpiryDate!.month, simExpiryDate!.day);
        if (cleanSim.isBefore(today)) errors.add('Masa berlaku SIM pengemudi telah kedaluwarsa');
      }
      if (simFileId == null || simFileId! <= 0) {
        errors.add('Scan berkas SIM wajib diunggah untuk pengemudi');
      }
    }

    return errors;
  }
}

