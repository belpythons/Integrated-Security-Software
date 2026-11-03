import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../core/app_constants.dart';
import '../core/operational_rules.dart';
import '../models/permit_header.dart';
import '../models/vehicle_item.dart';
import '../models/driver_item.dart';
import '../models/inspection_checklist_model.dart';
import '../widgets/checklist_11_points_widget.dart';

class PermitDetailPage extends StatefulWidget {
  final MaterialPermitHeader permit;
  final String inspectorNip;

  const PermitDetailPage({
    super.key,
    required this.permit,
    this.inspectorNip = 'SEC-POSKO-01',
  });

  @override
  State<PermitDetailPage> createState() => _PermitDetailPageState();
}

class _PermitDetailPageState extends State<PermitDetailPage> {
  VehicleItem? _selectedVehicle;
  DriverItem? _selectedDriver;
  late VehicleInspectionReport _inspectionReport;
  final TextEditingController _badgeController = TextEditingController();

  VisitCycleStatus _currentVisitStatus = VisitCycleStatus.none;
  ScreeningResult _screeningResult = ScreeningResult.rejected;
  bool _isKtpHeld = false;

  @override
  void initState() {
    super.initState();
    if (widget.permit.vehicleItems.isNotEmpty) {
      _selectedVehicle = widget.permit.vehicleItems.first;
    }
    if (widget.permit.driverItems.isNotEmpty) {
      _selectedDriver = widget.permit.driverItems.first;
    }
    _inspectionReport = VehicleInspectionReport.initStandard(inspectorNip: widget.inspectorNip);
    _evaluateScreeningStatus();
  }

  void _evaluateScreeningStatus() {
    if (_selectedVehicle == null) return;

    // Evaluasi aturan KKB Bypass
    final canBypass = OperationalRules.resolveKkbBypass(
      hasActiveKkb: _selectedVehicle!.hasActiveKkb,
      adminSkipInspection: _selectedVehicle!.adminSkipInspection,
    );

    if (canBypass) {
      _screeningResult = ScreeningResult.bypassed;
    } else {
      // Re-screening rule: Jika kendaraan checkedOut, wajib screening ulang
      final bool needsScreening = OperationalRules.isReScreeningRequired(
        lastStatus: _currentVisitStatus,
        hasAdminSkipInspection: false,
      );

      if (needsScreening) {
        final bool isZone1 = widget.permit.destinationZone == DestinationZone.zone1;
        final bool isPass = _inspectionReport.isTotalPassed(isZone1: isZone1);
        _screeningResult = isPass ? ScreeningResult.approved : ScreeningResult.rejected;
      }
    }
    setState(() {});
  }

  void _handleCheckIn() {
    if (_selectedVehicle == null || _selectedDriver == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Pilih kendaraan dan sopir dari daftar terdaftar!')),
      );
      return;
    }

