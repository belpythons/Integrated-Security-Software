// Model data evaluasi checklist 11 pilar kelaikan fisik kendaraan (kkb_kks.jpg)
// Sesuai Master Blueprint Bab 3.7 & Stage 05.

enum InspectionStatus {
  pass('PASS', 'Lulus'),
  fail('FAIL', 'Tidak Layak');

  final String code;
  final String label;
  const InspectionStatus(this.code, this.label);

  static InspectionStatus fromCode(String code) {
    return InspectionStatus.values.firstWhere(
      (e) => e.code == code.toUpperCase(),
      orElse: () => InspectionStatus.fail,
    );
  }
}

class CategoryInspectionItem {
  final String categoryCode;
  final String categoryTitle;
  final String subItemsDescription;
  InspectionStatus status;
  String notes;

  CategoryInspectionItem({
    required this.categoryCode,
    required this.categoryTitle,
    required this.subItemsDescription,
    this.status = InspectionStatus.pass,
    this.notes = '',
  });

  bool get isPass => status == InspectionStatus.pass;

  Map<String, dynamic> toJson() {
    return {
      'category_code': categoryCode,
      'category_title': categoryTitle,
      'status': status.code,
      'notes': notes,
    };
  }

  factory CategoryInspectionItem.fromJson(Map<String, dynamic> json) {
    return CategoryInspectionItem(
      categoryCode: json['category_code'] as String? ?? '',
      categoryTitle: json['category_title'] as String? ?? '',
      subItemsDescription: json['sub_items_desc'] as String? ?? '',
      status: InspectionStatus.fromCode(json['status'] as String? ?? 'FAIL'),
      notes: json['notes'] as String? ?? '',
    );
  }
}

class DriverPpeChecklist {
  bool safetyShoes;
  bool longPants;
  bool collaredShirtOrCoverall;
  bool helmetGlasses;
  bool specificHazmatPpe;

  DriverPpeChecklist({
    this.safetyShoes = true,
    this.longPants = true,
    this.collaredShirtOrCoverall = true,
    this.helmetGlasses = false,
    this.specificHazmatPpe = false,
  });

  bool isCompliantForZone({required bool isZone1}) {
    if (!safetyShoes || !longPants || !collaredShirtOrCoverall) {
      return false;
    }
    // Jika Zone 1 (Area Proses / Train), helm dan kacamata safety wajib
    if (isZone1 && !helmetGlasses) {
      return false;
    }
    return true;
  }

  Map<String, dynamic> toJson() {
    return {
      'safety_shoes': safetyShoes,
      'long_pants': longPants,
      'collared_shirt_or_coverall': collaredShirtOrCoverall,
      'helmet_glasses': helmetGlasses,
      'specific_hazmat_ppe': specificHazmatPpe,
    };
  }

  factory DriverPpeChecklist.fromJson(Map<String, dynamic> json) {
    return DriverPpeChecklist(
      safetyShoes: json['safety_shoes'] as bool? ?? false,
      longPants: json['long_pants'] as bool? ?? false,
      collaredShirtOrCoverall: json['collared_shirt_or_coverall'] as bool? ?? false,
      helmetGlasses: json['helmet_glasses'] as bool? ?? false,
      specificHazmatPpe: json['specific_hazmat_ppe'] as bool? ?? false,
    );
  }
}

class VehicleInspectionReport {
  final List<CategoryInspectionItem> categories;
  final DriverPpeChecklist driverPpe;
  final String inspectorNip;
  final DateTime inspectionDate;

  VehicleInspectionReport({
    required this.categories,
    required this.driverPpe,
    required this.inspectorNip,
    required this.inspectionDate,
  });

