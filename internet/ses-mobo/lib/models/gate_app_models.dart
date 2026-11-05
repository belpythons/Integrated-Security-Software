import 'package:flutter/material.dart';

class GateSecurityUser {
  final String username;
  final String pin;
  final String name;
  final String employeeId;
  final String defaultGate;
  final bool active;

  GateSecurityUser({
    required this.username,
    required this.pin,
    required this.name,
    required this.employeeId,
    required this.defaultGate,
    this.active = true,
  });
}

class GateDriver {
  final String id;
  final String name;
  final String nik;
  final String simNumber;
  final String simType;
  final DateTime simValidUntil;

  GateDriver({
    required this.id,
    required this.name,
    required this.nik,
    required this.simNumber,
    required this.simType,
    required this.simValidUntil,
  });

  bool get isSimValid => simValidUntil.isAfter(DateTime.now());
}

class GateVehicle {
  final String id;
  final String plate;
  final String type;
  final String brand;
  final DateTime taxValidUntil;
  final DateTime stnkValidUntil;
  final DateTime kirValidUntil;
  final bool hasKkbSticker;

  GateVehicle({
    required this.id,
    required this.plate,
    required this.type,
    required this.brand,
    required this.taxValidUntil,
    required this.stnkValidUntil,
    required this.kirValidUntil,
    this.hasKkbSticker = false,
  });

  bool get isTaxValid => taxValidUntil.isAfter(DateTime.now());
  bool get isStnkValid => stnkValidUntil.isAfter(DateTime.now());
  bool get isKirValid => kirValidUntil.isAfter(DateTime.now());
  bool get isAllDocValid => isTaxValid && isStnkValid && isKirValid;
}

class GateCargoItem {
  final String name;
  final int qty;
  final String uom;

  GateCargoItem({
    required this.name,
    required this.qty,
    required this.uom,
  });
}

enum PermitValidationCode {
  validToday,
  notApprovedYet,
  notValidYet,
  expired,
  notFound,
}

class PermitValidationState {
  final PermitValidationCode code;
  final String title;
  final String message;
  final Color color;
  final IconData icon;

  PermitValidationState({
    required this.code,
    required this.title,
    required this.message,
    required this.color,
    required this.icon,
  });

  bool get isValid => code == PermitValidationCode.validToday;
}

class GatePermitRequest {
  final String id;
  final String status; // 'Approved', 'In Approval', 'Draft', 'Rejected'
  final String vendor;
  final DateTime startDate;
  final DateTime endDate;
  final String poNumber;
  final String doNumber;
  final String plantDestination;
  final List<String> driverIds;
  final List<String> vehicleIds;
  final List<GateCargoItem> items;
  final String poDescription;
  final String unitPriceMasked;
  final bool hasKkbSticker;

  GatePermitRequest({
    required this.id,
    required this.status,
    required this.vendor,
    required this.startDate,
    required this.endDate,
    required this.poNumber,
    required this.doNumber,
    required this.plantDestination,
    required this.driverIds,
    required this.vehicleIds,
    required this.items,
    this.poDescription = 'Material Delivery Contract',
    this.unitPriceMasked = '*** CONFIDENTIAL / PRICE MASKED ***',
    this.hasKkbSticker = false,
  });
}

enum ChecklistCategory { vehicle, safety, cargo, driver }

enum ChecklistAnswer { presentAndFit, presentNotFit, notPresent }

class GateChecklistItem {
  final int id;
  final String name;
  final ChecklistCategory category;
  final bool mandatory;
  final bool photoRequired;

  GateChecklistItem({
    required this.id,
    required this.name,
    required this.category,
    required this.mandatory,
    required this.photoRequired,
  });

  String get categoryName {
    switch (category) {
      case ChecklistCategory.vehicle:
        return 'Kendaraan';
      case ChecklistCategory.safety:
        return 'Keselamatan';
      case ChecklistCategory.cargo:
        return 'Muatan';
      case ChecklistCategory.driver:
        return 'Pengemudi';
    }
  }
}

enum GateLogType { checkIn, checkOut, rejected }

class GateActivityLog {
  final int id;
  final DateTime timestamp;
  final String requestId;
  final String driverId;
  final String vehicleId;
  final GateLogType type;
  final String gate;
  final String officer;
  final String? result; // 'Laik', 'Tidak Laik'
  final bool isFastReentry;
  final String? summary;
  final String? remarks;
  final Map<int, ChecklistAnswer>? answers;
  final List<String>? photos;
  final String visitorBadgeNo;
  final bool ktpHeld;
  final bool visitorBadgeReturned;
  final bool ktpReturned;

  GateActivityLog({
    required this.id,
    required this.timestamp,
    required this.requestId,
    required this.driverId,
    required this.vehicleId,
    required this.type,
    required this.gate,
    required this.officer,
    this.result,
    this.isFastReentry = false,
    this.summary,
    this.remarks,
    this.answers,
    this.photos,
    this.visitorBadgeNo = 'V-082',
    this.ktpHeld = true,
    this.visitorBadgeReturned = false,
    this.ktpReturned = false,
  });

  String get typeLabel {
    switch (type) {
      case GateLogType.checkIn:
        return 'Check-in';
      case GateLogType.checkOut:
        return 'Check-out';
      case GateLogType.rejected:
        return 'Ditolak';
    }
  }
}
