import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../services/mock_permit_service.dart';
import 'inspection_page.dart';
import 'result_page.dart';

class ReentryPage extends StatelessWidget {
  final String requestId;
  final String driverId;
  final String vehicleId;
  final int previousLogId;

  const ReentryPage({
    super.key,
    required this.requestId,
    required this.driverId,
    required this.vehicleId,
    required this.previousLogId,
  });

  @override
  Widget build(BuildContext context) {
    final service = MockPermitService();
    final driver = service.getDriver(driverId);
    final vehicle = service.getVehicle(vehicleId);
    final prevLog = service.activityLogs.cast().firstWhere(
          (l) => l.id == previousLogId,
          orElse: () => null,
        );

    final timeStr = prevLog != null
        ? '${prevLog.timestamp.hour.toString().padLeft(2, '0')}:${prevLog.timestamp.minute.toString().padLeft(2, '0')}'
        : '07:40';
    final officerStr = prevLog?.officer ?? 'Petugas Security';
    final gateStr = prevLog?.gate ?? 'Gate 1';

    return Scaffold(
      backgroundColor: AppColors.bg,
      appBar: AppBar(
        title: const Text('Masuk Kembali (Re-entry)'),
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
              // 1. SOP Re-entry Green Banner
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.greenBg,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.green.withValues(alpha: 0.3), width: 0.5),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.verified_user, color: AppColors.green, size: 26),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Telah Lolos Inspeksi Hari Ini',
                            style: TextStyle(
                              color: AppColors.green,
                              fontWeight: FontWeight.w700,
                              fontSize: 16,
                            ),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            '${driver?.name ?? driverId} dengan kendaraan ${vehicle?.plate ?? vehicleId} telah lulus inspeksi hari ini pukul $timeStr oleh $officerStr ($gateStr). Sesuai SOP PT Badak NGL, pengemudi dan kendaraan yang sama dapat masuk kembali pada hari yang sama tanpa perlu inspeksi fisik ulang.',
                            style: const TextStyle(
                              color: AppColors.label,
                              fontSize: 14,
                              height: 1.4,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 20),

              // 2. Summary Group Card
              Container(
                decoration: BoxDecoration(
                  color: AppColors.card,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.sep, width: 0.5),
                ),
                child: Column(
                  children: [
                    _buildRow('Pengemudi', driver?.name ?? driverId),
                    const Divider(height: 1, indent: 16, color: AppColors.sep),
                    _buildRow('Kendaraan', vehicle?.plate ?? vehicleId, isMono: true),
                    const Divider(height: 1, indent: 16, color: AppColors.sep),
                    _buildRow('Inspeksi Pertama', '$timeStr · Laik'),
                  ],
                ),
              ),

              const SizedBox(height: 36),

              // 3. Confirm Check-In Button
              ElevatedButton.icon(
                onPressed: () {
                  final log = service.commitCheckin(
                    requestId: requestId,
                    driverId: driverId,
                    vehicleId: vehicleId,
                    isFit: true,
                    fastReentry: true,
                    summary: 'Re-entry · lolos inspeksi hari ini',
                  );

                  Navigator.of(context).pushReplacement(
                    MaterialPageRoute(builder: (_) => ResultPage(logId: log.id)),
                  );
                },
                icon: const Icon(Icons.login, size: 20),
                label: const Text('Konfirmasi Check-In (Re-entry)', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.green,
                  foregroundColor: Colors.white,
                  minimumSize: const Size(double.infinity, 52),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                ),
              ),

              const SizedBox(height: 14),

              // 4. Inspect Again Instead Button
              TextButton(
                onPressed: () {
                  Navigator.of(context).pushReplacement(
                    MaterialPageRoute(
                      builder: (_) => InspectionPage(
                        requestId: requestId,
                        driverId: driverId,
                        vehicleId: vehicleId,
                      ),
                    ),
                  );
                },
                child: const Text(
                  'Lakukan Inspeksi Ulang',
                  style: TextStyle(
                    color: AppColors.blue,
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
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
          const SizedBox(width: 8),
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

