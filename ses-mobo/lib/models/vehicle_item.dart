// Model data kendaraan pengangkut material
// Sesuai Master Blueprint Bab 3.4 & Stage 04.

class VehicleItem {
  final String vehicleItemId;
  final String requestNo;
  final String licensePlateNo;
  final String vehicleCategory;
  final String vehicleBrand;
  final DateTime stnk5YrExpiry;
  final DateTime stnkTaxExpiry;
  final int stnkFileId;
  final bool isKirRequired;
  final DateTime? kirExpiryDate;
  final int? kirFileId;
  final bool hasActiveKkb;
  final String? kkbStickerNo;
  final int? kkbFileId;
  final bool adminSkipInspection;

  VehicleItem({
    required this.vehicleItemId,
    required this.requestNo,
    required this.licensePlateNo,
    required this.vehicleCategory,
    required this.vehicleBrand,
    required this.stnk5YrExpiry,
    required this.stnkTaxExpiry,
    required this.stnkFileId,
    this.isKirRequired = true,
    this.kirExpiryDate,
    this.kirFileId,
    this.hasActiveKkb = false,
    this.kkbStickerNo,
    this.kkbFileId,
    this.adminSkipInspection = false,
  });

  /// Evaluasi apakah kendaraan lolos bypass cek fisik
  bool get isInspectionBypassed => hasActiveKkb && adminSkipInspection;

  Map<String, dynamic> toJson() {
    return {
      'vehicle_item_id': vehicleItemId,
      'request_no': requestNo,
      'license_plate_no': licensePlateNo,
      'vehicle_category': vehicleCategory,
      'vehicle_brand': vehicleBrand,
      'stnk_5yr_expiry': stnk5YrExpiry.toIso8601String(),
      'stnk_tax_expiry': stnkTaxExpiry.toIso8601String(),
      'stnk_file_id': stnkFileId,
      'is_kir_required': isKirRequired ? 'Y' : 'N',
      'kir_expiry_date': kirExpiryDate?.toIso8601String(),
      'kir_file_id': kirFileId,
      'has_active_kkb': hasActiveKkb ? 'Y' : 'N',
      'kkb_sticker_no': kkbStickerNo,
      'kkb_file_id': kkbFileId,
      'admin_skip_inspection': adminSkipInspection ? 'Y' : 'N',
    };
  }

  factory VehicleItem.fromJson(Map<String, dynamic> json) {
    return VehicleItem(
      vehicleItemId: json['vehicle_item_id'] as String? ?? '',
      requestNo: json['request_no'] as String? ?? '',
      licensePlateNo: json['license_plate_no'] as String? ?? '',
      vehicleCategory: json['vehicle_category'] as String? ?? 'TRUCK',
      vehicleBrand: json['vehicle_brand'] as String? ?? '',
      stnk5YrExpiry: DateTime.parse(json['stnk_5yr_expiry'] as String),
      stnkTaxExpiry: DateTime.parse(json['stnk_tax_expiry'] as String),
      stnkFileId: json['stnk_file_id'] as int? ?? 0,
      isKirRequired: (json['is_kir_required'] as String? ?? 'Y') == 'Y',
      kirExpiryDate: json['kir_expiry_date'] != null ? DateTime.parse(json['kir_expiry_date'] as String) : null,
      kirFileId: json['kir_file_id'] as int?,
      hasActiveKkb: (json['has_active_kkb'] as String? ?? 'N') == 'Y',
      kkbStickerNo: json['kkb_sticker_no'] as String?,
      kkbFileId: json['kkb_file_id'] as int?,
      adminSkipInspection: (json['admin_skip_inspection'] as String? ?? 'N') == 'Y',
    );
  }

  List<String> validate({DateTime? referenceDate}) {
    final ref = referenceDate ?? DateTime.now();
    final today = DateTime(ref.year, ref.month, ref.day);
    final errors = <String>[];

    if (licensePlateNo.trim().isEmpty) errors.add('Plat nomor polisi wajib diisi');
    if (vehicleBrand.trim().isEmpty) errors.add('Merk kendaraan wajib diisi');

    // Validasi STNK 5 tahunan & Pajak Tahunan
    final cleanStnk = DateTime(stnk5YrExpiry.year, stnk5YrExpiry.month, stnk5YrExpiry.day);
    final cleanTax = DateTime(stnkTaxExpiry.year, stnkTaxExpiry.month, stnkTaxExpiry.day);

    if (cleanStnk.isBefore(today)) errors.add('Masa berlaku STNK 5 tahunan telah kedaluwarsa');
    if (cleanTax.isBefore(today)) errors.add('Pajak Tahunan STNK telah kedaluwarsa (Pajak mati)');
    if (stnkFileId <= 0) errors.add('Scan berkas STNK wajib diunggah');

    // Validasi Buku KIR untuk angkutan barang
    if (isKirRequired) {
      if (kirExpiryDate == null) {
        errors.add('Masa berlaku Buku KIR wajib diisi untuk angkutan barang');
      } else {
        final cleanKir = DateTime(kirExpiryDate!.year, kirExpiryDate!.month, kirExpiryDate!.day);
        if (cleanKir.isBefore(today)) errors.add('Masa berlaku Buku KIR telah kedaluwarsa');
      }
      if (kirFileId == null || kirFileId! <= 0) {
        errors.add('Scan Buku KIR wajib diunggah untuk angkutan barang');
      }
    }

    if (hasActiveKkb && (kkbStickerNo == null || kkbStickerNo!.trim().isEmpty)) {
      errors.add('Nomor Stiker KKB wajib dicantumkan jika mengklaim KKB aktif');
    }

    return errors;
  }
}

