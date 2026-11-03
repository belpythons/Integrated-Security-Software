import '../core/app_constants.dart';

/// Model log operasional screening posko & siklus keluar-masuk gerbang (SES_MAT_PERMIT_SCREENING_LOG)
/// Sesuai Master Blueprint Bab 4.5 & Stage 07.
class ScreeningLog {
  final int logId;
  final String requestNo;
  final String licensePlateNo;
  final String driverName;
  final ScreeningResult screeningResult;
  final String inspectorNip;
  final DateTime screeningTimestamp;
  final String? checklistJsonData;
  String? visitorBadgeNo;
  bool isKtpHeld;
  DateTime? checkinTimestamp;
  DateTime? checkoutTimestamp;
  VisitCycleStatus visitStatus;

  ScreeningLog({
    required this.logId,
    required this.requestNo,
    required this.licensePlateNo,
    required this.driverName,
    required this.screeningResult,
    required this.inspectorNip,
    required this.screeningTimestamp,
    this.checklistJsonData,
    this.visitorBadgeNo,
    this.isKtpHeld = false,
    this.checkinTimestamp,
    this.checkoutTimestamp,
    this.visitStatus = VisitCycleStatus.none,
  });

  bool get isEligibleForCheckIn {
    return (screeningResult == ScreeningResult.approved ||
            screeningResult == ScreeningResult.bypassed) &&
        visitStatus != VisitCycleStatus.checkedIn;
  }

  void performCheckIn({required String badgeNo}) {
    if (!isEligibleForCheckIn) {
      throw StateError('Kendaraan belum lolos screening fisik atau sudah berstatus CHECKED_IN');
    }
    visitorBadgeNo = badgeNo;
    isKtpHeld = true;
    checkinTimestamp = DateTime.now();
    visitStatus = VisitCycleStatus.checkedIn;
  }

  void performCheckOut() {
    if (visitStatus != VisitCycleStatus.checkedIn) {
      throw StateError('Kendaraan tidak dalam status CHECKED_IN');
    }
    isKtpHeld = false;
    checkoutTimestamp = DateTime.now();
    visitStatus = VisitCycleStatus.checkedOut;
  }

  Map<String, dynamic> toJson() {
    return {
      'log_id': logId,
      'request_no': requestNo,
      'license_plate_no': licensePlateNo,
      'driver_name': driverName,
      'screening_result': screeningResult.code,
      'inspector_nip': inspectorNip,
      'screening_timestamp': screeningTimestamp.toIso8601String(),
      'checklist_json_data': checklistJsonData,
      'visitor_badge_no': visitorBadgeNo,
      'is_ktp_held': isKtpHeld ? 'Y' : 'N',
      'checkin_timestamp': checkinTimestamp?.toIso8601String(),
      'checkout_timestamp': checkoutTimestamp?.toIso8601String(),
      'visit_status': visitStatus.code,
    };
  }

  factory ScreeningLog.fromJson(Map<String, dynamic> json) {
    return ScreeningLog(
      logId: json['log_id'] as int,
      requestNo: json['request_no'] as String,
      licensePlateNo: json['license_plate_no'] as String,
      driverName: json['driver_name'] as String,
      screeningResult: ScreeningResult.fromCode(json['screening_result'] as String? ?? 'REJECTED'),
      inspectorNip: json['inspector_nip'] as String,
      screeningTimestamp: DateTime.parse(json['screening_timestamp'] as String),
      checklistJsonData: json['checklist_json_data'] as String?,
      visitorBadgeNo: json['visitor_badge_no'] as String?,
      isKtpHeld: json['is_ktp_held'] == 'Y',
      checkinTimestamp: json['checkin_timestamp'] != null
          ? DateTime.parse(json['checkin_timestamp'] as String)
          : null,
      checkoutTimestamp: json['checkout_timestamp'] != null
          ? DateTime.parse(json['checkout_timestamp'] as String)
          : null,
      visitStatus: VisitCycleStatus.fromCode(json['visit_status'] as String? ?? 'NONE'),
    );
  }
}
