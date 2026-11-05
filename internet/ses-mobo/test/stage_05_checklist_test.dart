import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ses_masuk_barang/models/inspection_checklist_model.dart';
import 'package:ses_masuk_barang/widgets/checklist_11_points_widget.dart';

void main() {
  group('Stage 05 - Inspection Checklist 11 Categories & PPE Test', () {
    test('Standard report initialization should contain exactly 11 categories', () {
      final report = VehicleInspectionReport.initStandard(inspectorNip: 'SEC-007');

      expect(report.inspectorNip, 'SEC-007');
      expect(report.categories.length, 11);
      expect(report.isAllCategoriesPassed, isTrue);

      final titles = report.categories.map((c) => c.categoryTitle).toList();
      expect(titles.any((t) => t.contains('Mesin Utama')), isTrue);
      expect(titles.any((t) => t.contains('Sistem Bahan Bakar')), isTrue);
      expect(titles.any((t) => t.contains('Sistem Roda')), isTrue);
      expect(titles.any((t) => t.contains('Sistem Kemudi')), isTrue);
      expect(titles.any((t) => t.contains('Sistem Rem')), isTrue);
      expect(titles.any((t) => t.contains('Sistem Kelistrikan')), isTrue);
      expect(titles.any((t) => t.contains('Sistem Penerangan')), isTrue);
      expect(titles.any((t) => t.contains('Komponen Pendukung')), isTrue);
      expect(titles.any((t) => t.contains('Kelengkapan Tambahan')), isTrue);
      expect(titles.any((t) => t.contains('Kelengkapan Khusus')), isTrue);
      expect(titles.any((t) => t.contains('Ketentuan Umum')), isTrue);
    });

    test('If any category is FAIL, isAllCategoriesPassed must be false', () {
      final report = VehicleInspectionReport.initStandard(inspectorNip: 'SEC-007');
      // Set category V (Sistem Rem) to FAIL
      report.categories[4].status = InspectionStatus.fail;
      report.categories[4].notes = 'Minyak rem bocor dan handbrake blong';

      expect(report.isAllCategoriesPassed, isFalse);
      expect(report.getFailedCategoryNames(), contains('V. Sistem Rem'));
    });

    test('Driver PPE compliance rule for Zone 1 vs other zones', () {
      final report = VehicleInspectionReport.initStandard(inspectorNip: 'SEC-007');

      // Default: Safety shoes, long pants, collared shirt = true; helmet = false
      expect(report.driverPpe.isCompliantForZone(isZone1: false), isTrue);
      expect(report.driverPpe.isCompliantForZone(isZone1: true), isFalse);

      // Equipping helmet & glasses
      report.driverPpe.helmetGlasses = true;
      expect(report.driverPpe.isCompliantForZone(isZone1: true), isTrue);

      // If missing safety shoes, fails anywhere
      report.driverPpe.safetyShoes = false;
      expect(report.driverPpe.isCompliantForZone(isZone1: false), isFalse);
      expect(report.driverPpe.isCompliantForZone(isZone1: true), isFalse);
    });

    test('JSON serialization and roundtrip of inspection report', () {
      final report = VehicleInspectionReport.initStandard(inspectorNip: 'SEC-007');
      report.categories[2].status = InspectionStatus.fail;
      report.categories[2].notes = 'Ban gundul sebelah kanan';

      final json = report.toJson();
      expect(json['inspector_nip'], 'SEC-007');
      expect((json['categories'] as List).length, 11);
      expect(json['is_all_passed'], isFalse);

      final ppeJson = json['driver_ppe'] as Map<String, dynamic>;
      final restoredPpe = DriverPpeChecklist.fromJson(ppeJson);
      expect(restoredPpe.safetyShoes, isTrue);
      expect(restoredPpe.helmetGlasses, isFalse);
    });

    testWidgets('Checklist11PointsWidget updates UI state when category toggled to FAIL',
        (WidgetTester tester) async {
      final report = VehicleInspectionReport.initStandard(inspectorNip: 'SEC-007');
      bool callbackFired = false;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: Checklist11PointsWidget(
                report: report,
                isZone1: false,
                onChanged: (_) {
                  callbackFired = true;
                },
              ),
            ),
          ),
        ),
      );

      // Awalnya PASS
      expect(find.text('Kelaikan Fisik: MEMENUHI SYARAT (PASS)'), findsOneWidget);

      // Cari tombol FAIL pertama dan tap
      final failChipFinder = find.widgetWithText(ChoiceChip, 'FAIL').first;
      await tester.tap(failChipFinder);
      await tester.pump();

      expect(callbackFired, isTrue);
      expect(find.text('Kelaikan Fisik: DITOLAK (FAIL)'), findsOneWidget);
    });
  });
}
