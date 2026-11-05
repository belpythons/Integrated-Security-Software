import 'package:flutter_test/flutter_test.dart';
import 'package:ses_masuk_barang/core/app_constants.dart';
import 'package:ses_masuk_barang/core/operational_rules.dart';

void main() {
  group('Stage 02: Aturan Operasional Berbasis Bukti Audio', () {
    test('Isu 2: Price Masking menyembunyikan nilai finansial', () {
      expect(OperationalRules.formatMaskedPrice(150000000), '*** CONFIDENTIAL / PRICE MASKED ***');
      expect(OperationalRules.formatMaskedPrice(null), '*** CONFIDENTIAL / PRICE MASKED ***');
    });

    test('Isu 6: Aturan Re-Screening Pasca Check-Out Kritis', () {
      // Kasus 1: Belum pernah screening -> Wajib screening
      expect(
        OperationalRules.isReScreeningRequired(
          lastStatus: VisitCycleStatus.none,
          hasAdminSkipInspection: false,
        ),
        isTrue,
      );

      // Kasus 2: Baru lulus screening -> Tidak perlu screening ulang saat mau Check-In
      expect(
        OperationalRules.isReScreeningRequired(
          lastStatus: VisitCycleStatus.screened,
          hasAdminSkipInspection: false,
        ),
        isFalse,
      );

      // Kasus 3: Truk sudah keluar kilang (CHECKED_OUT) dan kembali lagi -> WAJIB SCREENING ULANG
      expect(
        OperationalRules.isReScreeningRequired(
          lastStatus: VisitCycleStatus.checkedOut,
          hasAdminSkipInspection: false,
        ),
        isTrue,
        reason: 'Kendaraan yang sudah CHECKED_OUT wajib screening ulang sebelum check-in trip berikutnya',
      );

      // Kasus 4: Kendaraan ber-stiker KKB yang di-bypass Admin -> Bebas screening kapanpun
      expect(
        OperationalRules.isReScreeningRequired(
          lastStatus: VisitCycleStatus.checkedOut,
          hasAdminSkipInspection: true,
        ),
        isFalse,
      );
    });

    test('Isu 7: Validasi Dropdown Non-Free Text di Posko', () {
      final validDrivers = ['Samsul', 'Budi', 'Joko'];
      expect(
        OperationalRules.validateRequestItemSelection(
          registeredItemIds: validDrivers,
          selectedItemId: 'Samsul',
        ),
        isTrue,
      );

      expect(
        OperationalRules.validateRequestItemSelection(
          registeredItemIds: validDrivers,
          selectedItemId: 'Ahmad Yani (Sopir Luar Tidak Terdaftar)',
        ),
        isFalse,
        reason: 'Sopir yang tidak ada di daftar request wajib ditolak',
      );
    });

    test('Isu 8: Logika Bypass KKB vs Kontraktor CA', () {
      // Memiliki stiker KKB dan dicentang oleh Admin Security -> Bypass
      expect(
        OperationalRules.resolveKkbBypass(hasActiveKkb: true, adminSkipInspection: true),
        isTrue,
      );

      // Memiliki stiker KKB tetapi Admin tidak mencentang -> Tidak bypass
      expect(
        OperationalRules.resolveKkbBypass(hasActiveKkb: true, adminSkipInspection: false),
        isFalse,
      );

      // Kontraktor CA memakai mobil toko (non-KKB) -> Tidak bypass, wajib cek fisik
      expect(
        OperationalRules.resolveKkbBypass(hasActiveKkb: false, adminSkipInspection: false),
        isFalse,
      );
    });

    test('Isu 10: Evaluasi Indikator Kuning (Soft Warning Highlight)', () {
      final arrivalDate = DateTime(2026, 11, 5);

      // Dokumen mati sebelum kedatangan -> Kuning
      expect(
        OperationalRules.shouldShowExpiryWarning(
          docExpiryDate: DateTime(2026, 11, 4),
          arrivalDate: arrivalDate,
        ),
        isTrue,
      );

      // Dokumen mati persis pada hari kedatangan -> Kuning
      expect(
        OperationalRules.shouldShowExpiryWarning(
          docExpiryDate: DateTime(2026, 11, 5),
          arrivalDate: arrivalDate,
        ),
        isTrue,
      );

      // Dokumen masih aktif jauh ke depan -> Tidak kuning
      expect(
        OperationalRules.shouldShowExpiryWarning(
          docExpiryDate: DateTime(2026, 11, 10),
          arrivalDate: arrivalDate,
        ),
        isFalse,
      );
    });
  });
}

