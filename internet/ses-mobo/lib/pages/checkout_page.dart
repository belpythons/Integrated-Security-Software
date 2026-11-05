import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../services/mock_permit_service.dart';
import 'result_page.dart';

class CheckoutPage extends StatefulWidget {
  final int logId;

  const CheckoutPage({super.key, required this.logId});

  @override
  State<CheckoutPage> createState() => _CheckoutPageState();
}

class _CheckoutPageState extends State<CheckoutPage> {
  bool _badgeReturned = true;
  bool _ktpReturned = true;

  String _formatDuration(DateTime checkedInAt) {
    final diff = DateTime.now().difference(checkedInAt);
    if (diff.inMinutes < 60) {
      return '${diff.inMinutes.abs()} min';
    }
    return '${diff.inHours} h ${diff.inMinutes.remainder(60)} min';
  }

  @override
  Widget build(BuildContext context) {
    final service = MockPermitService();
    final log = service.activityLogs.cast().firstWhere(
          (l) => l.id == widget.logId,
          orElse: () => null,
        );

    if (log == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Pemeriksaan Keluar (Check-Out)')),
        body: const Center(child: Text('Catatan log tidak ditemukan')),
      );
    }

    final driver = service.getDriver(log.driverId);
    final vehicle = service.getVehicle(log.vehicleId);
    final timeStr =
        '${log.timestamp.hour.toString().padLeft(2, '0')}:${log.timestamp.minute.toString().padLeft(2, '0')}';
    final canCheckout = _badgeReturned && _ktpReturned;

    return Scaffold(
      backgroundColor: AppColors.bg,
      appBar: AppBar(
        title: const Text('Pemeriksaan Keluar (Check-Out)'),
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
              // 1. Details Group Card
              Container(
                decoration: BoxDecoration(
                  color: AppColors.card,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.sep, width: 0.5),
                ),
                child: Column(
                  children: [
                    _buildRow('Pengemudi', driver?.name ?? log.driverId),
                    const Divider(height: 1, indent: 16, color: AppColors.sep),
                    _buildRow('Kendaraan', vehicle?.plate ?? log.vehicleId, isMono: true),
                    const Divider(height: 1, indent: 16, color: AppColors.sep),
                    _buildRow('Waktu Masuk', '$timeStr · ${log.gate}'),
                    const Divider(height: 1, indent: 16, color: AppColors.sep),
                    _buildRow('Durasi di Area Kilang', _formatDuration(log.timestamp), isMono: true),
                    const Divider(height: 1, indent: 16, color: AppColors.sep),
                    _buildRow('No. Seri Badge Disimpan', log.visitorBadgeNo, isMono: true),
                  ],
                ),
              ),

              const SizedBox(height: 16),

              // 2. SOP Badge & KTP Return Verification (ses-api Contract)
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                child: Text(
                  'SOP PENGEMBALIAN IDENTITAS & AKSES (ses-api)',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: AppColors.label2,
                    letterSpacing: 0.5,
                  ),
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
                    SwitchListTile(
                      value: _badgeReturned,
                      onChanged: (val) => setState(() => _badgeReturned = val),
                      secondary: Icon(
                        Icons.badge,
                        color: _badgeReturned ? AppColors.green : AppColors.red,
                      ),
                      title: const Text(
                        'Badge Visitor Dikembalikan',
                        style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
                      ),
                      subtitle: Text(
                        _badgeReturned
                            ? 'Kartu tamu ${log.visitorBadgeNo} sudah diterima petugas'
                            : 'Wajib dikembalikan sebelum meninggalkan gerbang!',
                        style: TextStyle(
                          fontSize: 12,
                          color: _badgeReturned ? AppColors.green : AppColors.red,
                        ),
                      ),
                    ),
                    const Divider(height: 1, indent: 16, color: AppColors.sep),
                    SwitchListTile(
                      value: _ktpReturned,
                      onChanged: (val) => setState(() => _ktpReturned = val),
                      secondary: Icon(
                        Icons.credit_card,
                        color: _ktpReturned ? AppColors.green : AppColors.red,
                      ),
                      title: const Text(
                        'Fisik KTP Diserahkan ke Supir',
                        style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
                      ),
                      subtitle: Text(
                        _ktpReturned
                            ? 'KTP asli supir telah diserahkan kembali'
                            : 'Wajib mengembalikan KTP fisik asli supir!',
                        style: TextStyle(
                          fontSize: 12,
                          color: _ktpReturned ? AppColors.green : AppColors.red,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 16),

              // 3. SOP Cargo Verification Blue Banner
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.blueBg,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.blue.withValues(alpha: 0.3), width: 0.5),
                ),
                child: const Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.info_outline, color: AppColors.blue, size: 24),
                    SizedBox(width: 14),
                    Expanded(
                      child: Text(
                        'Pastikan bak muatan kosong atau dokumen barang keluar sesuai sebelum mobil meninggalkan gerbang. Supir & truk yang sama dapat re-entry di hari yang sama tanpa inspeksi ulang.',
                        style: TextStyle(
                          color: AppColors.label,
                          fontSize: 13,
                          height: 1.4,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 28),

              // 4. Confirm Check-Out Button
              ElevatedButton.icon(
                onPressed: canCheckout
                    ? () {
                        final outLog = service.commitCheckout(
                          requestId: log.requestId,
                          driverId: log.driverId,
                          vehicleId: log.vehicleId,
                          visitorBadgeReturned: _badgeReturned,
                          ktpReturned: _ktpReturned,
                        );

                        Navigator.of(context).pushAndRemoveUntil(
                          MaterialPageRoute(builder: (_) => ResultPage(logId: outLog.id)),
                          (route) => route.isFirst,
                        );
                      }
                    : null,
                icon: const Icon(Icons.logout, size: 20),
                label: Text(
                  canCheckout ? 'Konfirmasi Check-Out & Selesai' : 'Kembalikan Badge & KTP',
                  style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.blue,
                  foregroundColor: Colors.white,
                  minimumSize: const Size(double.infinity, 52),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
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
