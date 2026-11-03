import 'app_constants.dart';
import '../models/vehicle_item.dart';
import '../models/driver_item.dart';

class ValidationResult {
  final bool isValid;
  final String? errorMessage;

  const ValidationResult({required this.isValid, this.errorMessage});
}

/// Mesin kalkulasi masa berlaku izin material & aturan penyesuaian Admin Security
/// Sesuai Master Blueprint Bab 4 & Stage 06.
class ValidityEngine {
  ValidityEngine._();

  /// Menghitung batas maksimum hari yang diizinkan oleh sistem berdasarkan jenis basis izin.
  static int getSystemCapDays(PermitBasis basis) {
    switch (basis) {
      case PermitBasis.po:
        return 3; // PO maksimal 3 hari kalender
      case PermitBasis.ca:
        return 14; // Kontrak CA maksimal 14 hari
      case PermitBasis.facilityMemo:
        return 7; // Fasilitas Kantin / Sembako maksimal 7 hari
      case PermitBasis.internalAsset:
        return 3; // Internal Gate Pass default 3 hari
    }
  }

  /// Menghitung tanggal `Valid From`
  /// Formula: max(Sysdate.beginningOfDay, EstArrivalDate.beginningOfDay)
  static DateTime calculateValidFrom({
    required DateTime sysdate,
    required DateTime estArrivalDate,
  }) {
    final sysDateStart = DateTime(sysdate.year, sysdate.month, sysdate.day);
    final estDateStart = DateTime(estArrivalDate.year, estArrivalDate.month, estArrivalDate.day);

    return estDateStart.isAfter(sysDateStart) ? estDateStart : sysDateStart;
  }

  /// Menghitung tanggal kedaluwarsa dokumen terpendek dari seluruh kendaraan dan pengemudi.
  /// Memeriksa: STNK 5 Th, Pajak STNK 1 Th, KIR, SIM Driver, dan Masa Berlaku Kontrak.
  static DateTime findEarliestDocumentExpiry({
    required List<VehicleItem> vehicles,
    required List<DriverItem> drivers,
    DateTime? contractExpiryDate,
  }) {
    DateTime? earliest;

    void compareDate(DateTime? candidate) {
      if (candidate == null) return;
      final candStart = DateTime(candidate.year, candidate.month, candidate.day);
      if (earliest == null || candStart.isBefore(earliest!)) {
        earliest = candStart;
      }
    }

    // Periksa kontrak
    compareDate(contractExpiryDate);

    // Periksa seluruh kendaraan
    for (final v in vehicles) {
      compareDate(v.stnk5YrExpiry);
      compareDate(v.stnkTaxExpiry);
      compareDate(v.kirExpiryDate);
    }

    // Periksa seluruh driver
    for (final d in drivers) {
      compareDate(d.simExpiryDate);
    }

    // Jika tidak ada dokumen terdaftar sama sekali, default ke 1 tahun ke depan
    return earliest ?? DateTime.now().add(const Duration(days: 365));
  }

  /// Menghitung rekomendasi otomatis tanggal `Valid To`
  /// Formula: min(System Cap Date, Earliest Document Expiry)
  static DateTime calculateRecommendedValidTo({
    required PermitBasis basis,
    required DateTime validFrom,
    required List<VehicleItem> vehicles,
    required List<DriverItem> drivers,
    DateTime? contractExpiryDate,
  }) {
    final capDays = getSystemCapDays(basis);
    final capDate = validFrom.add(Duration(days: capDays));

    final earliestDoc = findEarliestDocumentExpiry(
      vehicles: vehicles,
      drivers: drivers,
      contractExpiryDate: contractExpiryDate,
    );

    return capDate.isBefore(earliestDoc) ? capDate : earliestDoc;
  }

