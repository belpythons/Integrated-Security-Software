/// Generic response wrapper matching `com.badaklng.ses.api.dto.ApiResponse<T>`
/// from the backend Spring Boot `ses-api`.
class ApiResponse<T> {
  final bool success;
  final String message;
  final T? data;
  final int timestamp;

  const ApiResponse({
    required this.success,
    required this.message,
    this.data,
    required this.timestamp,
  });

  factory ApiResponse.fromJson(
    Map<String, dynamic> json,
    T Function(dynamic dataJson)? fromDataJson,
  ) {
    return ApiResponse<T>(
      success: json['success'] as bool? ?? false,
      message: json['message'] as String? ?? '',
      data: json['data'] != null && fromDataJson != null
          ? fromDataJson(json['data'])
          : json['data'] as T?,
      timestamp: json['timestamp'] as int? ?? DateTime.now().millisecondsSinceEpoch,
    );
  }

  Map<String, dynamic> toJson(dynamic Function(T? data)? toDataJson) {
    return {
      'success': success,
      'message': message,
      'data': toDataJson != null ? toDataJson(data) : data,
      'timestamp': timestamp,
    };
  }

  factory ApiResponse.ok(T data, String message) {
    return ApiResponse<T>(
      success: true,
      message: message,
      data: data,
      timestamp: DateTime.now().millisecondsSinceEpoch,
    );
  }

  factory ApiResponse.error(String message) {
    return ApiResponse<T>(
      success: false,
      message: message,
      data: null,
      timestamp: DateTime.now().millisecondsSinceEpoch,
    );
  }
}

