// Data Transfer Objects strictly matching the backend Spring Boot `ses-api`
// at `com.badaklng.ses.api.dto.*`.

/// Request for authentication: `POST /api/v1/auth/login`
class AuthRequestDto {
  final String username;
  final String password;

  const AuthRequestDto({
    required this.username,
    required this.password,
  });

  Map<String, dynamic> toJson() => {
        'username': username,
        'password': password,
      };
}

/// Response from authentication: `POST /api/v1/auth/login`
class AuthResponseDto {
  final String token;
  final String tokenType;
  final int expiresInMs;
  final String username;
  final String fullName;
  final List<String> roles;

  const AuthResponseDto({
    required this.token,
    required this.tokenType,
    required this.expiresInMs,
    required this.username,
    required this.fullName,
    required this.roles,
  });

  factory AuthResponseDto.fromJson(Map<String, dynamic> json) {
    return AuthResponseDto(
      token: json['token'] as String? ?? '',
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresInMs: json['expiresInMs'] as int? ?? 0,
      username: json['username'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      roles: (json['roles'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
    );
  }

  Map<String, dynamic> toJson() => {
        'token': token,
        'tokenType': tokenType,
        'expiresInMs': expiresInMs,
        'username': username,
        'fullName': fullName,
        'roles': roles,
      };
}

/// Response from scanning permit QR: `POST /api/v1/posko/scan-qr`
class PoskoScanResponseDto {
  final String requestNo;
  final String vendorName;
  final String department;
  final String poNumber;
  final String poDescription;
  final String unitPriceMasked;
  final String? validFrom;
  final String? validTo;
  final List<String> registeredVehicles;
  final List<String> registeredDrivers;
  final String screeningStatus;
  final bool hasKkbSticker;
  final String currentStatus;

  const PoskoScanResponseDto({
    required this.requestNo,
    required this.vendorName,
    required this.department,
    required this.poNumber,
    required this.poDescription,
    required this.unitPriceMasked,
    this.validFrom,
    this.validTo,
    required this.registeredVehicles,
    required this.registeredDrivers,
    required this.screeningStatus,
    required this.hasKkbSticker,
    required this.currentStatus,
  });

  factory PoskoScanResponseDto.fromJson(Map<String, dynamic> json) {
    return PoskoScanResponseDto(
      requestNo: json['requestNo'] as String? ?? '',
      vendorName: json['vendorName'] as String? ?? '',
      department: json['department'] as String? ?? '',
      poNumber: json['poNumber'] as String? ?? '',
      poDescription: json['poDescription'] as String? ?? '',
      unitPriceMasked: json['unitPriceMasked'] as String? ??
          '*** CONFIDENTIAL / PRICE MASKED ***',
      validFrom: json['validFrom'] as String?,
      validTo: json['validTo'] as String?,
      registeredVehicles: (json['registeredVehicles'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      registeredDrivers: (json['registeredDrivers'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      screeningStatus: json['screeningStatus'] as String? ?? 'PENDING_INSPECTION',
      hasKkbSticker: json['hasKkbSticker'] as bool? ?? false,
      currentStatus: json['currentStatus'] as String? ?? 'PENDING',
    );
  }

  Map<String, dynamic> toJson() => {
        'requestNo': requestNo,
        'vendorName': vendorName,
        'department': department,
        'poNumber': poNumber,
        'poDescription': poDescription,
        'unitPriceMasked': unitPriceMasked,
        'validFrom': validFrom,
        'validTo': validTo,
        'registeredVehicles': registeredVehicles,
        'registeredDrivers': registeredDrivers,
        'screeningStatus': screeningStatus,
        'hasKkbSticker': hasKkbSticker,
        'currentStatus': currentStatus,
      };
}

/// Request for gate check-in: `POST /api/v1/posko/check-in`
class PoskoCheckInRequestDto {
  final String requestNo;
  final String selectedVehiclePlate;
  final String selectedDriverName;
  final String visitorBadgeNo;
  final bool ktpHeld;
  final String? notes;

  const PoskoCheckInRequestDto({
    required this.requestNo,
    required this.selectedVehiclePlate,
    required this.selectedDriverName,
    required this.visitorBadgeNo,
    required this.ktpHeld,
    this.notes,
  });

  Map<String, dynamic> toJson() => {
        'requestNo': requestNo,
        'selectedVehiclePlate': selectedVehiclePlate,
        'selectedDriverName': selectedDriverName,
        'visitorBadgeNo': visitorBadgeNo,
        'ktpHeld': ktpHeld,
        if (notes != null) 'notes': notes,
      };

  factory PoskoCheckInRequestDto.fromJson(Map<String, dynamic> json) {
    return PoskoCheckInRequestDto(
      requestNo: json['requestNo'] as String? ?? '',
      selectedVehiclePlate: json['selectedVehiclePlate'] as String? ?? '',
      selectedDriverName: json['selectedDriverName'] as String? ?? '',
      visitorBadgeNo: json['visitorBadgeNo'] as String? ?? '',
      ktpHeld: json['ktpHeld'] as bool? ?? false,
      notes: json['notes'] as String?,
    );
  }
}

/// Request for gate check-out: `POST /api/v1/posko/check-out`
class PoskoCheckOutRequestDto {
  final String requestNo;
  final String selectedVehiclePlate;
  final bool visitorBadgeReturned;
  final bool ktpReturned;
  final String? notes;

  const PoskoCheckOutRequestDto({
    required this.requestNo,
    required this.selectedVehiclePlate,
    required this.visitorBadgeReturned,
    required this.ktpReturned,
    this.notes,
  });

  Map<String, dynamic> toJson() => {
        'requestNo': requestNo,
        'selectedVehiclePlate': selectedVehiclePlate,
        'visitorBadgeReturned': visitorBadgeReturned,
        'ktpReturned': ktpReturned,
        if (notes != null) 'notes': notes,
      };

  factory PoskoCheckOutRequestDto.fromJson(Map<String, dynamic> json) {
    return PoskoCheckOutRequestDto(
      requestNo: json['requestNo'] as String? ?? '',
      selectedVehiclePlate: json['selectedVehiclePlate'] as String? ?? '',
      visitorBadgeReturned: json['visitorBadgeReturned'] as bool? ?? false,
      ktpReturned: json['ktpReturned'] as bool? ?? false,
      notes: json['notes'] as String?,
    );
  }
}

/// Material Permit DTO for `GET /api/v1/material-permit/list`
class MaterialPermitDto {
  final String requestNo;
  final String? vendorName;
  final String? contractNo;
  final String? destinationDepartment;
  final String? vehiclePlate;
  final String? status;
  final String? validFrom;
  final String? validTo;

  const MaterialPermitDto({
    required this.requestNo,
    this.vendorName,
    this.contractNo,
    this.destinationDepartment,
    this.vehiclePlate,
    this.status,
    this.validFrom,
    this.validTo,
  });

  factory MaterialPermitDto.fromJson(Map<String, dynamic> json) {
    return MaterialPermitDto(
      requestNo: json['requestNo'] as String? ?? '',
      vendorName: json['vendorName'] as String?,
      contractNo: json['contractNo'] as String?,
      destinationDepartment: json['destinationDepartment'] as String?,
      vehiclePlate: json['vehiclePlate'] as String?,
      status: json['status'] as String?,
      validFrom: json['validFrom'] as String?,
      validTo: json['validTo'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'requestNo': requestNo,
        if (vendorName != null) 'vendorName': vendorName,
        if (contractNo != null) 'contractNo': contractNo,
        if (destinationDepartment != null)
          'destinationDepartment': destinationDepartment,
        if (vehiclePlate != null) 'vehiclePlate': vehiclePlate,
        if (status != null) 'status': status,
        if (validFrom != null) 'validFrom': validFrom,
        if (validTo != null) 'validTo': validTo,
      };
}