  /// Validasi perubahan tanggal Valid To oleh Admin Security (Admin Adjustment)
  /// Aturan:
  /// 1. Admin boleh memperpendek masa berlaku.
  /// 2. Admin TIDAK boleh memperpanjang melebihi System Cap Date.
  /// 3. Admin TIDAK boleh melebihi tanggal kedaluwarsa dokumen terpendek.
  /// 4. Admin TIDAK boleh menetapkan Valid To sebelum Valid From.
  static ValidationResult validateAdminAdjustment({
    required PermitBasis basis,
    required DateTime validFrom,
    required DateTime proposedValidTo,
    required List<VehicleItem> vehicles,
    required List<DriverItem> drivers,
    DateTime? contractExpiryDate,
  }) {
    final proposedStart = DateTime(proposedValidTo.year, proposedValidTo.month, proposedValidTo.day);
    final validFromStart = DateTime(validFrom.year, validFrom.month, validFrom.day);

    if (proposedStart.isBefore(validFromStart)) {
      return const ValidationResult(
        isValid: false,
        errorMessage: 'Tanggal Valid To tidak boleh lebih awal dari Valid From.',
      );
    }

    final capDays = getSystemCapDays(basis);
    final maxCapDate = validFromStart.add(Duration(days: capDays));
    if (proposedStart.isAfter(maxCapDate)) {
      return ValidationResult(
        isValid: false,
        errorMessage:
            'Tanggal Valid To melampaui batas maksimum sistem untuk basis ${basis.label} (Maksimal $capDays hari).',
      );
    }

    final earliestDoc = findEarliestDocumentExpiry(
      vehicles: vehicles,
      drivers: drivers,
      contractExpiryDate: contractExpiryDate,
    );

    if (proposedStart.isAfter(earliestDoc)) {
      return ValidationResult(
        isValid: false,
        errorMessage:
            'Tanggal Valid To melampaui masa berlaku dokumen terpendek (${earliestDoc.toIso8601String().substring(0, 10)}).',
      );
    }

    return const ValidationResult(isValid: true);
  }
}

/// Mesin perutean persetujuan dinamis (Approval Routing Engine)
/// Memastikan PO Reguler tidak melalui Procurement, sedangkan CA Kontrak wajib melalui Procurement.
class WorkflowRoutingEngine {
  WorkflowRoutingEngine._();

  static List<ApprovalStep> resolveApprovalChain({
    required SubModuleType subModule,
    required PermitBasis basis,
    bool includeSupervisor = false,
  }) {
    final steps = <ApprovalStep>[];
    int stepNumber = 1;

    // Optional Step 0: Supervisor
    if (includeSupervisor) {
      steps.add(
        ApprovalStep(
          stepNumber: stepNumber++,
          roleName: 'Supervisor User',
          description: 'Verifikasi teknis awal oleh Pengawas Lapangan',
        ),
      );
    }

    // Step 1: Manager User / Sponsor
    steps.add(
      ApprovalStep(
        stepNumber: stepNumber++,
        roleName: 'Manager User',
        description: 'Persetujuan pimpinan departemen pemohon / penerima material',
      ),
    );

    // Step 2: Procurement (KHUSUS CA / Kontrak)
    // Audio Rule: PO Reguler TIDAK melalui Procurement!
    if (basis == PermitBasis.ca) {
      steps.add(
        ApprovalStep(
          stepNumber: stepNumber++,
          roleName: 'Procurement Specialist',
          description: 'Otorisasi klausul kontrak & verifikasi rekanan CA',
        ),
      );
    }

    // Step 3: Admin Security
    steps.add(
      ApprovalStep(
        stepNumber: stepNumber++,
        roleName: 'Admin Security',
        description: 'Verifikasi kelengkapan dokumen berkas fisik/KKB & setting masa berlaku',
      ),
    );

    // Step 4: Manager Security
    steps.add(
      ApprovalStep(
        stepNumber: stepNumber++,
        roleName: 'Manager Security',
        description: 'Approval final otorisasi penerbitan Master QR Gate Pass',
      ),
    );

    return steps;
  }
}

class ApprovalStep {
  final int stepNumber;
  final String roleName;
  final String description;

  const ApprovalStep({
    required this.stepNumber,
    required this.roleName,
    required this.description,
  });
}
