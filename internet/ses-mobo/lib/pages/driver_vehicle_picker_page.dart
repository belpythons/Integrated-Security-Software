import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../services/mock_permit_service.dart';
import 'inspection_page.dart';
import 'reentry_page.dart';

class DriverVehiclePickerPage extends StatefulWidget {
  final String requestId;

  const DriverVehiclePickerPage({super.key, required this.requestId});

  @override
  State<DriverVehiclePickerPage> createState() => _DriverVehiclePickerPageState();
}

class _DriverVehiclePickerPageState extends State<DriverVehiclePickerPage> {
  final _service = MockPermitService();
  String? _selectedDriverId;
  String? _selectedVehicleId;

  void _onContinue() {
    if (_selectedDriverId == null || _selectedVehicleId == null) return;

    // Check if this pair was already inspected and passed today
    final prevInspection = _service.inspectedToday(
      widget.requestId,
      _selectedDriverId!,
      _selectedVehicleId!,
    );

    if (prevInspection != null) {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => ReentryPage(
            requestId: widget.requestId,
            driverId: _selectedDriverId!,
            vehicleId: _selectedVehicleId!,
            previousLogId: prevInspection.id,
          ),
        ),
      );
    } else {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => InspectionPage(
            requestId: widget.requestId,
            driverId: _selectedDriverId!,
            vehicleId: _selectedVehicleId!,
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final req = _service.getRequest(widget.requestId);
    if (req == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Pilih Pengemudi & Kendaraan')),
        body: const Center(child: Text('Izin tidak ditemukan')),
      );
    }

    final isReady = _selectedDriverId != null && _selectedVehicleId != null;

    return Scaffold(
      backgroundColor: AppColors.bg,
      appBar: AppBar(
        title: const Text('Pilih Pengemudi & Kendaraan'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 20),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 1. Driver Selection
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                child: Text(
                  'PENGEMUDI',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.label2, letterSpacing: 0.5),
                ),
              ),
              Container(
                decoration: BoxDecoration(
                  color: AppColors.card,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.sep, width: 0.5),
                ),
                child: Column(
                  children: req.driverIds.asMap().entries.map((entry) {
                    final idx = entry.key;
                    final id = entry.value;
                    final drv = _service.getDriver(id);
                    final blockReason = _service.drvBlock(req.id, id);
                    final isSelected = _selectedDriverId == id;
                    final isDisabled = blockReason != null;

                    return Column(
                      children: [
                        if (idx > 0) const Divider(height: 1, indent: 56, color: AppColors.sep),
                        InkWell(
                          onTap: isDisabled ? null : () => setState(() => _selectedDriverId = id),
                          borderRadius: BorderRadius.vertical(
                            top: idx == 0 ? const Radius.circular(16) : Radius.zero,
                            bottom: idx == req.driverIds.length - 1 ? const Radius.circular(16) : Radius.zero,
                          ),
                          child: Opacity(
                            opacity: isDisabled ? 0.45 : 1.0,
                            child: Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                              child: Row(
                                children: [
                                  Container(
                                    width: 32,
                                    height: 32,
                                    decoration: BoxDecoration(color: AppColors.blue, borderRadius: BorderRadius.circular(8)),
                                    child: const Icon(Icons.person, color: Colors.white, size: 18),
                                  ),
                                  const SizedBox(width: 14),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          drv?.name ?? id,
                                          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15, color: AppColors.label),
                                        ),
                                        const SizedBox(height: 2),
                                        Text(
                                          isDisabled
                                              ? blockReason
                                              : 'NIK ${drv?.nik.substring(0, 6)}••••• · SIM ${drv?.simType}',
                                          style: TextStyle(
                                            fontSize: 12,
                                            color: isDisabled ? AppColors.red : AppColors.label2,
                                            fontWeight: isDisabled ? FontWeight.w600 : FontWeight.normal,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                  if (isSelected)
                                    const Icon(Icons.check_circle, color: AppColors.blue, size: 22)
                                  else if (!isDisabled)
                                    const Icon(Icons.circle_outlined, color: AppColors.label3, size: 22),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ],
                    );
                  }).toList(),
                ),
              ),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                child: Text(
                  'Cocokkan wajah pengemudi serta fisik KTP/SIM dengan data yang terdaftar.',
                  style: TextStyle(fontSize: 12, color: AppColors.label2),
                ),
              ),
              const SizedBox(height: 20),

              // 2. Vehicle Selection
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                child: Text(
                  'KENDARAAN',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.label2, letterSpacing: 0.5),
                ),
              ),
              Container(
                decoration: BoxDecoration(
                  color: AppColors.card,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.sep, width: 0.5),
                ),
                child: Column(
                  children: req.vehicleIds.asMap().entries.map((entry) {
                    final idx = entry.key;
                    final id = entry.value;
                    final veh = _service.getVehicle(id);
                    final blockReason = _service.vehBlock(req.id, id);
                    final isSelected = _selectedVehicleId == id;
                    final isDisabled = blockReason != null;

                    return Column(
                      children: [
                        if (idx > 0) const Divider(height: 1, indent: 56, color: AppColors.sep),
                        InkWell(
                          onTap: isDisabled ? null : () => setState(() => _selectedVehicleId = id),
                          borderRadius: BorderRadius.vertical(
                            top: idx == 0 ? const Radius.circular(16) : Radius.zero,
                            bottom: idx == req.vehicleIds.length - 1 ? const Radius.circular(16) : Radius.zero,
                          ),
                          child: Opacity(
                            opacity: isDisabled ? 0.45 : 1.0,
                            child: Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                              child: Row(
                                children: [
                                  Container(
                                    width: 32,
                                    height: 32,
                                    decoration: BoxDecoration(color: AppColors.indigo, borderRadius: BorderRadius.circular(8)),
                                    child: const Icon(Icons.local_shipping, color: Colors.white, size: 18),
                                  ),
                                  const SizedBox(width: 14),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          veh?.plate ?? id,
                                          style: const TextStyle(
                                            fontFamily: 'monospace',
                                            fontWeight: FontWeight.w700,
                                            fontSize: 15,
                                            color: AppColors.label,
                                          ),
                                        ),
                                        const SizedBox(height: 2),
                                        Text(
                                          isDisabled
                                              ? blockReason
                                              : '${veh?.type} · ${veh?.brand}',
                                          style: TextStyle(
                                            fontSize: 12,
                                            color: isDisabled ? AppColors.red : AppColors.label2,
                                            fontWeight: isDisabled ? FontWeight.w600 : FontWeight.normal,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                  if (isSelected)
                                    const Icon(Icons.check_circle, color: AppColors.blue, size: 22)
                                  else if (!isDisabled)
                                    const Icon(Icons.circle_outlined, color: AppColors.label3, size: 22),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ],
                    );
                  }).toList(),
                ),
              ),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                child: Text(
                  'Hanya pengemudi dan kendaraan yang tercantum pada dokumen izin ini yang dapat dipilih.',
                  style: TextStyle(fontSize: 12, color: AppColors.label2),
                ),
              ),
              const SizedBox(height: 32),

              // 3. Continue Button
              ElevatedButton(
                onPressed: isReady ? _onContinue : null,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.blue,
                  foregroundColor: Colors.white,
                  minimumSize: const Size(double.infinity, 52),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                ),
                child: const Text('Lanjutkan Pemeriksaan Fisik', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

