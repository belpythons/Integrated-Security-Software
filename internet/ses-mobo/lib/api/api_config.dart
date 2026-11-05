/// Configuration for connecting ses-mobo to the backend ses-api (PT Badak NGL).
/// Defaulted directly to live production connection without manual offline toggle.
class ApiConfig {
  static const String defaultEmulatorUrl = 'http://10.0.2.2:8084/api/v1';
  static const String defaultLocalhostUrl = 'http://localhost:8084/api/v1';

  /// Currently configured base URL.
  /// If using USB debugging with `adb reverse tcp:8084 tcp:8084`, localhost:8084 is accessible.
  static String baseUrl = defaultLocalhostUrl;

  /// Default is live connected to backend (no toggle).
  static bool isOfflineMode = false;

  /// Connection timeout in seconds.
  static int timeoutSeconds = 6;

  /// Stored JWT token from AuthRestController.
  static String? authToken;

  /// Authenticated user's details.
  static String? authenticatedUsername;
  static String? authenticatedFullName;
  static List<String> authenticatedRoles = [];
  static String activeGate = 'KPBR Gate';

  /// Check if user has an active authenticated session.
  static bool get isAuthenticated => authToken != null && authToken!.isNotEmpty;

  /// Reset to defaults.
  static void reset() {
    baseUrl = defaultLocalhostUrl;
    isOfflineMode = false;
    authToken = null;
    authenticatedUsername = null;
    authenticatedFullName = null;
    authenticatedRoles = [];
    activeGate = 'KPBR Gate';
  }
}
