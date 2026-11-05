import '../api/api_client.dart';
import '../api/api_config.dart';
import '../api/dto/api_response.dart';
import '../api/dto/posko_dtos.dart';

/// Service connecting to the live Spring Boot `ses-api` backend at `http://localhost:8084/api/v1`.
class RemotePermitService {
  final ApiClient _client;

  RemotePermitService({ApiClient? client}) : _client = client ?? ApiClient();

  /// Authenticate security officer against `POST /api/v1/auth/login`.
  Future<ApiResponse<AuthResponseDto>> login(String username, String password) async {
    final response = await _client.post<AuthResponseDto>(
      '/auth/login',
      body: AuthRequestDto(username: username, password: password).toJson(),
      fromDataJson: (json) => AuthResponseDto.fromJson(json as Map<String, dynamic>),
    );

    if (response.success && response.data != null) {
      ApiConfig.authToken = response.data!.token;
      ApiConfig.authenticatedUsername = response.data!.username;
    }
    return response;
  }

  /// Scan QR payload against `POST /api/v1/posko/scan-qr`.
  Future<ApiResponse<PoskoScanResponseDto>> scanQr(String qrPayload) async {
    return _client.post<PoskoScanResponseDto>(
      '/posko/scan-qr',
      body: {'qrPayload': qrPayload},
      fromDataJson: (json) => PoskoScanResponseDto.fromJson(json as Map<String, dynamic>),
    );
  }

  /// Execute Check-In with physical KTP hold and visitor badge: `POST /api/v1/posko/check-in`.
  Future<ApiResponse<Map<String, Object?>>> checkIn(PoskoCheckInRequestDto request) async {
    return _client.post<Map<String, Object?>>(
      '/posko/check-in',
      body: request.toJson(),
      fromDataJson: (json) => (json as Map<String, dynamic>).cast<String, Object?>(),
    );
  }

  /// Execute Check-Out with visitor badge and KTP return: `POST /api/v1/posko/check-out`.
  Future<ApiResponse<Map<String, Object?>>> checkOut(PoskoCheckOutRequestDto request) async {
    return _client.post<Map<String, Object?>>(
      '/posko/check-out',
      body: request.toJson(),
      fromDataJson: (json) => (json as Map<String, dynamic>).cast<String, Object?>(),
    );
  }

  /// Fetch list of permits from `GET /api/v1/material-permit/list`.
  Future<ApiResponse<List<MaterialPermitDto>>> fetchMaterialPermits({
    int page = 0,
    int size = 10,
    String? term,
  }) async {
    final params = <String, String>{
      'page': page.toString(),
      'size': size.toString(),
      if (term != null && term.isNotEmpty) 'term': term,
    };

    final response = await _client.get<Map<String, dynamic>>(
      '/material-permit/list',
      queryParams: params,
      fromDataJson: (json) => json as Map<String, dynamic>,
    );

    if (response.success && response.data != null) {
      final itemsJson = response.data!['items'] as List<dynamic>? ?? [];
      final list = itemsJson
          .map((i) => MaterialPermitDto.fromJson(i as Map<String, dynamic>))
          .toList();
      return ApiResponse.ok(list, response.message);
    }

    return ApiResponse.error(response.message);
  }

  /// Check if the backend SES API is healthy and reachable.
  Future<bool> isBackendReachable() async {
    return _client.pingServer();
  }

  void close() {
    _client.close();
  }
}

