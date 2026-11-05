import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../core/app_constants.dart';
import '../core/qr_parser.dart';
import '../models/permit_header.dart';
import '../models/po_do_item.dart';
import '../models/vehicle_item.dart';
import '../models/driver_item.dart';
import '../models/screening_log.dart';
import '../models/gate_app_models.dart';
import 'permit_service.dart';

class MockPermitService implements PermitService {
  // Existing models for backward compatibility
  final List<MaterialPermitHeader> _permits = [];
  final List<ScreeningLog> _logs = [];

  // Gate Security App dataset
  late List<GateSecurityUser> _users;
  late List<String> _gates;
  late Map<String, GateDriver> _drivers;
  late Map<String, GateVehicle> _vehicles;
  late Map<String, GatePermitRequest> _requests;
  late List<GateChecklistItem> _checklist;
  late List<GateActivityLog> _activityLogs;

  // Active Session State
  GateSecurityUser? currentUser;
  String? currentGate;

  static final MockPermitService _instance = MockPermitService._internal();
  factory MockPermitService() => _instance;

  MockPermitService._internal() {
    resetSampleData();
  }

  void resetSampleData() {
    _initBackwardCompatibleData();
    _initGateSecurityData();
  }

  void _initBackwardCompatibleData() {
    _permits.clear();
    _logs.clear();
    final now = DateTime.now();

    _permits.add(
      MaterialPermitHeader(
        requestNo: 'REQ-MAT-20261103-0001',
        subModuleType: SubModuleType.entry,
        permitBasis: PermitBasis.po,
        refDocNo: 'PO-778810',
        inputChannel: 'INTRANET',
        vendorName: 'PT Amalindo Utama',
        targetDeptId: 'D-WHS',
        destinationZone: DestinationZone.zone2,
        specificLocation: 'Warehouse Bongkar Muat Sekunder',
        estArrivalDate: now,
        validFrom: now.subtract(const Duration(days: 1)),
        validTo: now.add(const Duration(days: 3)),
        qrCodeToken: 'QR-TOKEN-AMALINDO-001',
        permitStatus: PermitStatus.approved,
        poDoItems: [
          PoDoItem(
            requestNo: 'REQ-MAT-20261103-0001',
            poNumber: 'PO-778810',
            poDescription: 'Pipa Seamless Carbon Steel 6 Inch',
            doNumber: 'DO-AMA-01',
            doDate: now,
            rawUnitPrice: 12500000,
            poFileId: 101,
            doFileId: 102,
          ),
          PoDoItem(
            requestNo: 'REQ-MAT-20261103-0001',
            poNumber: 'PO-778810',
            poDescription: 'Flange Blind ANSI Class 300',
            doNumber: 'DO-AMA-02',
            doDate: now,
            rawUnitPrice: 3200000,
            poFileId: 101,
            doFileId: 103,
          ),
        ],
        vehicleItems: [
          VehicleItem(
            vehicleItemId: 'VH-01',
            requestNo: 'REQ-MAT-20261103-0001',
            licensePlateNo: 'KT 8821 NA',
            vehicleCategory: 'TRUCK',
            vehicleBrand: 'Hino Ranger',
            stnk5YrExpiry: now.add(const Duration(days: 120)),
            stnkTaxExpiry: now.add(const Duration(days: 120)),
            kirExpiryDate: now.add(const Duration(days: 60)),
            stnkFileId: 201,
            hasActiveKkb: false,
            adminSkipInspection: false,
          ),
        ],
        driverItems: [
          DriverItem(
            driverItemId: 'DR-01',
            requestNo: 'REQ-MAT-20261103-0001',
            personnelRole: 'DRIVER',
            driverOriginType: 'EXTERNAL_NON_KKB',
            driverName: 'Pak Joko Susilo',
            nikKtp: '6471021980040001',
            idCardFileId: 301,
            licenseType: 'SIM_B2',
            licenseNo: 'SIM-99102-B2',
            simExpiryDate: now.add(const Duration(days: 300)),
          ),
        ],
      ),
    );

    _permits.add(
      MaterialPermitHeader(
        requestNo: 'REQ-MAT-20261103-0002',
        subModuleType: SubModuleType.entry,
        permitBasis: PermitBasis.ca,
        refDocNo: 'CA-2026-NGL-088',
        inputChannel: 'INTERNET',
        vendorName: 'PT Kilang Mandiri',
        targetDeptId: 'D-ENG',
        destinationZone: DestinationZone.zone1,
        specificLocation: 'Train F/G Process Unit',
        estArrivalDate: now,
        validFrom: now.subtract(const Duration(days: 2)),
        validTo: now.add(const Duration(days: 5)),
        qrCodeToken: 'QR-TOKEN-KILANGMANDIRI-002',
        permitStatus: PermitStatus.approved,
        poDoItems: [
          PoDoItem(
            requestNo: 'REQ-MAT-20261103-0002',
            poNumber: 'CA-REF-99',
            poDescription: 'Concrete Mix Super Strength K-400',
            doNumber: 'DO-KM-01',
            doDate: now,
            rawUnitPrice: 85000000,
            poFileId: 104,
            doFileId: 105,
          ),
        ],
        vehicleItems: [
          VehicleItem(
            vehicleItemId: 'VH-02',
            requestNo: 'REQ-MAT-20261103-0002',
            licensePlateNo: 'KT 9999 CA',
            vehicleCategory: 'MOLEN_MIXER',
            vehicleBrand: 'Mitsubishi Fuso',
            stnk5YrExpiry: now.add(const Duration(days: 200)),
            stnkTaxExpiry: now.add(const Duration(days: 200)),
            kirExpiryDate: now.add(const Duration(days: 150)),
            stnkFileId: 202,
            hasActiveKkb: true,
            adminSkipInspection: true,
          ),
        ],
        driverItems: [
          DriverItem(
            driverItemId: 'DR-02',
            requestNo: 'REQ-MAT-20261103-0002',
            personnelRole: 'DRIVER',
            driverOriginType: 'EXTERNAL_KKB',
            driverName: 'Pak Hartanto',
            nikKtp: '6471031985050002',
            idCardFileId: 302,
            licenseType: 'SIM_B2',
            licenseNo: 'SIM-88211-B2',
            simExpiryDate: now.add(const Duration(days: 180)),
          ),
        ],
      ),
    );

    _permits.add(
      MaterialPermitHeader(
        requestNo: 'REQ-MAT-20261103-0003',
        subModuleType: SubModuleType.entry,
        permitBasis: PermitBasis.facilityMemo,
        refDocNo: 'MEMO-BADAK-KANTIN-01',
        inputChannel: 'INTRANET',
        vendorName: 'CV Berkah Pangan Makmur',
        targetDeptId: 'D-HR-SERVICES',
        destinationZone: DestinationZone.gedungPutih,
        specificLocation: 'Kantin Pusat Badak Gedung Putih',
        estArrivalDate: now,
        validFrom: now,
        validTo: now.add(const Duration(days: 1)),
        qrCodeToken: 'QR-TOKEN-BERKAH-003',
        permitStatus: PermitStatus.approved,
        poDoItems: [
          PoDoItem(
            requestNo: 'REQ-MAT-20261103-0003',
            poNumber: 'MEMO-HR-88',
            poDescription: 'Bahan Makanan Kering & Beras Super 200 Sak',
            doNumber: 'DO-BP-01',
            doDate: now,
            rawUnitPrice: 14500000,
            poFileId: 106,
            doFileId: 107,
          ),
        ],
        vehicleItems: [
          VehicleItem(
            vehicleItemId: 'VH-03',
            requestNo: 'REQ-MAT-20261103-0003',
            licensePlateNo: 'KT 8123 BA',
            vehicleCategory: 'PICKUP',
            vehicleBrand: 'Suzuki Carry',
            stnk5YrExpiry: now.add(const Duration(days: 90)),
            stnkTaxExpiry: now.add(const Duration(days: 90)),
            kirExpiryDate: now.add(const Duration(days: 45)),
            stnkFileId: 203,
            hasActiveKkb: false,
            adminSkipInspection: false,
          ),
        ],
        driverItems: [
          DriverItem(
            driverItemId: 'DR-03',
            requestNo: 'REQ-MAT-20261103-0003',
            personnelRole: 'DRIVER',
            driverOriginType: 'EXTERNAL_NON_KKB',
            driverName: 'Pak Wahyu',
            nikKtp: '6471011992010003',
            idCardFileId: 303,
            licenseType: 'SIM_A',
            licenseNo: 'SIM-77192-A',
            simExpiryDate: now.add(const Duration(days: 250)),
          ),
        ],
      ),
    );
  }

