import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ses_masuk_barang/api/api_config.dart';
import 'package:ses_masuk_barang/api/dto/api_response.dart';
import 'package:ses_masuk_barang/api/dto/posko_dtos.dart';
import 'package:ses_masuk_barang/core/app_colors.dart';
import 'package:ses_masuk_barang/core/qr_parser.dart';
import 'package:ses_masuk_barang/models/gate_app_models.dart';
import 'package:ses_masuk_barang/services/mock_permit_service.dart';
import 'package:ses_masuk_barang/services/permit_repository.dart';

void main() {
  group('SES Smart QrParser Tests (Document Web Integration)', () {
    test('Extracts requestNo from web verification link with localhost', () {
      const url = 'http://localhost:8082/verify/REQ-MAT-20261103-0121';
      expect(QrParser.parse(url), equals('REQ-MAT-20261103-0121'));
      expect(QrParser.isSesPermit(url), isTrue);
    });

    test('Extracts requestNo from production domain with query parameters', () {
      const url = 'https://ses.badaklng.co.id/verify/REQ-MAT-20261103-0121?token=xyz123#view';
      expect(QrParser.parse(url), equals('REQ-MAT-20261103-0121'));
      expect(QrParser.isSesPermit(url), isTrue);
    });

    test('Preserves raw REQ-MAT- token intact', () {
      const raw = 'REQ-MAT-20261104-0005';
      expect(QrParser.parse(raw), equals('REQ-MAT-20261104-0005'));
      expect(QrParser.isSesPermit(raw), isTrue);
    });

    test('Preserves IMB prototype token intact', () {
      const raw = 'IMB-2026-000121';
      expect(QrParser.parse(raw), equals('IMB-2026-000121'));
      expect(QrParser.isSesPermit(raw), isTrue);
    });

    test('Finds permit directly using full verification URL', () async {
      final repository = PermitRepository();
      const webQrUrl = 'http://localhost:8082/verify/REQ-MAT-20261103-0121';
      final permit = await repository.findPermit(webQrUrl);
      expect(permit, isNotNull);
      expect(permit!.vendor, equals('PT Sinar Logistik Abadi'));
    });
  });

  group('SES Gate-Aware Operations & 3 Gates Configuration', () {
    late MockPermitService mock;

    setUp(() {
      mock = MockPermitService();
    });

    test('Configured gates match exactly 3 specified gates', () {
      expect(mock.gates, equals([
        'KPBR Gate',
        'SD Gate',
        'Warehouse Gate (Zona 2)',
      ]));
    });

    test('Warehouse Gate Zona 2 enforces warehouse destination compliance', () {
      final whPermit = mock.getRequest('REQ-MAT-20261103-0121');
      expect(whPermit, isNotNull);
      expect(mock.isGateZoneCompliant('Warehouse Gate (Zona 2)', whPermit!), isTrue);

      final nonWhPermit = GatePermitRequest(
        id: 'REQ-MAT-OFFICE',
        status: 'Approved',
        vendor: 'PT Badak Office',
        startDate: DateTime.now(),
        endDate: DateTime.now().add(const Duration(days: 1)),
        poNumber: 'PO-OFFICE',
        doNumber: 'DO-01',
        plantDestination: 'Gedung Putih · Office Central',
        driverIds: ['DRV-0001'],
        vehicleIds: ['VEH-0001'],
        items: [],
      );
      expect(mock.isGateZoneCompliant('Warehouse Gate (Zona 2)', nonWhPermit), isFalse);
      expect(mock.isGateZoneCompliant('KPBR Gate', nonWhPermit), isTrue);
    });

    test('insideForGate filters on-site vehicles specifically by gate', () {
      final all = mock.inside();
      final kpbrInside = mock.insideForGate('KPBR Gate');
      final whInside = mock.insideForGate('Warehouse Gate (Zona 2)');

      expect(all.isNotEmpty, isTrue);
      expect(kpbrInside.length + whInside.length <= all.length, isTrue);
      for (final log in kpbrInside) {
        expect(log.gate, equals('KPBR Gate'));
      }
    });

    test('SES Mono Blue color tokens match theme-ses-modern.css specification', () {
      expect(AppColors.brand, equals(const Color(0xFF0064D9)));
      expect(AppColors.shell, equals(const Color(0xFF1D2D3E)));
      expect(AppColors.bg, equals(const Color(0xFFF5F6F7)));
      expect(AppColors.line, equals(const Color(0xFFD9DEE3)));
      expect(AppColors.brandSoft, equals(const Color(0xFFEBF4FF)));
    });
  });

  group('SES API DTO Contract Tests (Matching com.badaklng.ses.api.dto.*)', () {
    test('ApiResponse parses success, message, data, and timestamp correctly', () {
      final json = {
        'success': true,
        'message': 'Valid permit scan',
        'data': {
          'requestNo': 'REQ-MAT-20261103-0121',
          'vendorName': 'PT Sinar Logistik Abadi',
          'department': 'WH02 · Procurement Receiving',
          'poNumber': '4500012388',
          'poDescription': 'Material Delivery Contract',
          'unitPriceMasked': '*** CONFIDENTIAL / PRICE MASKED ***',
          'validFrom': '2026-11-03',
          'validTo': '2026-11-04',
          'registeredVehicles': ['B 9031 TRQ'],
          'registeredDrivers': ['Rudi Hartono'],
          'screeningStatus': 'APPROVED',
          'hasKkbSticker': false,
          'currentStatus': 'APPROVED',
        },
        'timestamp': 1730620000000,
      };

      final resp = ApiResponse<PoskoScanResponseDto>.fromJson(
        json,
        (data) => PoskoScanResponseDto.fromJson(data as Map<String, dynamic>),
      );

      expect(resp.success, isTrue);
      expect(resp.message, equals('Valid permit scan'));
      expect(resp.data, isNotNull);
      expect(resp.data!.requestNo, equals('REQ-MAT-20261103-0121'));
      expect(resp.data!.unitPriceMasked, equals('*** CONFIDENTIAL / PRICE MASKED ***'));
      expect(resp.data!.registeredVehicles, contains('B 9031 TRQ'));
      expect(resp.data!.registeredDrivers, contains('Rudi Hartono'));
      expect(resp.data!.hasKkbSticker, isFalse);
    });

    test('PoskoCheckInRequestDto serializes required fields strictly', () {
      const dto = PoskoCheckInRequestDto(
        requestNo: 'REQ-MAT-20261103-0121',
        selectedVehiclePlate: 'B 9031 TRQ',
        selectedDriverName: 'Rudi Hartono',
        visitorBadgeNo: 'V-082',
        ktpHeld: true,
        notes: 'Inspection Passed',
      );

      final map = dto.toJson();
      expect(map['requestNo'], equals('REQ-MAT-20261103-0121'));
      expect(map['selectedVehiclePlate'], equals('B 9031 TRQ'));
      expect(map['selectedDriverName'], equals('Rudi Hartono'));
      expect(map['visitorBadgeNo'], equals('V-082'));
      expect(map['ktpHeld'], isTrue);
      expect(map['notes'], equals('Inspection Passed'));
    });

    test('PoskoCheckOutRequestDto serializes return validation strictly', () {
      const dto = PoskoCheckOutRequestDto(
        requestNo: 'REQ-MAT-20261103-0121',
        selectedVehiclePlate: 'B 9031 TRQ',
        visitorBadgeReturned: true,
        ktpReturned: true,
        notes: 'Checked out cleanly',
      );

      final map = dto.toJson();
      expect(map['requestNo'], equals('REQ-MAT-20261103-0121'));
      expect(map['visitorBadgeReturned'], isTrue);
      expect(map['ktpReturned'], isTrue);
    });

    test('AuthResponseDto deserializes JWT token and roles', () {
      final json = {
        'token': 'jwt.mock.token.xyz',
        'tokenType': 'Bearer',
        'expiresInMs': 86400000,
        'username': 'dedi.k',
        'fullName': 'Dedi Kurniawan',
        'roles': ['ROLE_SECURITY_OFFICER', 'ROLE_USER'],
      };

      final auth = AuthResponseDto.fromJson(json);
      expect(auth.token, equals('jwt.mock.token.xyz'));
      expect(auth.username, equals('dedi.k'));
      expect(auth.roles, contains('ROLE_SECURITY_OFFICER'));
    });
  });

  group('SES Dual-Source PermitRepository Tests', () {
    late PermitRepository repository;

    setUp(() {
      repository = PermitRepository();
      ApiConfig.reset();
    });

    test('Default mode connects directly to SES backend without manual toggle', () {
      expect(repository.isOfflineMode, isFalse);
      expect(repository.baseUrl, equals(ApiConfig.defaultLocalhostUrl));
    });

    test('Can find permit with real SES request number REQ-MAT-20261103-0121', () async {
      final permit = await repository.findPermit('REQ-MAT-20261103-0121');
      expect(permit, isNotNull);
      expect(permit!.vendor, equals('PT Sinar Logistik Abadi'));
      expect(permit.unitPriceMasked, equals('*** CONFIDENTIAL / PRICE MASKED ***'));
      expect(permit.status, equals('Approved'));
    });

    test('Can find permit with alias IMB-2026-000121', () async {
      final permit = await repository.findPermit('IMB-2026-000121');
      expect(permit, isNotNull);
      expect(permit!.poNumber, equals('4500012388'));
    });

    test('Check-In commits with visitor badge and ktpHeld', () async {
      final log = await repository.checkIn(
        requestId: 'REQ-MAT-20261103-0121',
        driverId: 'DRV-0004',
        vehicleId: 'VEH-0004',
        isFit: true,
        fastReentry: false,
        visitorBadgeNo: 'V-099',
        ktpHeld: true,
      );

      expect(log.type, equals(GateLogType.checkIn));
      expect(log.visitorBadgeNo, equals('V-099'));
      expect(log.ktpHeld, isTrue);
      expect(log.result, equals('Laik'));
    });

    test('Check-Out commits with visitorBadgeReturned and ktpReturned', () async {
      final outLog = await repository.checkOut(
        requestId: 'REQ-MAT-20261103-0121',
        driverId: 'DRV-0004',
        vehicleId: 'VEH-0004',
        visitorBadgeReturned: true,
        ktpReturned: true,
      );

      expect(outLog.type, equals(GateLogType.checkOut));
      expect(outLog.visitorBadgeReturned, isTrue);
      expect(outLog.ktpReturned, isTrue);
    });

    test('MockPermitService backward compatibility remains intact', () {
      final mock = MockPermitService();
      expect(mock.users.isNotEmpty, isTrue);
      expect(mock.gates.isNotEmpty, isTrue);
      expect(mock.checklist.length, equals(10));
    });
  });

  group('Bahasa Indonesia Localization & Taxonomy Verification', () {
    test('Checklist categories are localized to Indonesian', () {
      final mock = MockPermitService();
      final catNames = mock.checklist.map((c) => c.categoryName).toSet();
      expect(catNames, containsAll(['Kendaraan', 'Keselamatan', 'Muatan', 'Pengemudi']));
    });

    test('GateLogType typeLabels match Indonesian operational terms', () {
      expect(GateActivityLog(
        id: 1,
        timestamp: DateTime.now(),
        requestId: 'REQ-1',
        driverId: 'D-1',
        vehicleId: 'V-1',
        type: GateLogType.checkIn,
        gate: 'KPBR Gate',
        officer: 'Dedi',
      ).typeLabel, equals('Check-in'));

      expect(GateActivityLog(
        id: 2,
        timestamp: DateTime.now(),
        requestId: 'REQ-1',
        driverId: 'D-1',
        vehicleId: 'V-1',
        type: GateLogType.checkOut,
        gate: 'KPBR Gate',
        officer: 'Dedi',
      ).typeLabel, equals('Check-out'));

      expect(GateActivityLog(
        id: 3,
        timestamp: DateTime.now(),
        requestId: 'REQ-1',
        driverId: 'D-1',
        vehicleId: 'V-1',
        type: GateLogType.rejected,
        gate: 'KPBR Gate',
        officer: 'Dedi',
      ).typeLabel, equals('Ditolak'));
    });

    test('PermitValidationState produces official Indonesian titles', () {
      final mock = MockPermitService();
      final validState = mock.permitState('REQ-MAT-20261103-0121');
      expect(validState.title, equals('Izin Aktif Hari Ini'));

      final notFoundState = mock.permitState('REQ-NON-EXISTENT');
      expect(notFoundState.title, equals('Izin Tidak Ditemukan'));
    });
  });
}
