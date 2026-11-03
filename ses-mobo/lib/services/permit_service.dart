import '../models/permit_header.dart';
import '../models/screening_log.dart';

abstract class PermitService {
  Future<List<MaterialPermitHeader>> fetchActivePermits();
  Future<MaterialPermitHeader?> findPermitByQrToken(String token);
  Future<ScreeningLog> recordScreeningLog(ScreeningLog log);
}

