import 'dart:io';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Stage 03: Verifikasi Skrip DDL SQL Oracle Idempotent', () {
    final sqlFile = File('sql/V1.4__create_ses_material_permit_tables.sql');

    test('Berkas SQL V1.4 ada di direktori sql/ dan tidak kosong', () {
      expect(sqlFile.existsSync(), isTrue, reason: 'File DDL SQL V1.4 harus tersedia');
      final content = sqlFile.readAsStringSync();
      expect(content.length, greaterThan(500));
    });

    test('Skrip DDL mencakup 6 tabel utama sesuai spesifikasi arsitektur', () {
      final content = sqlFile.readAsStringSync();

      expect(content.contains('TABLE_NAME = \'SES_VENDOR_ACCOUNT\''), isTrue);
      expect(content.contains('TABLE_NAME = \'SES_MAT_PERMIT_HDR\''), isTrue);
      expect(content.contains('TABLE_NAME = \'SES_MAT_PERMIT_PO_DO\''), isTrue);
      expect(content.contains('TABLE_NAME = \'SES_MAT_PERMIT_VEHICLE\''), isTrue);
      expect(content.contains('TABLE_NAME = \'SES_MAT_PERMIT_DRIVER\''), isTrue);
      expect(content.contains('TABLE_NAME = \'SES_MAT_PERMIT_SCREENING_LOG\''), isTrue);
    });

    test('Skrip DDL bersifat idempotent dan mendefinisikan indeks komposit', () {
      final content = sqlFile.readAsStringSync();

      // Mengecek pola blok PL/SQL idempotent
      expect(content.contains('DECLARE'), isTrue);
      expect(content.contains('SELECT COUNT(*) INTO v_count'), isTrue);
      expect(content.contains('IF v_count = 0 THEN'), isTrue);

      // Mengecek indeks performa query
      expect(content.contains('IDX_SES_MAT_HDR_STATUS'), isTrue);
      expect(content.contains('IDX_SES_MAT_PO_NO'), isTrue);
      expect(content.contains('IDX_SES_MAT_VEH_PLATE'), isTrue);
    });
  });
}

