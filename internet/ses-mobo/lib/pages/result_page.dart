import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../models/gate_app_models.dart';
import '../services/mock_permit_service.dart';
import 'scanner_page.dart';

class ResultPage extends StatelessWidget {
  final int logId;

  const ResultPage({super.key, required this.logId});

  @override
  Widget build(BuildContext context) {
    final service = MockPermitService();
    final log = service.activityLogs.cast().firstWhere(
          (l) => l.id == logId,
          orElse: () => null,
        );

    if (log == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Hasil Pemeriksaan')),
        body: const Center(child: Text('Catatan log tidak ditemukan')),
      );
    }

    final driver = service.getDriver(log.driverId);
    final vehicle = service.getVehicle(log.vehicleId);
    final plate = vehicle?.plate ?? log.vehicleId;
    final timeStr = '${log.timestamp.hour.toString().padLeft(2, '0')}:${log.timestamp.minute.toString().padLeft(2, '0')}';

    Color statusColor = AppColors.green;
    IconData statusIcon = Icons.check;
    String statusTitle = 'Check-In Berhasil';
    String statusSubtitle = '$plate diizinkan memasuki area kilang.';

    switch (log.type) {
      case GateLogType.checkIn:
        statusColor = AppColors.green;
        statusIcon = Icons.check;
        statusTitle = 'Check-In Berhasil';
        statusSubtitle = '$plate diizinkan memasuki area kilang.';
        break;
      case GateLogType.checkOut:
        statusColor = AppColors.blue;
        statusIcon = Icons.logout;
        statusTitle = 'Check-Out Selesai';
        statusSubtitle = '$plate telah meninggalkan area kilang.';
        break;
      case GateLogType.rejected:
        statusColor = AppColors.red;
        statusIcon = Icons.close;
        statusTitle = 'Check-In Ditolak';
        statusSubtitle = '$plate tidak diizinkan memasuki area kilang.';
        break;
    }

    return Scaffold(
      backgroundColor: AppColors.bg,
      appBar: AppBar(
        automaticallyImplyLeading: false,
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).popUntil((route) => route.isFirst),
            child: const Text('Selesai', style: TextStyle(color: AppColors.blue, fontSize: 16, fontWeight: FontWeight.w700)),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 1. Big Status Circle & Title
              Center(
                child: Column(
                  children: [
                    Container(
                      width: 90,
                      height: 90,
                      decoration: BoxDecoration(
                        color: statusColor,
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: statusColor.withValues(alpha: 0.3),
                            blurRadius: 16,
                            offset: const Offset(0, 6),
                          ),
                        ],
                      ),
                      child: Icon(statusIcon, color: Colors.white, size: 50),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      statusTitle,
                      style: const TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.w700,
                        color: AppColors.label,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      statusSubtitle,
                      style: const TextStyle(
                        fontSize: 15,
                        color: AppColors.label2,
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 28),

              // 2. Summary Group Card
              Container(
                decoration: BoxDecoration(
                  color: AppColors.card,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.sep, width: 0.5),
                ),
                child: Column(
                  children: [
                    _buildRow('Waktu', timeStr, isMono: true),
                    const Divider(height: 1, indent: 16, color: AppColors.sep),
                    _buildRow('Nomor Izin', log.requestId, isMono: true),
                    const Divider(height: 1, indent: 16, color: AppColors.sep),
                    _buildRow('Pengemudi', driver?.name ?? log.driverId),
                    const Divider(height: 1, indent: 16, color: AppColors.sep),
                    _buildRow('Kendaraan', plate, isMono: true),
                    const Divider(height: 1, indent: 16, color: AppColors.sep),
                    _buildRow('Pos Gerbang', log.gate),
                    if (log.summary != null) ...[
                      const Divider(height: 1, indent: 16, color: AppColors.sep),
                      _buildRow('Hasil Pemeriksaan', log.summary!),
                    ],
                  ],
                ),
              ),

              if (log.remarks != null && log.remarks!.isNotEmpty) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: log.type == GateLogType.rejected ? AppColors.redBg : AppColors.blueBg,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(
                      color: (log.type == GateLogType.rejected ? AppColors.red : AppColors.blue).withValues(alpha: 0.3),
                      width: 0.5,
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Catatan Petugas',
                        style: TextStyle(
                          color: log.type == GateLogType.rejected ? AppColors.red : AppColors.blue,
                          fontWeight: FontWeight.w700,
                          fontSize: 13,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        log.remarks!,
                        style: const TextStyle(color: AppColors.label, fontSize: 14),
                      ),
                    ],
                  ),
                ),
              ],

              const SizedBox(height: 32),

              // 3. Scan Next Button
              OutlinedButton.icon(
                onPressed: () {
                  Navigator.of(context).pushReplacement(
                    MaterialPageRoute(builder: (_) => const ScannerPage()),
                  );
                },
                icon: const Icon(Icons.qr_code_scanner, color: AppColors.blue, size: 20),
                label: const Text(
                  'Pindai Izin Berikutnya',
                  style: TextStyle(color: AppColors.blue, fontSize: 16, fontWeight: FontWeight.w600),
                ),
                style: OutlinedButton.styleFrom(
                  backgroundColor: AppColors.card,
                  side: const BorderSide(color: AppColors.sep, width: 0.5),
                  minimumSize: const Size(double.infinity, 52),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                ),
              ),

              const SizedBox(height: 12),

              // Done Button
              ElevatedButton(
                onPressed: () => Navigator.of(context).popUntil((route) => route.isFirst),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.blue,
                  foregroundColor: Colors.white,
                  minimumSize: const Size(double.infinity, 52),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                ),
                child: const Text('Kembali ke Pos Gerbang', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
              ),
              const SizedBox(height: 20),
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
                fontSize: 14,
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

