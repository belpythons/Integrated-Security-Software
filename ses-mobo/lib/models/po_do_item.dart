// Model data transaksi PO dan DO (mendukung Multi-PO per PT dan Softcopy Terpisah)
// Sesuai Master Blueprint Bab 3.3 & Stage 04.

import '../core/operational_rules.dart';

class PoDoItem {
  final int? permitPoId;
  final String requestNo;
  final String poNumber;
  final DateTime? poOrderDate;
  final String poDescription;
  final String doNumber;
  final DateTime doDate;
  final num? rawUnitPrice; // Disimpan internal, di-masking saat ekspose UI/Posko
  final int poFileId;
  final int doFileId;

  PoDoItem({
    this.permitPoId,
    required this.requestNo,
    required this.poNumber,
    this.poOrderDate,
    required this.poDescription,
    required this.doNumber,
    required this.doDate,
    this.rawUnitPrice,
    required this.poFileId,
    required this.doFileId,
  });

  /// Mengembalikan harga ter-masking untuk posko dan laporan publik
  String get displayPrice => OperationalRules.formatMaskedPrice(rawUnitPrice);

  Map<String, dynamic> toJson({bool maskPrice = true}) {
    return {
      'permit_po_id': permitPoId,
      'request_no': requestNo,
      'po_number': poNumber,
      'po_order_date': poOrderDate?.toIso8601String(),
      'po_description': poDescription,
      'do_number': doNumber,
      'do_date': doDate.toIso8601String(),
      'unit_price': maskPrice ? OperationalRules.maskedPriceLabel : rawUnitPrice,
      'po_file_id': poFileId,
      'do_file_id': doFileId,
    };
  }

  factory PoDoItem.fromJson(Map<String, dynamic> json) {
    return PoDoItem(
      permitPoId: json['permit_po_id'] as int?,
      requestNo: json['request_no'] as String? ?? '',
      poNumber: json['po_number'] as String? ?? '',
      poOrderDate: json['po_order_date'] != null ? DateTime.parse(json['po_order_date'] as String) : null,
      poDescription: json['po_description'] as String? ?? '',
      doNumber: json['do_number'] as String? ?? '',
      doDate: DateTime.parse(json['do_date'] as String),
      rawUnitPrice: json['unit_price'] is num ? json['unit_price'] as num : null,
      poFileId: json['po_file_id'] as int? ?? 0,
      doFileId: json['do_file_id'] as int? ?? 0,
    );
  }

  List<String> validate() {
    final errors = <String>[];
    if (poNumber.trim().isEmpty) errors.add('Nomor PO wajib diisi');
    if (doNumber.trim().isEmpty) errors.add('Nomor DO wajib diisi');
    if (poDescription.trim().isEmpty) errors.add('Deskripsi PO wajib diisi');
    if (poFileId <= 0) errors.add('Softcopy berkas PO wajib diunggah');
    if (doFileId <= 0) errors.add('Softcopy berkas DO wajib diunggah terpisah');
    return errors;
  }
}