  /// Factory untuk menginisialisasi 11 kategori standar kkb_kks.jpg
  factory VehicleInspectionReport.initStandard({required String inspectorNip}) {
    return VehicleInspectionReport(
      inspectorNip: inspectorNip,
      inspectionDate: DateTime.now(),
      driverPpe: DriverPpeChecklist(),
      categories: [
        CategoryInspectionItem(
          categoryCode: 'CAT_I_ENGINE',
          categoryTitle: 'I. Mesin Utama',
          subItemsDescription: 'Performance mesin, sistem starter, knalpot & emisi',
        ),
        CategoryInspectionItem(
          categoryCode: 'CAT_II_FUEL',
          categoryTitle: 'II. Sistem Bahan Bakar',
          subItemsDescription: 'Tangki bahan bakar, tutup tangki, pipa penyalur BBM',
        ),
        CategoryInspectionItem(
          categoryCode: 'CAT_III_WHEELS',
          categoryTitle: 'III. Sistem Roda',
          subItemsDescription: 'Ban utama, ban cadangan (serep), velg & baut roda',
        ),
        CategoryInspectionItem(
          categoryCode: 'CAT_IV_STEERING',
          categoryTitle: 'IV. Sistem Kemudi',
          subItemsDescription: 'Roda kemudi, clearance (speling), mekanisme kemudi',
        ),
        CategoryInspectionItem(
          categoryCode: 'CAT_V_BRAKES',
          categoryTitle: 'V. Sistem Rem',
          subItemsDescription: 'Rem utama (foot brake) & rem parkir (hand brake)',
        ),
        CategoryInspectionItem(
          categoryCode: 'CAT_VI_ELECTRICAL',
          categoryTitle: 'VI. Sistem Kelistrikan',
          subItemsDescription: 'Kabel busi, kabel accu, tutup accu, fuse box sekring',
        ),
        CategoryInspectionItem(
          categoryCode: 'CAT_VII_LIGHTS',
          categoryTitle: 'VII. Sistem Penerangan',
          subItemsDescription: 'Lampu dekat/jauh, sein, rem, mundur, plat no, lampu kota',
        ),
        CategoryInspectionItem(
          categoryCode: 'CAT_VIII_ACCESSORIES',
          categoryTitle: 'VIII. Komponen Pendukung',
          subItemsDescription: 'Kaca spion kiri/kanan, wiper kaca, klakson, kondisi accu',
        ),
        CategoryInspectionItem(
          categoryCode: 'CAT_IX_SAFETY_ITEMS',
          categoryTitle: 'IX. Kelengkapan Tambahan',
          subItemsDescription: 'Segitiga pengaman, sabuk pengaman seatbelt, kotak P3K',
        ),
        CategoryInspectionItem(
          categoryCode: 'CAT_X_SPECIAL_HAZMAT',
          categoryTitle: 'X. Kelengkapan Khusus',
          subItemsDescription: 'Tabung APAR aktif, plakat B3 & nama bahan kimia',
        ),
        CategoryInspectionItem(
          categoryCode: 'CAT_XI_GENERAL_RULES',
          categoryTitle: 'XI. Ketentuan Umum',
          subItemsDescription: 'Kaca film standar, kaca depan bebas pandang, bak bebas orang',
        ),
      ],
    );
  }

  /// Evaluasi kelulusan seluruh 11 kategori
  bool get isAllCategoriesPassed => categories.every((cat) => cat.isPass);

  /// Evaluasi total lulus termasuk kepatuhan APD untuk zona terkait
  bool isTotalPassed({required bool isZone1}) {
    return isAllCategoriesPassed && driverPpe.isCompliantForZone(isZone1: isZone1);
  }

  List<String> getFailedCategoryNames() {
    return categories
        .where((cat) => !cat.isPass)
        .map((cat) => cat.categoryTitle)
        .toList();
  }

  Map<String, dynamic> toJson() {
    return {
      'inspector_nip': inspectorNip,
      'inspection_date': inspectionDate.toIso8601String(),
      'categories': categories.map((c) => c.toJson()).toList(),
      'driver_ppe': driverPpe.toJson(),
      'is_all_passed': isAllCategoriesPassed,
    };
  }
}

