import 'package:intl/intl.dart';

/// Helper generator cap air dinamis (Dynamic Watermarking)
/// Sesuai Master Blueprint Bab 5.3 & Stage 08.
class SecurityWatermark {
  SecurityWatermark._();

  /// Menghasilkan teks cap air dinamis sesuai standar kepatuhan audit PT Badak NGL
  /// Formula: "CONFIDENTIAL PT BADAK NGL - DOWNLOADED BY: `<USER_NIP>` AT `<TIMESTAMP>`"
  static String generateWatermarkText({
    required String userNip,
    DateTime? timestamp,
  }) {
    final now = timestamp ?? DateTime.now();
    final formatter = DateFormat('yyyy-MM-dd HH:mm:ss');
    final formattedTime = formatter.format(now);

    return 'CONFIDENTIAL PT BADAK NGL - DOWNLOADED BY: $userNip AT $formattedTime WITA';
  }

  /// Menghasilkan email tujuan notifikasi dengan memperhatikan parameter TESTING_MODE
  /// Jika TESTING_MODE = 'ON', email dialihkan ke developer test mailbox demi mencegah spam ke pejabat asli.
  static String resolveNotificationRecipient({
    required String originalRecipient,
    required bool isTestingMode,
    String fallbackTestEmail = 'dev-test@badakngl.co.id',
  }) {
    if (isTestingMode) {
      return fallbackTestEmail;
    }
    return originalRecipient;
  }
}

