import 'package:flutter_test/flutter_test.dart';
import 'package:ses_masuk_barang/core/app_constants.dart';

void main() {
  group('Stage 01: Ruang Lingkup & Sub-Modul Domain Test', () {
    test('Verifikasi 3 Sub-Modul Material terdefinisi sesuai spesifikasi', () {
      expect(SubModuleType.entry.code, 'ENTRY');
      expect(SubModuleType.exit.code, 'EXIT');
      expect(SubModuleType.gatePass.code, 'GATE_PASS');

      expect(SubModuleType.fromCode('ENTRY'), SubModuleType.entry);
      expect(SubModuleType.fromCode('EXIT'), SubModuleType.exit);
      expect(SubModuleType.fromCode('GATE_PASS'), SubModuleType.gatePass);
    });

    test('Verifikasi Dasar Acuan Kontrak (PO, CA, Fasilitas, Internal Asset)', () {
      expect(PermitBasis.po.code, 'PO');
      expect(PermitBasis.ca.code, 'CA');
      expect(PermitBasis.facilityMemo.code, 'FACILITY_MEMO');
      expect(PermitBasis.internalAsset.code, 'INTERNAL_ASSET');

      expect(PermitBasis.fromCode('PO'), PermitBasis.po);
      expect(PermitBasis.fromCode('CA'), PermitBasis.ca);
    });

    test('Verifikasi Aturan Kepatuhan APD Berdasarkan Zona Tujuan Kilang', () {
      final zone1 = DestinationZone.zone1;
      expect(zone1.requiresSafetyShoes, isTrue);
      expect(zone1.requiresCoverall, isTrue, reason: 'Zone 1 Process Area wajib coverall');

      final zone2 = DestinationZone.zone2;
      expect(zone2.requiresSafetyShoes, isTrue);
      expect(zone2.requiresCoverall, isFalse, reason: 'Zone 2 Kilang/Warehouse cukup baju berkerah');

      final gedungPutih = DestinationZone.gedungPutih;
      expect(gedungPutih.requiresClosedShoes, isTrue);
      expect(gedungPutih.requiresSafetyShoes, isFalse, reason: 'Gedung Putih bebas dari safety shoes berat');
    });

    test('Verifikasi Status Siklus Kunjungan & Dokumen Mandatory', () {
      expect(VisitCycleStatus.checkedOut.code, 'CHECKED_OUT');
      expect(ScreeningResult.bypassed.code, 'BYPASSED');
      expect(MandatoryDocType.softcopyDo.isAlwaysMandatory, isTrue);
    });
  });
}

