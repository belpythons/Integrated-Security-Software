import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'api_config.dart';
import 'dto/api_response.dart';
import 'dto/posko_dtos.dart';

/// HTTP Client that interacts with the Spring Boot backend `ses-api`.
class ApiClient {
  final http.Client _client;

  ApiClient({http.Client? client}) : _client = client ?? http.Client();

  Map<String, String> _headers() {
    final headers = <String, String>{
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
    if (ApiConfig.authToken != null && ApiConfig.authToken!.isNotEmpty) {
      headers['Authorization'] = 'Bearer ${ApiConfig.authToken}';
    }
    return headers;
  }

  /// Sends a POST request to [endpoint] with [body].
  Future<ApiResponse<T>> post<T>(
    String endpoint, {
    Map<String, dynamic>? body,
    T Function(dynamic dataJson)? fromDataJson,
  }) async {
    final uri = Uri.parse('${ApiConfig.baseUrl}$endpoint');
    try {
      final response = await _client
          .post(
            uri,
            headers: _headers(),
            body: body != null ? jsonEncode(body) : null,
          )
          .timeout(Duration(seconds: ApiConfig.timeoutSeconds));

      final decoded = jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
      return ApiResponse.fromJson(decoded, fromDataJson);
    } on SocketException catch (e) {
      return ApiResponse.error(
        'Koneksi gagal ke server SES (${uri.host}:${uri.port}). Pastikan backend ses-api berjalan. ($e)',
      );
    } on TimeoutException {
      return ApiResponse.error(
        'Waktu koneksi ke server SES habis (${ApiConfig.timeoutSeconds}s). Server tidak merespons.',
      );
    } on FormatException catch (e) {
      return ApiResponse.error('Format respon dari backend tidak valid: $e');
    } catch (e) {
      return ApiResponse.error('Kesalahan jaringan: $e');
    }
  }

  /// Sends a GET request to [endpoint].
  Future<ApiResponse<T>> get<T>(
    String endpoint, {
    Map<String, String>? queryParams,
    T Function(dynamic dataJson)? fromDataJson,
  }) async {
    final baseUrl = ApiConfig.baseUrl;
    var uri = Uri.parse('$baseUrl$endpoint');
    if (queryParams != null && queryParams.isNotEmpty) {
      uri = uri.replace(queryParameters: queryParams);
    }

    try {
      final response = await _client
          .get(uri, headers: _headers())
          .timeout(Duration(seconds: ApiConfig.timeoutSeconds));

      final decoded = jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
      return ApiResponse.fromJson(decoded, fromDataJson);
    } on SocketException catch (e) {
      return ApiResponse.error('Tidak dapat menghubungi server SES ($e)');
    } on TimeoutException {
      return ApiResponse.error('Waktu tunggu koneksi habis (${ApiConfig.timeoutSeconds}s)');
    } catch (e) {
      return ApiResponse.error('Gagal mengambil data: $e');
    }
  }

  /// Authenticate against backend ses-api `POST /auth/login`
  Future<ApiResponse<AuthResponseDto>> login(String username, String password) async {
    final req = AuthRequestDto(username: username, password: password);
    final res = await post<AuthResponseDto>(
      '/auth/login',
      body: req.toJson(),
      fromDataJson: (json) => AuthResponseDto.fromJson(json as Map<String, dynamic>),
    );
    if (res.success && res.data != null) {
      ApiConfig.authToken = res.data!.token;
      ApiConfig.authenticatedUsername = res.data!.username;
      ApiConfig.authenticatedFullName = res.data!.fullName;
      ApiConfig.authenticatedRoles = res.data!.roles;
    }
    return res;
  }

  /// Tests connectivity to the backend SES API.
  Future<bool> pingServer() async {
    try {
      final uri = Uri.parse('${ApiConfig.baseUrl}/actuator/health');
      final response = await _client
          .get(uri)
          .timeout(Duration(seconds: ApiConfig.timeoutSeconds));
      return response.statusCode >= 200 && response.statusCode < 400;
    } catch (_) {
      // Fallback: try auth check
      try {
        final uri = Uri.parse('${ApiConfig.baseUrl}/auth/login');
        final response = await _client
            .post(
              uri,
              headers: {'Content-Type': 'application/json'},
              body: jsonEncode({'username': 'ping', 'password': 'ping'}),
            )
            .timeout(Duration(seconds: ApiConfig.timeoutSeconds));
        // If it responds with anything (even 401 or 403), the server is alive!
        return response.statusCode != 0;
      } catch (_) {
        return false;
      }
    }
  }

  void close() {
    _client.close();
  }
}