  void _initGateSecurityData() {
    final now = DateTime.now();
    DateTime d(int days) => DateTime(now.year, now.month, now.day + days);

    _users = [
      GateSecurityUser(
        username: 'dedi.k',
        pin: '123456',
        name: 'Dedi Kurniawan',
        employeeId: 'EMP-20211',
        defaultGate: 'KPBR Gate',
      ),
      GateSecurityUser(
        username: 'slamet.r',
        pin: '123456',
        name: 'Slamet Riyadi',
        employeeId: 'EMP-20234',
        defaultGate: 'SD Gate',
      ),
      GateSecurityUser(
        username: 'sec.badak',
        pin: '123456',
        name: 'Petugas Posko Lapangan Bola',
        employeeId: 'SEC-88210',
        defaultGate: 'Warehouse Gate (Zona 2)',
      ),
    ];

    _gates = [
      'KPBR Gate',
      'SD Gate',
      'Warehouse Gate (Zona 2)',
    ];

    _drivers = {
      'DRV-0001': GateDriver(
        id: 'DRV-0001',
        name: 'Budi Santoso',
        nik: '3175031204870001',
        simNumber: '8710-1204-000123',
        simType: 'B1 Umum',
        simValidUntil: d(410),
      ),
      'DRV-0002': GateDriver(
        id: 'DRV-0002',
        name: 'Agus Prasetyo',
        nik: '3201150809900004',
        simNumber: '9008-0809-004512',
        simType: 'B2 Umum',
        simValidUntil: d(20),
      ),
      'DRV-0004': GateDriver(
        id: 'DRV-0004',
        name: 'Rudi Hartono',
        nik: '3174070511850003',
        simNumber: '8505-0511-001877',
        simType: 'B1 Umum',
        simValidUntil: d(700),
      ),
      'DRV-0005': GateDriver(
        id: 'DRV-0005',
        name: 'Pak Joko Susilo',
        nik: '6471021980040001',
        simNumber: '9910-2198-000112',
        simType: 'B2 Umum',
        simValidUntil: d(300),
      ),
    };

    _vehicles = {
      'VEH-0001': GateVehicle(
        id: 'VEH-0001',
        plate: 'B 9123 KXT',
        type: 'CDD',
        brand: 'Mitsubishi Colt Diesel FE 74',
        taxValidUntil: d(150),
        stnkValidUntil: d(900),
        kirValidUntil: d(90),
      ),
      'VEH-0002': GateVehicle(
        id: 'VEH-0002',
        plate: 'B 9876 FGH',
        type: 'CDE',
        brand: 'Isuzu Elf NMR 71',
        taxValidUntil: d(25),
        stnkValidUntil: d(400),
        kirValidUntil: d(60),
      ),
      'VEH-0004': GateVehicle(
        id: 'VEH-0004',
        plate: 'B 9031 TRQ',
        type: 'CDE',
        brand: 'Toyota Dyna 130 HT',
        taxValidUntil: d(260),
        stnkValidUntil: d(1200),
        kirValidUntil: d(140),
      ),
      'VEH-0005': GateVehicle(
        id: 'VEH-0005',
        plate: 'KT 8821 NA',
        type: 'Fuso',
        brand: 'Hino Ranger 500',
        taxValidUntil: d(120),
        stnkValidUntil: d(120),
        kirValidUntil: d(60),
      ),
    };

    _requests = {
      'IMB-2026-000123': GatePermitRequest(
        id: 'IMB-2026-000123',
        status: 'Approved',
        vendor: 'PT Sinar Logistik Abadi',
        startDate: d(-1),
        endDate: d(2),
        poNumber: '4500012345',
        doNumber: '8000056001, 8000056002',
        plantDestination: 'WH01 · Main Warehouse',
        driverIds: ['DRV-0001', 'DRV-0002'],
        vehicleIds: ['VEH-0001', 'VEH-0002'],
        items: [
          GateCargoItem(name: 'Ball Bearing SKF 6205-2RS', qty: 200, uom: 'PC'),
          GateCargoItem(name: 'Hydraulic Oil Shell Tellus S2 M46 209L', qty: 12, uom: 'DR'),
          GateCargoItem(name: 'V-Belt Bando B-52', qty: 50, uom: 'PC'),
        ],
      ),
      'IMB-2026-000121': GatePermitRequest(
        id: 'IMB-2026-000121',
        status: 'Approved',
        vendor: 'PT Sinar Logistik Abadi',
        startDate: d(0),
        endDate: d(1),
        poNumber: '4500012388',
        doNumber: '8000056110',
        plantDestination: 'WH02 · Procurement Receiving',
        driverIds: ['DRV-0004'],
        vehicleIds: ['VEH-0004'],
        items: [
          GateCargoItem(name: 'Safety Helmet MSA V-Gard, white', qty: 150, uom: 'PC'),
          GateCargoItem(name: 'Safety Shoes Cheetah 7012H, size 42', qty: 60, uom: 'PR'),
        ],
      ),
      'IMB-2026-000124': GatePermitRequest(
        id: 'IMB-2026-000124',
        status: 'In Approval',
        vendor: 'PT Sinar Logistik Abadi',
        startDate: d(3),
        endDate: d(3),
        poNumber: '4500012990',
        doNumber: '8000056150',
        plantDestination: 'WH01 · Main Warehouse',
        driverIds: ['DRV-0001'],
        vehicleIds: ['VEH-0001'],
        items: [
          GateCargoItem(name: 'MCB Schneider 3P 32A', qty: 40, uom: 'PC'),
          GateCargoItem(name: 'Cable NYY 4x16mm²', qty: 500, uom: 'M'),
        ],
      ),
      'IMB-2026-000125': GatePermitRequest(
        id: 'IMB-2026-000125',
        status: 'Approved',
        vendor: 'PT Sinar Logistik Abadi',
        startDate: d(4),
        endDate: d(6),
        poNumber: '4500013002',
        doNumber: '–',
        plantDestination: 'WH02 · Procurement Receiving',
        driverIds: ['DRV-0004'],
        vehicleIds: ['VEH-0004'],
        items: [
          GateCargoItem(name: 'Copy Paper A4 80gsm', qty: 100, uom: 'RM'),
          GateCargoItem(name: 'Toner HP 26A CF226A', qty: 12, uom: 'PC'),
        ],
      ),
      'IMB-2026-000118': GatePermitRequest(
        id: 'IMB-2026-000118',
        status: 'Approved',
        vendor: 'PT Sinar Logistik Abadi',
        startDate: d(-12),
        endDate: d(-10),
        poNumber: '4500012345',
        doNumber: '8000056001',
        plantDestination: 'WH01 · Main Warehouse',
        driverIds: ['DRV-0002'],
        vehicleIds: ['VEH-0002'],
        items: [
          GateCargoItem(name: 'Ball Bearing SKF 6205-2RS', qty: 120, uom: 'PC'),
        ],
      ),
      'REQ-MAT-20261103-0121': GatePermitRequest(
        id: 'REQ-MAT-20261103-0121',
        status: 'Approved',
        vendor: 'PT Sinar Logistik Abadi',
        startDate: d(0),
        endDate: d(1),
        poNumber: '4500012388',
        doNumber: '8000056110',
        plantDestination: 'WH02 · Procurement Receiving',
        driverIds: ['DRV-0004'],
        vehicleIds: ['VEH-0004'],
        items: [
          GateCargoItem(name: 'Safety Helmet MSA V-Gard, white', qty: 150, uom: 'PC'),
          GateCargoItem(name: 'Safety Shoes Cheetah 7012H, size 42', qty: 60, uom: 'PR'),
        ],
      ),
      'REQ-MAT-20261103-0123': GatePermitRequest(
        id: 'REQ-MAT-20261103-0123',
        status: 'Approved',
        vendor: 'PT Sinar Logistik Abadi',
        startDate: d(-1),
        endDate: d(2),
        poNumber: '4500012345',
        doNumber: '8000056001, 8000056002',
        plantDestination: 'WH01 · Main Warehouse',
        driverIds: ['DRV-0001', 'DRV-0002'],
        vehicleIds: ['VEH-0001', 'VEH-0002'],
        items: [
          GateCargoItem(name: 'Ball Bearing SKF 6205-2RS', qty: 200, uom: 'PC'),
          GateCargoItem(name: 'Hydraulic Oil Shell Tellus S2 M46 209L', qty: 12, uom: 'DR'),
        ],
      ),
      'REQ-MAT-20261103-0001': GatePermitRequest(
        id: 'REQ-MAT-20261103-0001',
        status: 'Approved',
        vendor: 'PT Amalindo Utama',
        startDate: d(-1),
        endDate: d(3),
        poNumber: 'PO-778810',
        doNumber: 'DO-AMA-01, DO-AMA-02',
        plantDestination: 'Zone 2 (Kilang / Warehouse)',
        driverIds: ['DRV-0005'],
        vehicleIds: ['VEH-0005'],
        items: [
          GateCargoItem(name: 'Pipa Seamless Carbon Steel 6 Inch', qty: 50, uom: 'LN'),
          GateCargoItem(name: 'Flange Blind ANSI Class 300', qty: 25, uom: 'PC'),
        ],
      ),
    };

    _checklist = [
      GateChecklistItem(id: 1, name: 'Kondisi ban & alur roda (Tires & tread)', category: ChecklistCategory.vehicle, mandatory: true, photoRequired: true),
      GateChecklistItem(id: 2, name: 'Rem & rem tangan (Brakes & hand brake)', category: ChecklistCategory.vehicle, mandatory: true, photoRequired: false),
      GateChecklistItem(id: 3, name: 'Lampu utama, rem & sein (Lights)', category: ChecklistCategory.vehicle, mandatory: true, photoRequired: false),
      GateChecklistItem(id: 4, name: 'Kebocoran oli / BBM (Oil/fuel leakage)', category: ChecklistCategory.vehicle, mandatory: false, photoRequired: true),
      GateChecklistItem(id: 5, name: 'APAR aktif & siap pakai (Fire extinguisher)', category: ChecklistCategory.safety, mandatory: true, photoRequired: true),
      GateChecklistItem(id: 6, name: 'Kotak P3K (First aid kit)', category: ChecklistCategory.safety, mandatory: false, photoRequired: false),
      GateChecklistItem(id: 7, name: 'Ganjal roda kayu/karet (Wheel chocks)', category: ChecklistCategory.safety, mandatory: true, photoRequired: true),
      GateChecklistItem(id: 8, name: 'Muatan terikat & tertutup terpal (Cargo secured)', category: ChecklistCategory.cargo, mandatory: true, photoRequired: true),
      GateChecklistItem(id: 9, name: 'APD Pengemudi (Helm, rompi, safety shoes)', category: ChecklistCategory.driver, mandatory: true, photoRequired: true),
      GateChecklistItem(id: 10, name: 'Kondisi fisik pengemudi laik kerja (Fit to work)', category: ChecklistCategory.driver, mandatory: true, photoRequired: false),
    ];

    // Seed logs for simulation
    final todayStr = DateTime(now.year, now.month, now.day);
    _activityLogs = [
      GateActivityLog(
        id: 3,
        timestamp: todayStr.add(const Duration(hours: 6, minutes: 55)),
        requestId: 'IMB-2026-000123',
        driverId: 'DRV-0002',
        vehicleId: 'VEH-0002',
        type: GateLogType.rejected,
        gate: 'KPBR Gate',
        officer: 'Dedi Kurniawan',
        result: 'Tidak Laik',
        summary: 'Ban: Ada, Tidak Laik · Ganjal roda: Tidak Ada',
        remarks: 'Ban belakang gundul di bawah batas aman dan tidak ada ganjal roda. Ganti ban sebelum masuk kembali.',
      ),
      GateActivityLog(
        id: 4,
        timestamp: todayStr.add(const Duration(hours: 7, minutes: 40)),
        requestId: 'IMB-2026-000123',
        driverId: 'DRV-0001',
        vehicleId: 'VEH-0001',
        type: GateLogType.checkIn,
        gate: 'KPBR Gate',
        officer: 'Dedi Kurniawan',
        result: 'Laik',
        summary: '10/10 Ada & Laik',
      ),
      GateActivityLog(
        id: 5,
        timestamp: todayStr.add(const Duration(hours: 8, minutes: 5)),
        requestId: 'IMB-2026-000121',
        driverId: 'DRV-0004',
        vehicleId: 'VEH-0004',
        type: GateLogType.checkIn,
        gate: 'Warehouse Gate (Zona 2)',
        officer: 'Slamet Riyadi',
        result: 'Laik',
        summary: '9/10 · Kotak P3K: Ada, Tidak Laik',
      ),
      GateActivityLog(
        id: 6,
        timestamp: todayStr.add(const Duration(hours: 9, minutes: 15)),
        requestId: 'IMB-2026-000123',
        driverId: 'DRV-0001',
        vehicleId: 'VEH-0001',
        type: GateLogType.checkOut,
        gate: 'KPBR Gate',
        officer: 'Dedi Kurniawan',
      ),
    ];
  }

