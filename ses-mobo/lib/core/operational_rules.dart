// Aturan bisnis operasional hasil verifikasi rekaman audio teknis
// Sesuai Master Blueprint Bab 2 & Stage 02.

import 'app_constants.dart';

class OperationalRules {
  OperationalRules._();

  static const String maskedPriceLabel = '*** CONFIDENTIAL / PRICE MASKED ***';

  /// Isu 2: Price Masking - Nilai rupiah PO disembunyikan mutlak pada layar posko
  static String formatMaskedPrice([num? price]) {
    return maskedPriceLabel;
  }

  /// Isu 6 & Stage 07: Aturan Re-Screening Kritis Pasca Check-Out
  /// Jika kendaraan telah berstatus CHECKED_OUT, kedatangan berikutnya
  /// WAJIB melakukan screening ulang fisik sebelum dapat Check-In kembali.
  static bool isReScreeningRequired({
    required VisitCycleStatus lastStatus,
    required bool hasAdminSkipInspection,
  }) {
    // Jika kendaraan memiliki hak bypass KKB yang telah disetujui Admin Security
    if (hasAdminSkipInspection) {
      return false;
    }

    // Jika belum pernah screening sama sekali
    if (lastStatus == VisitCycleStatus.none) {
      return true;
    }

    // Jika kendaraan sudah keluar kilang (CHECKED_OUT), trip berikutnya wajib screening ulang
    if (lastStatus == VisitCycleStatus.checkedOut) {
      return true;
    }

    // Jika statusnya baru saja SCREENED (lulus cek fisik) dan hendak Check-In
    if (lastStatus == VisitCycleStatus.screened) {
      return false;
    }

    return false;
  }

  /// Isu 7: Validasi Dropdown Non-Free Text di Aplikasi Posko
  /// Sopir dan kendaraan wajib terdaftar di dalam daftar request.
  static bool validateRequestItemSelection({
    required List<String> registeredItemIds,
    required String selectedItemId,
  }) {
    if (selectedItemId.trim().isEmpty) return false;
    return registeredItemIds.contains(selectedItemId.trim());
  }

  /// Isu 8: Logika Kontraktor CA vs Stiker KKB
  /// Stiker KKB aktif hanya memberikan hak bypass jika Admin Security mencentang
  /// flag ADMIN_SKIP_INSPECTION.
  static bool resolveKkbBypass({
    required bool hasActiveKkb,
    required bool adminSkipInspection,
  }) {
    return hasActiveKkb && adminSkipInspection;
  }

  /// Isu 10: Indikator Kuning (Soft Warning Highlight)
  /// Menandai dokumen yang sudah kedaluwarsa atau akan mati dalam rentang hari kedatangan
  static bool shouldShowExpiryWarning({
    required DateTime? docExpiryDate,
    required DateTime arrivalDate,
  }) {
    if (docExpiryDate == null) return true;
    final cleanExpiry = DateTime(docExpiryDate.year, docExpiryDate.month, docExpiryDate.day);
    final cleanArrival = DateTime(arrivalDate.year, arrivalDate.month, arrivalDate.day);
    return cleanExpiry.isBefore(cleanArrival) || cleanExpiry.isAtSameMomentAs(cleanArrival);
  }
}

