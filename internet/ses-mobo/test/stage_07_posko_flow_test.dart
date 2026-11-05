import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ses_masuk_barang/core/app_constants.dart';
import 'package:ses_masuk_barang/core/operational_rules.dart';
import 'package:ses_masuk_barang/models/permit_header.dart';
import 'package:ses_masuk_barang/models/po_do_item.dart';
import 'package:ses_masuk_barang/models/vehicle_item.dart';
import 'package:ses_masuk_barang/models/driver_item.dart';
import 'package:ses_masuk_barang/models/screening_log.dart';
import 'package:ses_masuk_barang/pages/permit_detail_page.dart';

void main() {
  group('Stage 07: Operasional Mobile Posko Security & Gate Test', () {
    final baseDate = DateTime(2026, 11, 3);

    test('ScreeningLog Check-In & Check-Out state cycle with KTP holding', () {
      final log = ScreeningLog(
        logId: 1,
        requestNo: 'REQ-001',
        licensePlateNo: 'KT 1111 AA',
        driverName: 'Sopir Budi',
        screeningResult: ScreeningResult.approved,
        inspectorNip: 'SEC-01',
        screeningTimestamp: baseDate,
      );

      expect(log.isEligibleForCheckIn, isTrue);
      expect(log.isKtpHeld, isFalse);

      // Lakukan Check-In
      log.performCheckIn(badgeNo: 'VB-9901');
      expect(log.visitStatus, VisitCycleStatus.checkedIn);
      expect(log.isKtpHeld, isTrue);
      expect(log.visitorBadgeNo, 'VB-9901');
      expect(log.checkinTimestamp, isNotNull);

      // Lakukan Check-Out
      log.performCheckOut();
      expect(log.visitStatus, VisitCycleStatus.checkedOut);
      expect(log.isKtpHeld, isFalse);
      expect(log.checkoutTimestamp, isNotNull);
    });

    test('Re-screening rule: vehicle with CHECKED_OUT status MUST re-screen on re-entry', () {
      // Kendaraan umum non-KKB yang baru pertama kali tiba (NONE)
      expect(
        OperationalRules.isReScreeningRequired(
          lastStatus: VisitCycleStatus.none,
          hasAdminSkipInspection: false,
        ),
        isTrue,
      );

      // Kendaraan yang sudah CHECKED_OUT (keluar kilang)
      expect(
        OperationalRules.isReScreeningRequired(
          lastStatus: VisitCycleStatus.checkedOut,
          hasAdminSkipInspection: false,
        ),
        isTrue, // Audio rule: Wajib screening ulang!
      );

      // Kendaraan dengan KKB bypass
      expect(
        OperationalRules.isReScreeningRequired(
          lastStatus: VisitCycleStatus.none,
          hasAdminSkipInspection: true,
        ),
        isFalse,
      );
    });

    testWidgets('PermitDetailPage verifies Price Masking, Dropdown, and Check-In/Check-Out flow',
        (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final permit = MaterialPermitHeader(
        requestNo: 'REQ-MAT-20261103-0001',
        subModuleType: SubModuleType.entry,
        permitBasis: PermitBasis.po,
        refDocNo: 'PO-882199',
        inputChannel: 'INTRANET',
        vendorName: 'PT Multi Karya Mandiri',
        targetDeptId: 'D-WHS',
        destinationZone: DestinationZone.zone2,
        specificLocation: 'Gudang Material Utama',
        estArrivalDate: baseDate,
        validFrom: baseDate,
        validTo: baseDate.add(const Duration(days: 3)),
        permitStatus: PermitStatus.approved,
        poDoItems: [
          PoDoItem(
            requestNo: 'REQ-MAT-20261103-0001',
            poNumber: 'PO-882199',
            poDescription: 'Gate Valve 4 Inch Stainless Steel',
            doNumber: 'DO-001',
            doDate: baseDate,
            rawUnitPrice: 5500000,
            poFileId: 101,
            doFileId: 102,
          ),
        ],
        vehicleItems: [
          VehicleItem(
            vehicleItemId: 'VH-01',
            requestNo: 'REQ-MAT-20261103-0001',
            licensePlateNo: 'KT 8821 ZZ',
            vehicleCategory: 'TRUCK',
            vehicleBrand: 'Hino Dutro',
            stnk5YrExpiry: baseDate.add(const Duration(days: 100)),
            stnkTaxExpiry: baseDate.add(const Duration(days: 100)),
            stnkFileId: 501,
            hasActiveKkb: true,
            kkbStickerNo: 'KKB-2026-99',
            adminSkipInspection: true, // KKB Bypass aktif
          ),
        ],
        driverItems: [
          DriverItem(
            driverItemId: 'DR-01',
            requestNo: 'REQ-MAT-20261103-0001',
            personnelRole: 'DRIVER',
            driverOriginType: 'KKB_CONTRACTOR',
            driverName: 'Pak Suparman',
            idCardFileId: 601,
            simExpiryDate: baseDate.add(const Duration(days: 200)),
          ),
        ],
      );

      await tester.pumpWidget(
        MaterialApp(
          home: PermitDetailPage(permit: permit),
        ),
      );

      // 1. Verifikasi Price Masking
      expect(find.text('HARGA DIRAHASIAKAN'), findsOneWidget);
      expect(find.text('Gate Valve 4 Inch Stainless Steel (Ref: PO-882199)'), findsOneWidget);

      // 2. Verifikasi KKB Bypass Banner
      expect(find.text('KKB STICKER BYPASS AKTIF'), findsOneWidget);

      // 3. Input Visitor Badge dan Jalankan Check-In
      final badgeField = find.widgetWithText(TextFormField, 'Nomor Seri Visitor Badge (Diserahkan ke Sopir)');
      expect(badgeField, findsOneWidget);
      await tester.ensureVisible(badgeField);
      await tester.enterText(badgeField, 'VB-8821');

      final checkInButton = find.widgetWithText(ElevatedButton, 'PROSES CHECK-IN & TAHAN KTP');
      expect(checkInButton, findsOneWidget);
      await tester.ensureVisible(checkInButton);
      await tester.tap(checkInButton);
      await tester.pumpAndSettle();

      // 4. Verifikasi Status Berubah ke Checked-In dan Tombol Check-Out Muncul
      final checkOutButton = find.widgetWithText(ElevatedButton, 'PROSES CHECK-OUT & KEMBALIKAN KTP');
      expect(checkOutButton, findsOneWidget);
      expect(find.textContaining('KTP Asli Sopir Pak Suparman sedang DITAHAN'), findsOneWidget);

      // 5. Jalankan Check-Out
      await tester.ensureVisible(checkOutButton);
      await tester.tap(checkOutButton);
      await tester.pumpAndSettle();

      // 6. Verifikasi Status Kembali Meminta Badge Baru untuk Siklus Berikutnya
      expect(find.widgetWithText(ElevatedButton, 'PROSES CHECK-IN & TAHAN KTP'), findsOneWidget);
    });
  });
}