  // --- Getters & Queries ---
  List<GateSecurityUser> get users => _users;
  List<String> get gates => _gates;
  Map<String, GateDriver> get drivers => _drivers;
  Map<String, GateVehicle> get vehicles => _vehicles;
  Map<String, GatePermitRequest> get requests => _requests;
  List<GateChecklistItem> get checklist => _checklist;
  List<GateActivityLog> get activityLogs => List.unmodifiable(_activityLogs);

  GatePermitRequest? getRequest(String id) {
    final clean = QrParser.parse(id);
    if (_requests.containsKey(clean)) return _requests[clean];
    for (final entry in _requests.entries) {
      if (entry.key == clean) return entry.value;
      final kClean = entry.key.replaceAll(RegExp(r'[^0-9]'), '');
      final idClean = clean.replaceAll(RegExp(r'[^0-9]'), '');
      if (kClean.isNotEmpty && idClean.isNotEmpty && (kClean.endsWith(idClean) || idClean.endsWith(kClean))) {
        return entry.value;
      }
    }
    return null;
  }
  GateDriver? getDriver(String id) => _drivers[id];
  GateVehicle? getVehicle(String id) => _vehicles[id];

  /// List of vehicles currently inside the premises
  List<GateActivityLog> inside() {
    final Map<String, GateActivityLog> status = {};
    final sortedLogs = List<GateActivityLog>.from(_activityLogs)
      ..sort((a, b) => a.timestamp.compareTo(b.timestamp));

    for (final log in sortedLogs) {
      final key = '${log.requestId}|${log.driverId}|${log.vehicleId}';
      if (log.type == GateLogType.checkIn) {
        status[key] = log;
      } else if (log.type == GateLogType.checkOut) {
        status.remove(key);
      }
    }
    return status.values.toList();
  }

