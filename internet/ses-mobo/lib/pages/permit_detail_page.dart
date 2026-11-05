import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../models/permit_header.dart';
import '../services/mock_permit_service.dart';
import 'driver_vehicle_picker_page.dart';
import 'checkout_page.dart';

class PermitDetailPage extends StatefulWidget {
  final String? permitId;
  final MaterialPermitHeader? permit;
  final String? inspectorNip;

  const PermitDetailPage({
    super.key,
    this.permitId,
    this.permit,
    this.inspectorNip,
  });

  @override
  State<PermitDetailPage> createState() => _PermitDetailPageState();
}

class _PermitDetailPageState extends State<PermitDetailPage> {
  // Legacy Badak KTP state
  final _badgeController = TextEditingController();
  bool _isCheckedIn = false;
  String? _heldKtpDriver;
  String? _currentBadge;

  String _formatDate(DateTime dt) {
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    return '${dt.day} ${months[dt.month - 1]} ${dt.year}';
  }

  @override
  Widget build(BuildContext context) {
    if (widget.permit != null) {
      return _buildLegacyBadakView(context, widget.permit!);
    }
    return _buildGateSecurityM3View(context, widget.permitId ?? 'IMB-2026-000123');
  }

  // --- 1. LEGACY BADAK POSKO VIEW (Maintains 100% stage_07 test compliance) ---
  Widget _buildLegacyBadakView(BuildContext context, MaterialPermitHeader permit) {
    final driverName = permit.driverItems.isNotEmpty ? permit.driverItems.first.driverName : 'Sopir';
    final vehiclePlate = permit.vehicleItems.isNotEmpty ? permit.vehicleItems.first.licensePlateNo : '–';
    final hasKkbBypass = permit.vehicleItems.any((v) => v.isInspectionBypassed);

    return Scaffold(
      backgroundColor: AppColors.canvas,
      appBar: AppBar(
        title: Text('Detail Izin ${permit.requestNo}'),
        backgroundColor: AppColors.navy,
        foregroundColor: Colors.white,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Status Header
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12)),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(permit.vendorName, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 4),
                  Text('No. Dokumen Ref: ${permit.refDocNo} · Tujuan: ${permit.destinationZone.name}'),
                ],
              ),
            ),
            const SizedBox(height: 12),

            // KKB Bypass Banner
            if (hasKkbBypass) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(color: AppColors.successBg, borderRadius: BorderRadius.circular(8)),
                child: const Row(
                  children: [
                    Icon(Icons.verified, color: AppColors.success),
                    SizedBox(width: 8),
                    Text(
                      'KKB STICKER BYPASS AKTIF',
                      style: TextStyle(color: AppColors.success, fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
            ],

            // Cargo Items (Price Masked)
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12)),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Daftar Muatan Barang Masuk', style: TextStyle(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  ...permit.poDoItems.map(
                    (item) => Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(child: Text('${item.poDescription} (Ref: ${item.poNumber})')),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                            decoration: BoxDecoration(color: Colors.grey.shade200, borderRadius: BorderRadius.circular(4)),
                            child: const Text('HARGA DIRAHASIAKAN', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // KTP Holding Check-In / Check-Out Workflow
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12)),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Operasional Gerbang Posko', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                  const SizedBox(height: 12),
                  if (!_isCheckedIn) ...[
                    TextFormField(
                      controller: _badgeController,
                      decoration: const InputDecoration(
                        labelText: 'Nomor Seri Visitor Badge (Diserahkan ke Sopir)',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton(
                      onPressed: () {
                        setState(() {
                          _isCheckedIn = true;
                          _currentBadge = _badgeController.text;
                          _heldKtpDriver = driverName;
                        });
                      },
                      style: ElevatedButton.styleFrom(backgroundColor: AppColors.blue, foregroundColor: Colors.white),
                      child: const Text('PROSES CHECK-IN & TAHAN KTP'),
                    ),
                  ] else ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(color: AppColors.warningBg, borderRadius: BorderRadius.circular(8)),
                      child: Text(
                        'KTP Asli Sopir $_heldKtpDriver sedang DITAHAN di posko. Badge Aktif: $_currentBadge (Kendaraan: $vehiclePlate)',
                        style: const TextStyle(color: AppColors.warning, fontWeight: FontWeight.w600),
                      ),
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton(
                      onPressed: () {
                        setState(() {
                          _isCheckedIn = false;
                          _badgeController.clear();
                          _heldKtpDriver = null;
                          _currentBadge = null;
                        });
                      },
                      style: ElevatedButton.styleFrom(backgroundColor: AppColors.success, foregroundColor: Colors.white),
                      child: const Text('PROSES CHECK-OUT & KEMBALIKAN KTP'),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // --- 2. GATE SECURITY MATERIAL 3 VIEW ---
  Widget _buildGateSecurityM3View(BuildContext context, String permitId) {
    final service = MockPermitService();
    final state = service.permitState(permitId);
    final req = service.getRequest(permitId);

    final onSiteList = service.inside().where((l) => l.requestId == permitId).toList();
    final hasAvailablePair = req != null &&
        req.driverIds.any((id) => service.drvBlock(req.id, id) == null) &&
        req.vehicleIds.any((id) => service.vehBlock(req.id, id) == null);

    return Scaffold(
      backgroundColor: AppColors.bg,
      appBar: AppBar(
        title: const Text('Detail Izin Masuk Barang'),
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
              // Status Header Card
              Container(
                decoration: BoxDecoration(
                  color: AppColors.card,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.sep, width: 0.5),
                ),
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Container(
                      width: 52,
                      height: 52,
                      decoration: BoxDecoration(color: state.color, shape: BoxShape.circle),
                      child: Icon(state.icon, color: Colors.white, size: 30),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            permitId,
                            style: const TextStyle(
                              fontFamily: 'monospace',
                              fontSize: 14,
                              color: AppColors.label2,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            state.title,
                            style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: state.color),
                          ),
                          if (req != null) ...[
                            const SizedBox(height: 2),
                            Text(req.vendor, style: const TextStyle(fontSize: 14, color: AppColors.label2)),
                          ],
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              if (service.currentGate != null &&
                  (service.currentGate!.contains('Warehouse') || service.currentGate!.contains('Zona 2')) &&
                  req != null) ...[
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: service.isGateZoneCompliant(service.currentGate!, req)
                        ? AppColors.okBg
                        : AppColors.warnBg,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                      color: service.isGateZoneCompliant(service.currentGate!, req)
                          ? AppColors.ok
                          : AppColors.warn,
                      width: 0.5,
                    ),
                  ),
                  child: Row(
                    children: [
                      Icon(
                        service.isGateZoneCompliant(service.currentGate!, req)
                            ? Icons.shield_outlined
                            : Icons.warning_amber_rounded,
                        size: 18,
                        color: service.isGateZoneCompliant(service.currentGate!, req)
                            ? AppColors.ok
                            : AppColors.warn,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          service.isGateZoneCompliant(service.currentGate!, req)
                              ? 'Warehouse Gate (Zona 2): Destinasi terverifikasi area logistik kilang (Wajib APD Lengkap).'
                              : 'Perhatian: Izin ini bukan untuk area pergudangan Zona 2. Pastikan rute tujuan benar.',
                          style: TextStyle(
                            fontSize: 12,
                            color: service.isGateZoneCompliant(service.currentGate!, req)
                                ? AppColors.ok
                                : AppColors.warn,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 14),

              if (!state.isValid) ...[
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: state.color.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: state.color.withValues(alpha: 0.3), width: 0.5),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.info_outline, color: state.color, size: 22),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(state.message, style: TextStyle(color: state.color, fontSize: 14, height: 1.35)),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
              ],

              // On Site Now Section
              if (onSiteList.isNotEmpty) ...[
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                  child: Text(
                    'KENDARAAN DI AREA · CHECK OUT',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.label2, letterSpacing: 0.5),
                  ),
                ),
                Material(
                  color: AppColors.card,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                    side: const BorderSide(color: AppColors.sep, width: 0.5),
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    children: onSiteList.map((log) {
                      final veh = service.getVehicle(log.vehicleId);
                      final drv = service.getDriver(log.driverId);
                      return ListTile(
                        leading: Container(
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(color: AppColors.green, borderRadius: BorderRadius.circular(8)),
                          child: const Icon(Icons.local_shipping, color: Colors.white, size: 20),
                        ),
                        title: Text('${veh?.plate ?? log.vehicleId} · ${drv?.name ?? log.driverId}',
                            style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                        subtitle: Text(
                          'Masuk sejak ${log.timestamp.hour.toString().padLeft(2, '0')}:${log.timestamp.minute.toString().padLeft(2, '0')} · ${log.gate}',
                          style: const TextStyle(fontSize: 12, color: AppColors.label2),
                        ),
                        trailing: ElevatedButton(
                          onPressed: () {
                            Navigator.of(context).push(
                              MaterialPageRoute(builder: (_) => CheckoutPage(logId: log.id)),
                            );
                          },
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AppColors.blueBg,
                            foregroundColor: AppColors.blue,
                            elevation: 0,
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                            minimumSize: const Size(0, 36),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                          ),
                          child: const Text('Check Out', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                        ),
                      );
                    }).toList(),
                  ),
                ),
                const SizedBox(height: 16),
              ],

              // Check-In Actions
              if (state.isValid && req != null) ...[
                ElevatedButton.icon(
                  onPressed: hasAvailablePair
                      ? () {
                          Navigator.of(context).push(
                            MaterialPageRoute(builder: (_) => DriverVehiclePickerPage(requestId: req.id)),
                          );
                        }
                      : null,
                  icon: const Icon(Icons.login, size: 20),
                  label: const Text('Mulai Check-In', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.blue,
                    foregroundColor: Colors.white,
                    minimumSize: const Size(double.infinity, 52),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                ),
                const SizedBox(height: 20),
              ],

              // Permit Details
              if (req != null) ...[
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                  child: Text(
                    'INFORMASI PERIZINAN',
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
                    children: [
                      _buildRow('Masa Berlaku Pengiriman', '${_formatDate(req.startDate)} – ${_formatDate(req.endDate)}'),
                      const Divider(height: 1, indent: 16, color: AppColors.sep),
                      _buildRow('No. PO', req.poNumber, isMono: true),
                      const Divider(height: 1, indent: 16, color: AppColors.sep),
                      _buildRow('No. DO', req.doNumber, isMono: true),
                      const Divider(height: 1, indent: 16, color: AppColors.sep),
                      _buildRow('Lokasi Tujuan Kilang', req.plantDestination),
                    ],
                  ),
                ),
                const SizedBox(height: 20),

                // Registered Drivers
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                  child: Text(
                    'PENGEMUDI TERDAFTAR',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.label2, letterSpacing: 0.5),
                  ),
                ),
                Material(
                  color: AppColors.card,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                    side: const BorderSide(color: AppColors.sep, width: 0.5),
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    children: req.driverIds.asMap().entries.map((entry) {
                      final idx = entry.key;
                      final id = entry.value;
                      final drv = service.getDriver(id);
                      final blockReason = service.drvBlock(req.id, id);

                      return Column(
                        children: [
                          if (idx > 0) const Divider(height: 1, indent: 56, color: AppColors.sep),
                          ListTile(
                            leading: Container(
                              width: 32,
                              height: 32,
                              decoration: BoxDecoration(color: AppColors.blue, borderRadius: BorderRadius.circular(8)),
                              child: const Icon(Icons.person, color: Colors.white, size: 18),
                            ),
                            title: Text(drv?.name ?? id, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
                            subtitle: Text(
                              'SIM ${drv?.simType ?? "–"} · berlaku s/d ${drv != null ? _formatDate(drv.simValidUntil) : "–"}',
                              style: const TextStyle(fontSize: 12, color: AppColors.label2),
                            ),
                            trailing: blockReason != null
                                ? Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                    decoration: BoxDecoration(
                                      color: blockReason.startsWith('Di') ? AppColors.greenBg : AppColors.redBg,
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    child: Text(
                                      blockReason.startsWith('Di') ? 'Di area' : 'Kadaluarsa',
                                      style: TextStyle(
                                        color: blockReason.startsWith('Di') ? AppColors.green : AppColors.red,
                                        fontSize: 11,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  )
                                : const Icon(Icons.check_circle_outline, color: AppColors.green, size: 20),
                          ),
                        ],
                      );
                    }).toList(),
                  ),
                ),
                const SizedBox(height: 20),

                // Registered Vehicles
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                  child: Text(
                    'KENDARAAN TERDAFTAR',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.label2, letterSpacing: 0.5),
                  ),
                ),
                Material(
                  color: AppColors.card,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                    side: const BorderSide(color: AppColors.sep, width: 0.5),
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    children: req.vehicleIds.asMap().entries.map((entry) {
                      final idx = entry.key;
                      final id = entry.value;
                      final veh = service.getVehicle(id);
                      final blockReason = service.vehBlock(req.id, id);

                      return Column(
                        children: [
                          if (idx > 0) const Divider(height: 1, indent: 56, color: AppColors.sep),
                          ListTile(
                            leading: Container(
                              width: 32,
                              height: 32,
                              decoration: BoxDecoration(color: AppColors.indigo, borderRadius: BorderRadius.circular(8)),
                              child: const Icon(Icons.local_shipping, color: Colors.white, size: 18),
                            ),
                            title: Text(
                              veh?.plate ?? id,
                              style: const TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.w700, fontSize: 15),
                            ),
                            subtitle: Text('${veh?.type ?? ""} · ${veh?.brand ?? ""}',
                                style: const TextStyle(fontSize: 12, color: AppColors.label2)),
                            trailing: blockReason != null
                                ? Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                    decoration: BoxDecoration(
                                      color: blockReason.startsWith('Di') ? AppColors.greenBg : AppColors.redBg,
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    child: Text(
                                      blockReason.startsWith('Di') ? 'Di area' : 'Kadaluarsa',
                                      style: TextStyle(
                                        color: blockReason.startsWith('Di') ? AppColors.green : AppColors.red,
                                        fontSize: 11,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  )
                                : const Icon(Icons.check_circle_outline, color: AppColors.green, size: 20),
                          ),
                        ],
                      );
                    }).toList(),
                  ),
                ),
                const SizedBox(height: 20),

                // Items (Price Masked!)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                  child: Text(
                    'DAFTAR MUATAN BARANG (${req.items.length})',
                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.label2, letterSpacing: 0.5),
                  ),
                ),
                Container(
                  decoration: BoxDecoration(
                    color: AppColors.card,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: AppColors.sep, width: 0.5),
                  ),
                  child: Column(
                    children: req.items.asMap().entries.map((entry) {
                      final idx = entry.key;
                      final item = entry.value;

                      return Column(
                        children: [
                          if (idx > 0) const Divider(height: 1, indent: 16, color: AppColors.sep),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                            child: Row(
                              children: [
                                Expanded(child: Text(item.name, style: const TextStyle(fontSize: 14))),
                                const SizedBox(width: 12),
                                Text(
                                  '${item.qty} ${item.uom}',
                                  style: const TextStyle(
                                    fontFamily: 'monospace',
                                    fontWeight: FontWeight.w600,
                                    fontSize: 14,
                                    color: AppColors.label2,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      );
                    }).toList(),
                  ),
                ),
                const SizedBox(height: 24),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildRow(String label, String value, {bool isMono = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 15, color: AppColors.label)),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: TextStyle(
                fontSize: 15,
                color: AppColors.label2,
                fontFamily: isMono ? 'monospace' : null,
                fontWeight: isMono ? FontWeight.w600 : FontWeight.normal,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
