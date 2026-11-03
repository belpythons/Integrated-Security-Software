import '../core/app_constants.dart';
import '../models/permit_header.dart';
import '../models/po_do_item.dart';
import '../models/vehicle_item.dart';
import '../models/driver_item.dart';
import '../models/screening_log.dart';
import 'permit_service.dart';

class MockPermitService implements PermitService {
  final List<MaterialPermitHeader> _permits = [];
  final List<ScreeningLog> _logs = [];

  MockPermitService() {
    _initMockData();
  }

  void _initMockData() {
    final now = DateTime(2026, 11, 3);

    // Kasus 1: PO Reguler Multi-Item (PT Amalindo Utama, Truk Hino Non-KKB)
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
        validFrom: now,
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
            hasActiveKkb: false, // Non-KKB -> Wajib uji fisik 11 kategori
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

    // Kasus 2: Kontrak CA dengan Mobil Berstiker KKB Aktif (PT Kilang Mandiri, Truk Molen Mixer)
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
        specificLocation: 'Area Train 5 Fondasi Pompa',
        estArrivalDate: now,
        validFrom: now,
        validTo: now.add(const Duration(days: 14)),
        qrCodeToken: 'QR-TOKEN-KILANGMANDIRI-002',
        permitStatus: PermitStatus.approved,
        poDoItems: [
          PoDoItem(
            requestNo: 'REQ-MAT-20261103-0002',
            poNumber: 'CA-088-ITEM-01',
            poDescription: 'Beton Ready Mix K-350 Tahan Getaran',
            doNumber: 'DO-KM-99',
            doDate: now,
            rawUnitPrice: 950000,
            poFileId: 104,
            doFileId: 105,
          ),
        ],
        vehicleItems: [
          VehicleItem(
            vehicleItemId: 'VH-02',
            requestNo: 'REQ-MAT-20261103-0002',
            licensePlateNo: 'KT 9999 CA',
            vehicleCategory: 'MIXER_TRUCK',
            vehicleBrand: 'Mitsubishi Fuso',
            stnk5YrExpiry: now.add(const Duration(days: 200)),
            stnkTaxExpiry: now.add(const Duration(days: 200)),
            kirExpiryDate: now.add(const Duration(days: 90)),
            stnkFileId: 202,
            hasActiveKkb: true, // KKB Aktif
            kkbStickerNo: 'KKB-2026-ENG-04',
            adminSkipInspection: true, // Admin beri centang bypass
          ),
        ],
        driverItems: [
          DriverItem(
            driverItemId: 'DR-02',
            requestNo: 'REQ-MAT-20261103-0002',
            personnelRole: 'DRIVER',
            driverOriginType: 'KKB_CONTRACTOR',
            badgeNo: 'BADGE-CA-012',
            driverName: 'Pak Hartanto',
            idCardFileId: 302,
            licenseType: 'SIMPER',
            licenseNo: 'SIMPER-NGL-881',
            simExpiryDate: now.add(const Duration(days: 180)),
          ),
        ],
      ),
    );

    // Kasus 3: Pengurus Fasilitas / Sembako Kantin (Kantin Badak, Bahan Pangan)
    _permits.add(
      MaterialPermitHeader(
        requestNo: 'REQ-MAT-20261103-0003',
        subModuleType: SubModuleType.entry,
        permitBasis: PermitBasis.facilityMemo,
        refDocNo: 'MEMO-KANTIN-11-2026',
        inputChannel: 'INTRANET',
        vendorName: 'CV Berkah Pangan Makmur',
        targetDeptId: 'D-HR-SERVICES',
        destinationZone: DestinationZone.gedungPutih,
        specificLocation: 'Dapur Utama Kantin PT Badak',
        estArrivalDate: now,
        validFrom: now,
        validTo: now.add(const Duration(days: 7)),
        qrCodeToken: 'QR-TOKEN-KANTIN-003',
        permitStatus: PermitStatus.approved,
        poDoItems: [
          PoDoItem(
            requestNo: 'REQ-MAT-20261103-0003',
            poNumber: 'SPK-KANTIN-99',
            poDescription: 'Beras Ramos 500 Kg & Telur Ayam 200 Piring',
            doNumber: 'DO-SEMBAKO-01',
            doDate: now,
            rawUnitPrice: 7500000,
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
            vehicleBrand: 'Daihatsu Gran Max',
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
            idCardFileId: 303,
            licenseType: 'SIM_A',
            licenseNo: 'SIM-A-882190',
            simExpiryDate: now.add(const Duration(days: 350)),
          ),
        ],
      ),
    );
  }

  @override
  Future<List<MaterialPermitHeader>> fetchActivePermits() async {
    return List.unmodifiable(_permits);
  }

  @override
  Future<MaterialPermitHeader?> findPermitByQrToken(String token) async {
    final clean = token.trim().toLowerCase();
    try {
      return _permits.firstWhere(
        (p) =>
            p.requestNo.toLowerCase() == clean ||
            (p.qrCodeToken != null && p.qrCodeToken!.toLowerCase() == clean),
      );
    } catch (_) {
      return null;
    }
  }

  @override
  Future<ScreeningLog> recordScreeningLog(ScreeningLog log) async {
    _logs.add(log);
    return log;
  }
}