  /// List of vehicles currently inside the premises filtered by gate
  List<GateActivityLog> insideForGate(String? gate) {
    final list = inside();
    if (gate == null || gate.isEmpty) return list;
    return list.where((l) => l.gate == gate).toList();
  }

  /// Evaluates whether the permit destination is compliant for the assigned gate
  bool isGateZoneCompliant(String gate, GatePermitRequest req) {
    if (gate == 'Warehouse Gate (Zona 2)') {
      final dest = req.plantDestination.toLowerCase();
      return dest.contains('wh') ||
          dest.contains('warehouse') ||
          dest.contains('gudang') ||
          dest.contains('zone 2') ||
          dest.contains('zona 2');
    }
    return true;
  }

  GateActivityLog? insideBy(bool Function(GateActivityLog) predicate) {
    return inside().cast<GateActivityLog?>().firstWhere(
          (log) => log != null && predicate(log),
          orElse: () => null,
        );
  }

  /// Check if the driver and vehicle combination was already inspected and passed today
  GateActivityLog? inspectedToday(String req, String drv, String veh) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);

    final matches = _activityLogs.where((l) {
      return l.requestId == req &&
          l.driverId == drv &&
          l.vehicleId == veh &&
          l.type == GateLogType.checkIn &&
          l.result == 'Fit' &&
          !l.isFastReentry &&
          l.timestamp.isAfter(today);
    }).toList();

    return matches.isNotEmpty ? matches.first : null;
  }

  /// Evaluate permit validation status
  PermitValidationState permitState(String id) {
    final clean = QrParser.parse(id);
    final req = getRequest(clean);
    if (req == null) {
      return PermitValidationState(
        code: PermitValidationCode.notFound,
        title: 'Izin Tidak Ditemukan',
        message: 'QR Code ini bukan Izin Masuk Barang yang terdaftar. Akses masuk ditolak.',
        color: AppColors.red,
        icon: Icons.cancel_outlined,
      );
    }

    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final start = DateTime(req.startDate.year, req.startDate.month, req.startDate.day);
    final end = DateTime(req.endDate.year, req.endDate.month, req.endDate.day, 23, 59, 59);

    if (req.status != 'Approved') {
      return PermitValidationState(
        code: PermitValidationCode.notApprovedYet,
        title: 'Belum Disetujui',
        message: 'Status izin masih "${req.status}". Kendaraan belum diizinkan masuk sebelum seluruh persetujuan lengkap.',
        color: AppColors.orange,
        icon: Icons.hourglass_empty,
      );
    }

    if (today.isBefore(start)) {
      return PermitValidationState(
        code: PermitValidationCode.notValidYet,
        title: 'Belum Berlaku',
        message: 'Izin ini baru berlaku mulai ${req.startDate.day}/${req.startDate.month}/${req.startDate.year}. Belum dapat digunakan hari ini.',
        color: AppColors.orange,
        icon: Icons.event,
      );
    }

    if (today.isAfter(end)) {
      return PermitValidationState(
        code: PermitValidationCode.expired,
        title: 'Izin Kadaluarsa',
        message: 'Masa berlaku izin ini telah berakhir pada ${req.endDate.day}/${req.endDate.month}/${req.endDate.year}. Rekanan harus membuat pengajuan baru.',
        color: AppColors.red,
        icon: Icons.error_outline,
      );
    }

    return PermitValidationState(
      code: PermitValidationCode.validToday,
      title: 'Izin Aktif Hari Ini',
      message: 'Berlaku s/d ${req.endDate.day}/${req.endDate.month}/${req.endDate.year}',
      color: AppColors.green,
      icon: Icons.check_circle_outline,
    );
  }

  String? drvBlock(String req, String id) {
    final o = _drivers[id];
    if (o == null) return 'Pengemudi tidak terdaftar';
    if (!o.isSimValid) return 'SIM kadaluarsa';
    final ins = insideBy((l) => l.driverId == id);
    if (ins != null) return 'Di area sejak ${ins.timestamp.hour.toString().padLeft(2, '0')}:${ins.timestamp.minute.toString().padLeft(2, '0')}';
    return null;
  }

  String? vehBlock(String req, String id) {
    final o = _vehicles[id];
    if (o == null) return 'Kendaraan tidak terdaftar';
    if (!o.isAllDocValid) return 'STNK/KIR kadaluarsa';
    final ins = insideBy((l) => l.vehicleId == id);
    if (ins != null) return 'Di area sejak ${ins.timestamp.hour.toString().padLeft(2, '0')}:${ins.timestamp.minute.toString().padLeft(2, '0')}';
    return null;
  }

  GateActivityLog commitCheckin({
    required String requestId,
    required String driverId,
    required String vehicleId,
    required bool isFit,
    required bool fastReentry,
    String? summary,
    String? remarks,
    Map<int, ChecklistAnswer>? answers,
    List<String>? photos,
    String visitorBadgeNo = 'V-082',
    bool ktpHeld = true,
  }) {
    final nextId = (_activityLogs.isEmpty ? 0 : _activityLogs.map((l) => l.id).reduce((a, b) => a > b ? a : b)) + 1;
    final log = GateActivityLog(
      id: nextId,
      timestamp: DateTime.now(),
      requestId: requestId,
      driverId: driverId,
      vehicleId: vehicleId,
      type: isFit ? GateLogType.checkIn : GateLogType.rejected,
      gate: currentGate ?? 'KPBR Gate',
      officer: currentUser?.name ?? 'Petugas Security',
      result: isFit ? 'Laik' : 'Tidak Laik',
      isFastReentry: fastReentry,
      summary: summary ??
          (fastReentry
              ? 'Re-entry · inspected earlier today'
              : (isFit ? '10/10 Present & Fit' : 'Mandatory item failed')),
      remarks: remarks,
      answers: answers,
      photos: photos,
      visitorBadgeNo: visitorBadgeNo,
      ktpHeld: ktpHeld,
    );
    _activityLogs.add(log);
    return log;
  }

  GateActivityLog commitCheckout({
    required String requestId,
    required String driverId,
    required String vehicleId,
    bool visitorBadgeReturned = true,
    bool ktpReturned = true,
  }) {
    final nextId = (_activityLogs.isEmpty ? 0 : _activityLogs.map((l) => l.id).reduce((a, b) => a > b ? a : b)) + 1;
    final log = GateActivityLog(
      id: nextId,
      timestamp: DateTime.now(),
      requestId: requestId,
      driverId: driverId,
      vehicleId: vehicleId,
      type: GateLogType.checkOut,
      gate: currentGate ?? 'KPBR Gate',
      officer: currentUser?.name ?? 'Petugas Security',
      summary: 'Checked out cleanly',
      visitorBadgeReturned: visitorBadgeReturned,
      ktpReturned: ktpReturned,
    );
    _activityLogs.add(log);
    return log;
  }

  // --- Backward Compatibility methods ---
  @override
  Future<List<MaterialPermitHeader>> fetchActivePermits() async {
    return List.unmodifiable(_permits);
  }

  @override
  Future<MaterialPermitHeader?> findPermitByQrToken(String token) async {
    for (final permit in _permits) {
      if (permit.qrCodeToken == token || permit.requestNo == token) {
        return permit;
      }
    }
    return null;
  }

  Future<MaterialPermitHeader?> getPermitByToken(String qrToken) async {
    for (final permit in _permits) {
      if (permit.qrCodeToken == qrToken || permit.requestNo == qrToken) {
        return permit;
      }
    }
    return null;
  }

  Future<List<MaterialPermitHeader>> searchPermits({
    String? query,
    PermitBasis? basis,
  }) async {
    return _permits.where((p) {
      if (basis != null && p.permitBasis != basis) return false;
      if (query != null && query.isNotEmpty) {
        final q = query.toLowerCase();
        final matchReq = p.requestNo.toLowerCase().contains(q);
        final matchVendor = p.vendorName.toLowerCase().contains(q);
        final matchVehicle = p.vehicleItems.any((v) => v.licensePlateNo.toLowerCase().contains(q));
        return matchReq || matchVendor || matchVehicle;
      }
      return true;
    }).toList();
  }

  @override
  Future<ScreeningLog> recordScreeningLog(ScreeningLog log) async {
    _logs.add(log);
    return log;
  }

  Future<List<ScreeningLog>> getLogsForPermit(String requestNo) async {
    return _logs.where((l) => l.requestNo == requestNo).toList();
  }
}