    if (_screeningResult == ScreeningResult.rejected) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Kendaraan belum lolos uji fisik 11 kategori! Check-in ditolak.'),
          backgroundColor: AppColors.danger,
        ),
      );
      return;
    }

    if (_badgeController.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Wajib mengisi nomor Visitor Badge yang diserahkan ke sopir!'),
          backgroundColor: AppColors.warning,
        ),
      );
      return;
    }

    setState(() {
      _isKtpHeld = true;
      _currentVisitStatus = VisitCycleStatus.checkedIn;
    });

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Check-In Berhasil! KTP asli ${_selectedDriver!.driverName} ditahan. Visitor Badge #${_badgeController.text.trim()} diserahkan.',
        ),
        backgroundColor: AppColors.success,
      ),
    );
  }

  void _handleCheckOut() {
    setState(() {
      _isKtpHeld = false;
      _currentVisitStatus = VisitCycleStatus.checkedOut;
      _badgeController.clear();
      // Saat check-out, sesuai audio rule: jika nanti masuk lagi, wajib screening ulang
      _evaluateScreeningStatus();
    });

    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Check-Out Selesai! KTP fisik telah dikembalikan ke sopir.'),
        backgroundColor: AppColors.navy,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bool isZone1 = widget.permit.destinationZone == DestinationZone.zone1;

    return Scaffold(
      appBar: AppBar(
        title: Text('Posko Security: ${widget.permit.requestNo}'),
        backgroundColor: AppColors.navy,
        foregroundColor: Colors.white,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Card Detail Tiket & Proteksi Masking Harga
            Card(
              elevation: 2,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: Text(
                            widget.permit.vendorName,
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppColors.blueSubtleBg,
                            borderRadius: BorderRadius.circular(6),
                            border: Border.all(color: AppColors.blueSubtleBorder),
                          ),
                          child: Text(
                            widget.permit.permitBasis.label,
                            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.blue),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Tujuan: ${widget.permit.destinationZone.label}',
                      style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
                    ),
                    const Divider(height: 20),
                    const Text(
                      'Daftar Barang Bawaan (Price-Masked View):',
                      style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                    ),
                    const SizedBox(height: 6),
                    ...widget.permit.poDoItems.map((item) {
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 6),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Icon(Icons.inventory_2_outlined, size: 16, color: AppColors.textSecondary),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                '${item.poDescription} (Ref: ${item.poNumber})',
                                style: const TextStyle(fontSize: 13),
                              ),
                            ),
                            // Price Masking Badge
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: Colors.grey.shade200,
                                borderRadius: BorderRadius.circular(4),
                              ),
                              child: const Text(
                                'HARGA DIRAHASIAKAN',
                                style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: Colors.grey),
                              ),
                            ),
                          ],
                        ),
                      );
                    }),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Dropdown Non-Free Text: Kendaraan & Sopir
            Card(
              elevation: 2,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Pilih Armada & Personil di Posko (Non-Free Text)',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<VehicleItem>(
                      decoration: const InputDecoration(
                        labelText: 'Plat Nomor Kendaraan (Hanya Terdaftar)',
                        border: OutlineInputBorder(),
                        isDense: true,
                        prefixIcon: Icon(Icons.local_shipping_outlined),
                      ),
                      initialValue: _selectedVehicle,
                      items: widget.permit.vehicleItems.map((v) {
                        return DropdownMenuItem<VehicleItem>(
                          value: v,
                          child: Text('${v.licensePlateNo} (${v.vehicleCategory} - ${v.vehicleBrand})'),
                        );
                      }).toList(),
                      onChanged: (val) {
                        _selectedVehicle = val;
                        _evaluateScreeningStatus();
                      },
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<DriverItem>(
                      decoration: const InputDecoration(
                        labelText: 'Nama Pengemudi / Kenek (Hanya Terdaftar)',
                        border: OutlineInputBorder(),
                        isDense: true,
                        prefixIcon: Icon(Icons.person_outline),
                      ),
                      initialValue: _selectedDriver,
                      items: widget.permit.driverItems.map((d) {
                        return DropdownMenuItem<DriverItem>(
                          value: d,
                          child: Text('${d.driverName} [${d.personnelRole}] - ${d.driverOriginType}'),
                        );
                      }).toList(),
                      onChanged: (val) {
                        _selectedDriver = val;
                        _evaluateScreeningStatus();
                      },
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Section Kelaikan Fisik / Bypass
            if (_selectedVehicle != null && _selectedVehicle!.isInspectionBypassed)
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.successBg,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppColors.success),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.verified, color: AppColors.success, size: 36),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'KKB STICKER BYPASS AKTIF',
                            style: TextStyle(fontWeight: FontWeight.bold, color: AppColors.success),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Kendaraan ${_selectedVehicle!.licensePlateNo} berstiker KKB (${_selectedVehicle!.kkbStickerNo ?? "Valid"}) & verifikasi Admin. Bebas uji fisik 11 pilar.',
                            style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              )
            else
              Checklist11PointsWidget(
                report: _inspectionReport,
                isZone1: isZone1,
                onChanged: (updatedReport) {
                  _inspectionReport = updatedReport;
                  _evaluateScreeningStatus();
                },
              ),
            const SizedBox(height: 16),

            // Section Penukaran KTP & Gate Check-In/Check-Out
            Card(
              elevation: 2,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Expanded(
                          child: Text(
                            'Kontrol Gerbang & Penahanan KTP',
                            style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                          decoration: BoxDecoration(
                            color: _currentVisitStatus == VisitCycleStatus.checkedIn
                                ? AppColors.successBg
                                : AppColors.blueSubtleBg,
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            _currentVisitStatus.label,
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color: _currentVisitStatus == VisitCycleStatus.checkedIn
                                  ? AppColors.success
                                  : AppColors.blue,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const Divider(height: 20),
                    if (_currentVisitStatus != VisitCycleStatus.checkedIn) ...[
                      TextFormField(
                        controller: _badgeController,
                        decoration: const InputDecoration(
                          labelText: 'Nomor Seri Visitor Badge (Diserahkan ke Sopir)',
                          hintText: 'Contoh: V-BADGE-0921',
                          border: OutlineInputBorder(),
                          prefixIcon: Icon(Icons.badge_outlined),
                        ),
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Icon(
                            Icons.assignment_ind,
                            color: _isKtpHeld ? AppColors.warning : AppColors.textSecondary,
                            size: 20,
                          ),
                          const SizedBox(width: 8),
                          const Expanded(
                            child: Text(
                              'Sopir WAJIB menitipkan KTP Asli di posko gerbang.',
                              style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 14),
                      ElevatedButton.icon(
                        onPressed: _handleCheckIn,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.blue,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(vertical: 12),
                        ),
                        icon: const Icon(Icons.login),
                        label: const Text('PROSES CHECK-IN & TAHAN KTP'),
                      ),
                    ] else ...[
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AppColors.warningBg,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: AppColors.warning),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.lock, color: AppColors.warning),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Text(
                                'KTP Asli Sopir ${_selectedDriver?.driverName ?? ""} sedang DITAHAN di posko.\nVisitor Badge: ${_badgeController.text}',
                                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 14),
                      ElevatedButton.icon(
                        onPressed: _handleCheckOut,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.navy,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(vertical: 12),
                        ),
                        icon: const Icon(Icons.logout),
                        label: const Text('PROSES CHECK-OUT & KEMBALIKAN KTP'),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
