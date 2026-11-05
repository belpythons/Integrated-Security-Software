import '../api/api_config.dart';
import '../api/dto/posko_dtos.dart';
import '../core/qr_parser.dart';
import '../models/gate_app_models.dart';
import 'mock_permit_service.dart';
import 'remote_permit_service.dart';

/// Unified Dual-Source Repository for SES Mobile Gate Security.
/// Automatically bridges between local-first offline mock (default) and live SES API (port 8084).
class PermitRepository {
  static final PermitRepository _instance = PermitRepository._internal();
  factory PermitRepository() => _instance;

  final MockPermitService _localMock = MockPermitService();
  final RemotePermitService _remoteService = RemotePermitService();

  PermitRepository._internal();

  MockPermitService get local => _localMock;
  RemotePermitService get remote => _remoteService;

  /// Whether the app is currently configured to prefer local offline data.
  bool get isOfflineMode => ApiConfig.isOfflineMode;

  set isOfflineMode(bool val) {
    ApiConfig.isOfflineMode = val;
  }

  /// Current Base URL
  String get baseUrl => ApiConfig.baseUrl;

  set baseUrl(String val) {
    ApiConfig.baseUrl = val;
  }

  /// Ping the backend to check if `ses-api` is running on port 8084.
  Future<bool> checkBackendConnection() async {
    return _remoteService.isBackendReachable();
  }

  /// Scan or fetch permit details by QR code token or Request Number.
  Future<GatePermitRequest?> findPermit(String code) async {
    final cleanCode = QrParser.parse(code);
    if (!ApiConfig.isOfflineMode) {
      try {
        final resp = await _remoteService.scanQr(cleanCode);
        if (resp.success && resp.data != null) {
          final dto = resp.data!;
          final now = DateTime.now();
          DateTime parseDate(String? s, DateTime fallback) {
            if (s == null) return fallback;
            try {
              return DateTime.parse(s);
            } catch (_) {
              return fallback;
            }
          }

          // Convert PoskoScanResponseDto to GatePermitRequest
          return GatePermitRequest(
            id: dto.requestNo,
            status: dto.screeningStatus == 'APPROVED' ? 'Approved' : 'In Approval',
            vendor: dto.vendorName,
            startDate: parseDate(dto.validFrom, now),
            endDate: parseDate(dto.validTo, now.add(const Duration(days: 1))),
            poNumber: dto.poNumber,
            doNumber: '–',
            plantDestination: dto.department,
            driverIds: dto.registeredDrivers,
            vehicleIds: dto.registeredVehicles,
            poDescription: dto.poDescription,
            unitPriceMasked: dto.unitPriceMasked,
            hasKkbSticker: dto.hasKkbSticker,
            items: [
              GateCargoItem(name: dto.poDescription, qty: 1, uom: 'LOT'),
            ],
          );
        }
      } catch (_) {
        // Fall back to local mock
      }
    }

    return _localMock.getRequest(cleanCode);
  }

  /// Perform Gate Check-In transaction with KTP hold and visitor badge.
  Future<GateActivityLog> checkIn({
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
  }) async {
    // 1. Commit locally first to preserve offline state
    final localLog = _localMock.commitCheckin(
      requestId: requestId,
      driverId: driverId,
      vehicleId: vehicleId,
      isFit: isFit,
      fastReentry: fastReentry,
      summary: summary,
      remarks: remarks,
      answers: answers,
      photos: photos,
      visitorBadgeNo: visitorBadgeNo,
      ktpHeld: ktpHeld,
    );

    // 2. If online mode is active, sync with ses-api backend
    if (!ApiConfig.isOfflineMode) {
      try {
        final driverName = _localMock.getDriver(driverId)?.name ?? driverId;
        final vehiclePlate = _localMock.getVehicle(vehicleId)?.plate ?? vehicleId;

        await _remoteService.checkIn(
          PoskoCheckInRequestDto(
            requestNo: requestId,
            selectedVehiclePlate: vehiclePlate,
            selectedDriverName: driverName,
            visitorBadgeNo: visitorBadgeNo,
            ktpHeld: ktpHeld,
            notes: remarks ?? summary,
          ),
        );
      } catch (_) {
        // Log saved locally, network sync silent fallback
      }
    }

    return localLog;
  }

  /// Perform Gate Check-Out transaction with badge return and KTP return.
  Future<GateActivityLog> checkOut({
    required String requestId,
    required String driverId,
    required String vehicleId,
    bool visitorBadgeReturned = true,
    bool ktpReturned = true,
    String? notes,
  }) async {
    // 1. Commit locally first
    final localLog = _localMock.commitCheckout(
      requestId: requestId,
      driverId: driverId,
      vehicleId: vehicleId,
      visitorBadgeReturned: visitorBadgeReturned,
      ktpReturned: ktpReturned,
    );

    // 2. If online mode is active, sync with ses-api backend
    if (!ApiConfig.isOfflineMode) {
      try {
        final vehiclePlate = _localMock.getVehicle(vehicleId)?.plate ?? vehicleId;
        await _remoteService.checkOut(
          PoskoCheckOutRequestDto(
            requestNo: requestId,
            selectedVehiclePlate: vehiclePlate,
            visitorBadgeReturned: visitorBadgeReturned,
            ktpReturned: ktpReturned,
            notes: notes,
          ),
        );
      } catch (_) {
        // Log saved locally, network sync silent fallback
      }
    }

    return localLog;
  }
}

