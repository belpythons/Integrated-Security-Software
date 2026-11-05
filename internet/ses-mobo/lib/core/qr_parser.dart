/// Smart QR / Barcode parser for SES Material Entry Permit documents
/// Generated from the web platform SES (`D:\GIT\Gitea\belva\ses\ses-core\...\RequestQrCodeService.java`).
class QrParser {
  QrParser._();

  static final RegExp _reqMatRegex = RegExp(r'REQ-MAT-\d{8}-[\w\d]+', caseSensitive: false);
  static final RegExp _imbRegex = RegExp(r'IMB-\d{4}-[\w\d]+', caseSensitive: false);

  /// Extracts the clean request number from raw scanned payload.
  /// Handles both URL verification links (`{APP_LINK}/verify/{requestNo}`)
  /// and direct request numbers (`REQ-MAT-...` / `IMB-...`).
  static String parse(String raw) {
    var text = raw.trim();
    if (text.isEmpty) return '';

    // 1. Check for web verification URL: ".../verify/{requestNo}"
    if (text.contains('/verify/')) {
      final parts = text.split('/verify/');
      if (parts.length > 1) {
        var token = parts[1].trim();
        // Remove URL query parameters if present (e.g. ?token=...)
        if (token.contains('?')) {
          token = token.split('?').first;
        }
        // Remove trailing slash or hash
        if (token.contains('#')) {
          token = token.split('#').first;
        }
        if (token.endsWith('/')) {
          token = token.substring(0, token.length - 1);
        }
        if (token.isNotEmpty) {
          return token;
        }
      }
    }

    // 2. Search for regex pattern of official SES Material Permit (REQ-MAT-yyyyMMdd-XXXX)
    final matchMat = _reqMatRegex.firstMatch(text);
    if (matchMat != null) {
      return matchMat.group(0)!;
    }

    // 3. Search for prototype format (IMB-yyyy-XXXXXX)
    final matchImb = _imbRegex.firstMatch(text);
    if (matchImb != null) {
      return matchImb.group(0)!;
    }

    // 4. Default: return clean trimmed text
    return text;
  }

  /// Checks if the raw string represents an official SES permit token or URL.
  static bool isSesPermit(String raw) {
    final parsed = parse(raw);
    return parsed.toUpperCase().startsWith('REQ-MAT-') ||
        parsed.toUpperCase().startsWith('IMB-') ||
        raw.contains('/verify/');
  }
}

