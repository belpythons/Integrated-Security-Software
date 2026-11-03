import 'package:flutter_test/flutter_test.dart';
import 'package:ses_masuk_barang/core/app_constants.dart';
import 'package:ses_masuk_barang/models/po_do_item.dart';
import 'package:ses_masuk_barang/models/vehicle_item.dart';
import 'package:ses_masuk_barang/models/driver_item.dart';
import 'package:ses_masuk_barang/models/permit_header.dart';

void main() {
  group('Stage 04: Spesifikasi Form Input & Validasi Data', () {
    final now = DateTime(2026, 11, 3);

    test('Validasi PO & DO: Wajib Softcopy Terpisah & Masking Price', () {
      final poDo = PoDoItem(
        permitPoId: 101,
        requestNo: 'REQ-MAT-20261103-0001',
        poNumber: 'PO-PTB-2026-9901',
        poDescription: 'Pipa Seamless Stainless Steel 4 Inch',
        doNumber: 'DO-VND-8812',
        doDate: DateTime(2026, 11, 3),
        rawUnitPrice: 12500000.0,
        poFileId: 501,
        doFileId: 502,
      );

      expect(poDo.validate(), isEmpty);
      expect(poDo.displayPrice, '*** CONFIDENTIAL / PRICE MASKED ***');

      // Validasi serialisasi masking
      final json = poDo.toJson();
      expect(json['unit_price'], '*** CONFIDENTIAL / PRICE MASKED ***');
      expect(json['po_file_id'], 501);
      expect(json['do_file_id'], 502);
    });

    test('Validasi Kendaraan: Pajak Mati & Syarat KIR Truk', () {
      final validTruck = VehicleItem(
        vehicleItemId: 'VEH-01',
        requestNo: 'REQ-MAT-20261103-0001',
        licensePlateNo: 'KT 8123 BA',
        vehicleCategory: 'TRUCK',
        vehicleBrand: 'Mitsubishi Fuso',
        stnk5YrExpiry: DateTime(2027, 5, 20),
        stnkTaxExpiry: DateTime(2026, 11, 20),
        stnkFileId: 601,
        isKirRequired: true,
        kirExpiryDate: DateTime(2026, 12, 1),
        kirFileId: 602,
      );
      expect(validTruck.validate(referenceDate: now), isEmpty);

      // Truk dengan pajak tahunan mati
      final expiredTaxTruck = VehicleItem(
        vehicleItemId: 'VEH-02',
        requestNo: 'REQ-MAT-20261103-0001',
        licensePlateNo: 'KT 8123 BA',
        vehicleCategory: 'TRUCK',
        vehicleBrand: 'Mitsubishi Fuso',
        stnk5YrExpiry: DateTime(2027, 5, 20),
        stnkTaxExpiry: DateTime(2026, 10, 1), // Mati
        stnkFileId: 601,
        isKirRequired: true,
        kirExpiryDate: DateTime(2026, 12, 1),
        kirFileId: 602,
      );
      expect(expiredTaxTruck.validate(referenceDate: now).any((e) => e.contains('Pajak')), isTrue);
    });

    test('Validasi Pengemudi: Syarat SIM & Pembedaan KKB vs Eksternal', () {
      final externalDriver = DriverItem(
        driverItemId: 'DRV-01',
        requestNo: 'REQ-MAT-20261103-0001',
        personnelRole: 'DRIVER',
        driverOriginType: 'EXTERNAL_NON_KKB',
        nikKtp: '6474012304990001',
        driverName: 'Samsul Arifin',
        idCardFileId: 701,
        licenseType: 'SIM_B1',
        licenseNo: '9912-8812-0001',
        simExpiryDate: DateTime(2027, 1, 1),
        simFileId: 702,
      );
      expect(externalDriver.validate(referenceDate: now), isEmpty);

      // Sopir eksternal dengan NIK tidak 16 digit
      final invalidNikDriver = DriverItem(
        driverItemId: 'DRV-02',
        requestNo: 'REQ-MAT-20261103-0001',
        personnelRole: 'DRIVER',
        driverOriginType: 'EXTERNAL_NON_KKB',
        nikKtp: '12345', // Kurang dari 16 digit
        driverName: 'Joko',
        idCardFileId: 701,
        licenseType: 'SIM_A',
        licenseNo: '112233',
        simExpiryDate: DateTime(2027, 1, 1),
        simFileId: 702,
      );
      expect(invalidNikDriver.validate(referenceDate: now).any((e) => e.contains('16 digit')), isTrue);
    });

    test('Validasi Header: Aturan No-Backdate & Mandatory Linkage Izin Keluar', () {
      final exitPermitWithoutParent = MaterialPermitHeader(
        requestNo: 'REQ-MAT-20261103-0002',
        subModuleType: SubModuleType.exit,
        permitBasis: PermitBasis.ca,
        refDocNo: 'CA-2026-001',
        vendorName: 'PT Mandiri Sukses',
        targetDeptId: 'SEC',
        destinationZone: DestinationZone.zone2,
        specificLocation: 'Gudang 3',
        estArrivalDate: DateTime(2026, 11, 4),
        memoFileId: 801,
        // parentEntryReqNo sengaja null
      );
      final errors = exitPermitWithoutParent.validate(referenceDate: now);
      expect(errors.any((e) => e.contains('Mandatory Linkage')), isTrue);
    });
  });
}

