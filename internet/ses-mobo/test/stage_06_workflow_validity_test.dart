import 'package:flutter_test/flutter_test.dart';
import 'package:ses_masuk_barang/core/app_constants.dart';
import 'package:ses_masuk_barang/core/validity_engine.dart';
import 'package:ses_masuk_barang/models/vehicle_item.dart';
import 'package:ses_masuk_barang/models/driver_item.dart';

void main() {
  group('Stage 06: Workflow Approval & Validity Engine Test', () {
    final baseSysdate = DateTime(2026, 11, 3);

    test('PO Workflow routing must NOT contain Procurement Specialist', () {
      final poSteps = WorkflowRoutingEngine.resolveApprovalChain(
        subModule: SubModuleType.entry,
        basis: PermitBasis.po,
      );

      final roles = poSteps.map((s) => s.roleName).toList();
      expect(roles, contains('Manager User'));
      expect(roles, contains('Admin Security'));
      expect(roles, contains('Manager Security'));
      expect(roles, isNot(contains('Procurement Specialist')));
    });

    test('CA Workflow routing MUST contain Procurement Specialist', () {
      final caSteps = WorkflowRoutingEngine.resolveApprovalChain(
        subModule: SubModuleType.entry,
        basis: PermitBasis.ca,
        includeSupervisor: true,
      );

      final roles = caSteps.map((s) => s.roleName).toList();
      expect(roles.first, 'Supervisor User');
      expect(roles, contains('Manager User'));
      expect(roles, contains('Procurement Specialist'));
      expect(roles, contains('Admin Security'));
      expect(roles, contains('Manager Security'));
    });

    test('Validity Engine: Valid From equals max(sysdate, estArrivalDate)', () {
      // Skenario A: Kedatangan besok
      final arrivalTomorrow = baseSysdate.add(const Duration(days: 1));
      final validFromA = ValidityEngine.calculateValidFrom(
        sysdate: baseSysdate,
        estArrivalDate: arrivalTomorrow,
      );
      expect(validFromA, arrivalTomorrow);

      // Skenario B: Estimasi tanggal lampau (harus terkunci ke sysdate)
      final arrivalPast = baseSysdate.subtract(const Duration(days: 2));
      final validFromB = ValidityEngine.calculateValidFrom(
        sysdate: baseSysdate,
        estArrivalDate: arrivalPast,
      );
      expect(validFromB, baseSysdate);
    });

    test('Validity Engine: PO capped at 3 days if all documents valid long term', () {
      final validFrom = baseSysdate;
      final vehicles = [
        VehicleItem(
          vehicleItemId: 'VH-01',
          requestNo: 'REQ-01',
          licensePlateNo: 'KT 8821 NA',
          vehicleCategory: 'TRUCK',
          vehicleBrand: 'Hino',
          stnk5YrExpiry: baseSysdate.add(const Duration(days: 100)),
          stnkTaxExpiry: baseSysdate.add(const Duration(days: 100)),
          stnkFileId: 101,
        ),
      ];
      final drivers = [
        DriverItem(
          driverItemId: 'DR-01',
          requestNo: 'REQ-01',
          personnelRole: 'DRIVER',
          driverOriginType: 'EXTERNAL_NON_KKB',
          driverName: 'Pak Joko',
          idCardFileId: 201,
          simExpiryDate: baseSysdate.add(const Duration(days: 200)),
        ),
      ];

      final validTo = ValidityEngine.calculateRecommendedValidTo(
        basis: PermitBasis.po,
        validFrom: validFrom,
        vehicles: vehicles,
        drivers: drivers,
      );

      // Harusnya tepat 3 hari ke depan
      expect(validTo, validFrom.add(const Duration(days: 3)));
    });

    test('Validity Engine: CA capped at 14 days or earliest document expiry', () {
      final validFrom = baseSysdate;
      final vehicles = [
        VehicleItem(
          vehicleItemId: 'VH-02',
          requestNo: 'REQ-02',
          licensePlateNo: 'KT 9000 CA',
          vehicleCategory: 'TRUCK',
          vehicleBrand: 'Mitsubishi',
          stnk5YrExpiry: baseSysdate.add(const Duration(days: 100)),
          stnkTaxExpiry: baseSysdate.add(const Duration(days: 100)),
          kirExpiryDate: baseSysdate.add(const Duration(days: 5)), // KIR hanya 5 hari!
          stnkFileId: 102,
        ),
      ];
      final drivers = [
        DriverItem(
          driverItemId: 'DR-02',
          requestNo: 'REQ-02',
          personnelRole: 'DRIVER',
          driverOriginType: 'EXTERNAL_NON_KKB',
          driverName: 'Pak Budi',
          idCardFileId: 202,
          simExpiryDate: baseSysdate.add(const Duration(days: 30)),
        ),
      ];

      final validTo = ValidityEngine.calculateRecommendedValidTo(
        basis: PermitBasis.ca,
        validFrom: validFrom,
        vehicles: vehicles,
        drivers: drivers,
      );

      // Karena KIR habis dalam 5 hari, Recommended Valid To harus 5 hari, bukan 14 hari
      expect(validTo, validFrom.add(const Duration(days: 5)));
    });

    test('Admin Adjustment: shortens valid date is allowed, extending over cap is rejected', () {
      final validFrom = baseSysdate;
      final vehicles = [
        VehicleItem(
          vehicleItemId: 'VH-03',
          requestNo: 'REQ-03',
          licensePlateNo: 'KT 1234 XY',
          vehicleCategory: 'PICKUP',
          vehicleBrand: 'Toyota',
          stnk5YrExpiry: baseSysdate.add(const Duration(days: 30)),
          stnkTaxExpiry: baseSysdate.add(const Duration(days: 30)),
          stnkFileId: 103,
        ),
      ];
      final drivers = [
        DriverItem(
          driverItemId: 'DR-03',
          requestNo: 'REQ-03',
          personnelRole: 'DRIVER',
          driverOriginType: 'EXTERNAL_NON_KKB',
          driverName: 'Sopir A',
          idCardFileId: 203,
          simExpiryDate: baseSysdate.add(const Duration(days: 30)),
        ),
      ];

      // Admin set ke 1 hari (diperpendek) -> VALID
      final res1 = ValidityEngine.validateAdminAdjustment(
        basis: PermitBasis.po,
        validFrom: validFrom,
        proposedValidTo: validFrom.add(const Duration(days: 1)),
        vehicles: vehicles,
        drivers: drivers,
      );
      expect(res1.isValid, isTrue);

      // Admin set ke 5 hari untuk PO -> DITOLAK (max 3 hari)
      final res2 = ValidityEngine.validateAdminAdjustment(
        basis: PermitBasis.po,
        validFrom: validFrom,
        proposedValidTo: validFrom.add(const Duration(days: 5)),
        vehicles: vehicles,
        drivers: drivers,
      );
      expect(res2.isValid, isFalse);
      expect(res2.errorMessage, contains('melampaui batas maksimum sistem'));

      // Admin set tanggal sebelum Valid From -> DITOLAK
      final res3 = ValidityEngine.validateAdminAdjustment(
        basis: PermitBasis.po,
        validFrom: validFrom,
        proposedValidTo: validFrom.subtract(const Duration(days: 1)),
        vehicles: vehicles,
        drivers: drivers,
      );
      expect(res3.isValid, isFalse);
      expect(res3.errorMessage, contains('tidak boleh lebih awal'));
    });
  });
}
