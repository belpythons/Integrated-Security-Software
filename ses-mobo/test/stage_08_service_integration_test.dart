import 'package:flutter_test/flutter_test.dart';
import 'package:ses_masuk_barang/core/app_constants.dart';
import 'package:ses_masuk_barang/core/security_watermark.dart';
import 'package:ses_masuk_barang/services/mock_permit_service.dart';
import 'package:ses_masuk_barang/main.dart';

void main() {
  group('Stage 08: Arsitektur Backend, Keamanan, & Integrasi Service Test', () {
    late MockPermitService service;

    setUp(() {
      service = MockPermitService();
    });

    test('MockPermitService initializes 3 realistic operational cases', () async {
      final permits = await service.fetchActivePermits();
      expect(permits.length, 3);

      final vendors = permits.map((p) => p.vendorName).toList();
      expect(vendors, contains('PT Amalindo Utama'));
      expect(vendors, contains('PT Kilang Mandiri'));
      expect(vendors, contains('CV Berkah Pangan Makmur'));

      // Kasus 1: PO Reguler
      final poPermit = permits.firstWhere((p) => p.vendorName == 'PT Amalindo Utama');
      expect(poPermit.permitBasis, PermitBasis.po);
      expect(poPermit.poDoItems.length, 2); // Multi-PO item
      expect(poPermit.vehicleItems.first.hasActiveKkb, isFalse);

      // Kasus 2: Kontrak CA dengan stiker KKB aktif
      final caPermit = permits.firstWhere((p) => p.vendorName == 'PT Kilang Mandiri');
      expect(caPermit.permitBasis, PermitBasis.ca);
      expect(caPermit.vehicleItems.first.isInspectionBypassed, isTrue);

      // Kasus 3: Fasilitas Sembako Kantin
      final kantinPermit = permits.firstWhere((p) => p.vendorName == 'CV Berkah Pangan Makmur');
      expect(kantinPermit.permitBasis, PermitBasis.facilityMemo);
      expect(kantinPermit.destinationZone, DestinationZone.gedungPutih);
    });

    test('Find permit by QR Token and Request Number', () async {
      // Cari via nomor tiket
      final p1 = await service.findPermitByQrToken('REQ-MAT-20261103-0001');
      expect(p1, isNotNull);
      expect(p1!.vendorName, 'PT Amalindo Utama');

      // Cari via token QR
      final p2 = await service.findPermitByQrToken('QR-TOKEN-KILANGMANDIRI-002');
      expect(p2, isNotNull);
      expect(p2!.vendorName, 'PT Kilang Mandiri');

      // Token invalid
      final p3 = await service.findPermitByQrToken('QR-INVALID-999');
      expect(p3, isNull);
    });

    test('Dynamic Watermark Text format compliance', () {
      final fixedDate = DateTime(2026, 11, 3, 9, 30, 15);
      final watermark = SecurityWatermark.generateWatermarkText(
        userNip: 'SEC-88210',
        timestamp: fixedDate,
      );

      expect(
        watermark,
        'CONFIDENTIAL PT BADAK NGL - DOWNLOADED BY: SEC-88210 AT 2026-11-03 09:30:15 WITA',
      );
    });

    test('Testing Mode safeguard email redirection', () {
      const realRecipient = 'manager.engineering@badakngl.co.id';

      // Saat TESTING_MODE = ON, email dialihkan ke dev-test
      final testEmail = SecurityWatermark.resolveNotificationRecipient(
        originalRecipient: realRecipient,
        isTestingMode: true,
      );
      expect(testEmail, 'dev-test@badakngl.co.id');

      // Saat TESTING_MODE = OFF, email terkirim ke pejabat asli
      final prodEmail = SecurityWatermark.resolveNotificationRecipient(
        originalRecipient: realRecipient,
        isTestingMode: false,
      );
      expect(prodEmail, realRecipient);
    });

    testWidgets('SesMoboApp entrypoint smoke test renders Posko Home Page', (tester) async {
      final permits = await service.fetchActivePermits();

      await tester.pumpWidget(SesMoboApp(permits: permits));
      await tester.pumpAndSettle();

      // Verifikasi judul posko
      expect(find.text('SES Security Suite Mobile'), findsOneWidget);
      expect(find.text('Posko Pemeriksaan Fisik Truk Lapangan Bola'), findsOneWidget);
      expect(find.text('Petugas Aktif: NIP SEC-88210'), findsOneWidget);

      // Verifikasi kartu tiket tampil
      expect(find.text('PT Amalindo Utama'), findsOneWidget);
      expect(find.text('PT Kilang Mandiri'), findsOneWidget);
      expect(find.text('CV Berkah Pangan Makmur'), findsOneWidget);
    });
  });
}

