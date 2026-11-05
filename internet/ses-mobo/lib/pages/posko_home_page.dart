import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../api/api_config.dart';
import '../api/api_client.dart';
import '../models/permit_header.dart';
import '../models/gate_app_models.dart';
import '../services/mock_permit_service.dart';
import 'scanner_page.dart';
import 'permit_detail_page.dart';
import 'gate_selection_page.dart';
import 'login_page.dart';

class PoskoHomePage extends StatefulWidget {
  final List<MaterialPermitHeader>? permits;
  final String? inspectorNip;

  const PoskoHomePage({
    super.key,
    this.permits,
    this.inspectorNip,
  });

  @override
  State<PoskoHomePage> createState() => _PoskoHomePageState();
}

class _PoskoHomePageState extends State<PoskoHomePage> {
  final _service = MockPermitService();
  int _currentTabIndex = 0;
  String _activityFilter = 'Semua';

  String _formatDate(DateTime dt) {
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    return '${dt.day} ${months[dt.month - 1]} ${dt.year}';
  }

  void _showLogDetailModal(GateActivityLog log) {
    final driver = _service.getDriver(log.driverId);
    final vehicle = _service.getVehicle(log.vehicleId);
    final req = _service.getRequest(log.requestId);
    final timeStr = '${log.timestamp.hour.toString().padLeft(2, '0')}:${log.timestamp.minute.toString().padLeft(2, '0')}';

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.bg,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (ctx) {
        return DraggableScrollableSheet(
          initialChildSize: 0.75,
          minChildSize: 0.4,
          maxChildSize: 0.92,
          expand: false,
          builder: (_, scrollController) {
            return ListView(
              controller: scrollController,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              children: [
                Center(
                  child: Container(
                    width: 36,
                    height: 5,
                    decoration: BoxDecoration(color: AppColors.fill2, borderRadius: BorderRadius.circular(3)),
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  log.typeLabel,
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 16),
                Container(
                  decoration: BoxDecoration(
                    color: AppColors.card,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: AppColors.sep, width: 0.5),
                  ),
                  child: Column(
                    children: [
                      _buildModalRow('Waktu', '${_formatDate(log.timestamp)} $timeStr'),
                      const Divider(height: 1, indent: 16, color: AppColors.sep),
                      _buildModalRow('Nomor Izin', log.requestId, isMono: true),
                      const Divider(height: 1, indent: 16, color: AppColors.sep),
                      _buildModalRow('Vendor Rekanan', req?.vendor ?? '–'),
                      const Divider(height: 1, indent: 16, color: AppColors.sep),
                      _buildModalRow('Pengemudi', driver?.name ?? log.driverId),
                      const Divider(height: 1, indent: 16, color: AppColors.sep),
                      _buildModalRow('Kendaraan', vehicle?.plate ?? log.vehicleId, isMono: true),
                      const Divider(height: 1, indent: 16, color: AppColors.sep),
                      _buildModalRow('Pos Gerbang', log.gate),
                      const Divider(height: 1, indent: 16, color: AppColors.sep),
                      _buildModalRow('Petugas', log.officer),
                      if (log.result != null) ...[
                        const Divider(height: 1, indent: 16, color: AppColors.sep),
                        _buildModalRow('Hasil Pemeriksaan', log.result!),
                      ],
                    ],
                  ),
                ),
                if (log.answers != null) ...[
                  const SizedBox(height: 20),
                  const Text(
                    'LEMBAR PEMERIKSAAN FISIK',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.label2, letterSpacing: 0.5),
                  ),
                  const SizedBox(height: 8),
                  Container(
                    decoration: BoxDecoration(
                      color: AppColors.card,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: AppColors.sep, width: 0.5),
                    ),
                    child: Column(
                      children: _service.checklist.asMap().entries.map((entry) {
                        final idx = entry.key;
                        final item = entry.value;
                        final ans = log.answers![item.id];
                        final ansText = ans == ChecklistAnswer.presentAndFit
                            ? 'Ada & Laik'
                            : (ans == ChecklistAnswer.presentNotFit ? 'Ada, Tidak Laik' : 'Tidak Ada');
                        final ansColor = ans == ChecklistAnswer.presentAndFit
                            ? AppColors.green
                            : (ans == ChecklistAnswer.presentNotFit ? AppColors.orange : AppColors.red);

                        return Column(
                          children: [
                            if (idx > 0) const Divider(height: 1, indent: 16, color: AppColors.sep),
                            Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                              child: Row(
                                children: [
                                  Expanded(child: Text(item.name, style: const TextStyle(fontSize: 13))),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                    decoration: BoxDecoration(
                                      color: ansColor.withValues(alpha: 0.12),
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                    child: Text(
                                      ansText,
                                      style: TextStyle(color: ansColor, fontSize: 11, fontWeight: FontWeight.w600),
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
                ],
                if (log.remarks != null && log.remarks!.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: log.type == GateLogType.rejected ? AppColors.redBg : AppColors.blueBg,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Catatan Petugas', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13)),
                        const SizedBox(height: 4),
                        Text(log.remarks!, style: const TextStyle(fontSize: 14)),
                      ],
                    ),
                  ),
                ],
                const SizedBox(height: 24),
              ],
            );
          },
        );
      },
    );
  }

  Widget _buildModalRow(String label, String value, {bool isMono = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 14, color: AppColors.label)),
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

  Widget _buildSesFooter() {
    return const Padding(
      padding: EdgeInsets.only(top: 28, bottom: 20),
      child: Column(
        children: [
          Divider(height: 1, color: AppColors.sep),
          SizedBox(height: 12),
          Text(
            '© 2026 Security Suite • PT BADAK NGL',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: Color(0xFF64748B),
            ),
          ),
          SizedBox(height: 4),
          Text(
            'Sistem Pengamanan Terpadu Kilang Badak LNG',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 11,
              color: Color(0xFF94A3B8),
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.bg,
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(60),
        child: Container(
          color: const Color(0xFF1D2D3E),
          child: SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  Image.asset(
                    'assets/image/ptb_logo.png',
                    height: 30,
                    fit: BoxFit.contain,
                    errorBuilder: (context, error, stackTrace) => const Icon(Icons.shield, color: Colors.white, size: 22),
                  ),
                  const SizedBox(width: 10),
                  const Text(
                    'Security Suite',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 0.5,
                    ),
                  ),
                  const Spacer(),
                  InkWell(
                    onTap: () {
                      Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => GateSelectionPage(preselectedGate: _service.currentGate ?? 'KPBR Gate'),
                        ),
                      ).then((_) => setState(() {}));
                    },
                    borderRadius: BorderRadius.circular(14),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.meeting_room_outlined, color: Colors.white, size: 14),
                          const SizedBox(width: 4),
                          Text(
                            (_service.currentGate ?? 'KPBR Gate').split(' ').first,
                            style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w600),
                          ),
                          const Icon(Icons.arrow_drop_down, color: Colors.white, size: 16),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
      body: SafeArea(
        child: IndexedStack(
          index: _currentTabIndex,
          children: [
            _buildGateTab(),
            _buildActivityTab(),
            _buildProfileTab(),
          ],
        ),
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentTabIndex,
        onTap: (index) => setState(() => _currentTabIndex = index),
        selectedItemColor: AppColors.brand,
        unselectedItemColor: AppColors.label2,
        backgroundColor: Colors.white,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.door_sliding_outlined),
            activeIcon: Icon(Icons.door_sliding),
            label: 'Pos Gerbang',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.history_outlined),
            activeIcon: Icon(Icons.history),
            label: 'Aktivitas',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.person_outline),
            activeIcon: Icon(Icons.person),
            label: 'Profil',
          ),
        ],
      ),
    );
  }

  // --- TAB 1: GATE (HOME) ---
  Widget _buildGateTab() {
    final user = _service.currentUser;
    final gate = _service.currentGate ?? 'KPBR Gate';
    final allInsideVehicles = _service.inside();
    final insideHere = _service.insideForGate(gate);
    final isWarehouseZone2 = gate.contains('Warehouse') || gate.contains('Zona 2');
    final todayLogs = _service.activityLogs;
    final checkInsHere = todayLogs.where((l) => l.type == GateLogType.checkIn && l.gate == gate).length;
    final rejectedHere = todayLogs.where((l) => l.type == GateLogType.rejected && l.gate == gate).length;

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Date & Title
          if (widget.permits != null) ...[
            const Text(
              'SES Security Suite Mobile',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: AppColors.label),
            ),
            const Text(
              'Posko Pemeriksaan Fisik Truk Lapangan Bola',
              style: TextStyle(fontSize: 14, color: AppColors.label2),
            ),
            const SizedBox(height: 6),
            Text(
              'Petugas Aktif: NIP ${widget.inspectorNip ?? "SEC-88210"}',
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.primaryBlue),
            ),
          ] else ...[
            Text(
              _formatDate(DateTime.now()),
              style: const TextStyle(fontSize: 13, color: AppColors.label2, fontWeight: FontWeight.w500),
            ),
            const SizedBox(height: 2),
            const Text(
              'Pos Gerbang',
              style: TextStyle(fontSize: 34, fontWeight: FontWeight.w700, color: AppColors.label, letterSpacing: -0.5),
            ),
          ],
          const SizedBox(height: 16),

          // Hero Card with Scanner Button
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [AppColors.brand, AppColors.shell],
              ),
              borderRadius: BorderRadius.circular(20),
              boxShadow: [
                BoxShadow(color: Colors.black.withValues(alpha: 0.15), blurRadius: 16, offset: const Offset(0, 6)),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Bertugas · ${user?.name ?? "Dedi Kurniawan"}',
                      style: const TextStyle(fontSize: 14, color: Colors.white70),
                    ),
                    if (isWarehouseZone2)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: AppColors.orangeBg,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: AppColors.orange, width: 0.8),
                        ),
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.warning_amber_rounded, size: 12, color: AppColors.orange),
                            SizedBox(width: 4),
                            Text(
                              'ZONA 2 KILANG',
                              style: TextStyle(
                                fontSize: 10,
                                fontWeight: FontWeight.w700,
                                color: AppColors.orange,
                                letterSpacing: 0.5,
                              ),
                            ),
                          ],
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  gate,
                  style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700, color: Colors.white),
                ),
                const SizedBox(height: 18),
                ElevatedButton.icon(
                  onPressed: () {
                    Navigator.of(context).push(
                      MaterialPageRoute(builder: (_) => const ScannerPage()),
                    ).then((_) => setState(() {}));
                  },
                  icon: const Icon(Icons.qr_code_scanner, color: AppColors.brand, size: 26),
                  label: const Text(
                    'Scan QR Code',
                    style: TextStyle(
                      color: AppColors.brand,
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.white,
                    minimumSize: const Size(double.infinity, 60),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                    elevation: 4,
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 16),

          // 3 Statistics Boxes
          Row(
            children: [
              _buildStatBox('Di Area Pos Ini', '${insideHere.length}', AppColors.label),
              const SizedBox(width: 8),
              _buildStatBox('Total Masuk Hari Ini', '$checkInsHere', AppColors.label),
              const SizedBox(width: 8),
              _buildStatBox('Ditolak Masuk', '$rejectedHere', AppColors.red),
            ],
          ),

          const SizedBox(height: 24),

          // On Site Now Section
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'KENDARAAN DI AREA KILANG',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.label2, letterSpacing: 0.5),
                ),
                Text(
                  '${allInsideVehicles.length} total di area kilang',
                  style: const TextStyle(fontSize: 12, color: AppColors.label3),
                ),
              ],
            ),
          ),
          Material(
            color: AppColors.card,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: const BorderSide(color: AppColors.sep, width: 0.5),
            ),
            clipBehavior: Clip.antiAlias,
            child: allInsideVehicles.isNotEmpty
                ? Column(
                    children: allInsideVehicles.asMap().entries.map((entry) {
                      final idx = entry.key;
                      final log = entry.value;
                      final veh = _service.getVehicle(log.vehicleId);
                      final drv = _service.getDriver(log.driverId);
                      final timeStr =
                          '${log.timestamp.hour.toString().padLeft(2, '0')}:${log.timestamp.minute.toString().padLeft(2, '0')}';

                      return Column(
                        children: [
                          if (idx > 0) const Divider(height: 1, indent: 56, color: AppColors.sep),
                          ListTile(
                            onTap: () {
                              Navigator.of(context).push(
                                MaterialPageRoute(builder: (_) => PermitDetailPage(permitId: log.requestId)),
                              ).then((_) => setState(() {}));
                            },
                            leading: Container(
                              width: 34,
                              height: 34,
                              decoration: BoxDecoration(color: AppColors.green, borderRadius: BorderRadius.circular(8)),
                              child: const Icon(Icons.local_shipping, color: Colors.white, size: 20),
                            ),
                            title: Text(
                              '${veh?.plate ?? log.vehicleId} · ${drv?.name ?? log.driverId}',
                              style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                            ),
                            subtitle: Text(
                              'Masuk sejak $timeStr · ${log.gate}',
                              style: const TextStyle(fontSize: 12, color: AppColors.label2),
                            ),
                            trailing: const Icon(Icons.chevron_right, color: AppColors.label3, size: 20),
                          ),
                        ],
                      );
                    }).toList(),
                  )
                : const Padding(
                    padding: EdgeInsets.all(20),
                    child: Center(
                      child: Text(
                        'Tidak ada kendaraan rekanan di dalam area.',
                        style: TextStyle(color: AppColors.label2, fontSize: 14),
                      ),
                    ),
                  ),
          ),
          const SizedBox(height: 8),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 8),
            child: Text(
              'Pindai QR code yang sama saat kendaraan keluar untuk memproses check-out.',
              style: TextStyle(fontSize: 12, color: AppColors.label2),
            ),
          ),
          if (widget.permits != null) ...[
            const SizedBox(height: 20),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
              child: Text(
                'DAFTAR IZIN AKTIF',
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
                children: widget.permits!.asMap().entries.map((entry) {
                  final idx = entry.key;
                  final p = entry.value;
                  return Column(
                    children: [
                      if (idx > 0) const Divider(height: 1, indent: 56, color: AppColors.sep),
                      ListTile(
                        onTap: () {
                          Navigator.of(context).push(
                            MaterialPageRoute(builder: (_) => PermitDetailPage(permit: p)),
                          );
                        },
                        leading: Container(
                          width: 32,
                          height: 32,
                          decoration: BoxDecoration(color: AppColors.primaryBlue, borderRadius: BorderRadius.circular(8)),
                          child: const Icon(Icons.description, color: Colors.white, size: 18),
                        ),
                        title: Text(p.vendorName, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                        subtitle: Text('${p.requestNo} · ${p.destinationZone.name}', style: const TextStyle(fontSize: 12, color: AppColors.label2)),
                        trailing: const Icon(Icons.chevron_right, color: AppColors.label3, size: 18),
                      ),
                    ],
                  );
                }).toList(),
              ),
            ),
          ],
          _buildSesFooter(),
        ],
      ),
    );
  }

  Widget _buildStatBox(String label, String value, Color valueColor) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 8),
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.sep, width: 0.5),
        ),
        child: Column(
          children: [
            Text(
              value,
              style: TextStyle(fontSize: 24, fontWeight: FontWeight.w700, color: valueColor, fontFamily: 'monospace'),
            ),
            const SizedBox(height: 2),
            Text(
              label,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 11, color: AppColors.label2, fontWeight: FontWeight.w500),
            ),
          ],
        ),
      ),
    );
  }

  // --- TAB 2: ACTIVITY ---
  Widget _buildActivityTab() {
    final filters = ['Semua', 'Check-in', 'Check-out', 'Ditolak'];
    var logs = List<GateActivityLog>.from(_service.activityLogs)
      ..sort((a, b) => b.timestamp.compareTo(a.timestamp));

    if (_activityFilter != 'Semua') {
      logs = logs.where((l) => l.typeLabel == _activityFilter).toList();
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text(
            'Aktivitas',
            style: TextStyle(fontSize: 34, fontWeight: FontWeight.w700, color: AppColors.label, letterSpacing: -0.5),
          ),
          const SizedBox(height: 2),
          const Text(
            'Hari ini · Semua gerbang',
            style: TextStyle(fontSize: 14, color: AppColors.label2),
          ),
          const SizedBox(height: 16),

          // 4-way Segment Filter
          Container(
            decoration: BoxDecoration(
              color: AppColors.fill,
              borderRadius: BorderRadius.circular(10),
            ),
            padding: const EdgeInsets.all(3),
            child: Row(
              children: filters.map((f) {
                final isSelected = _activityFilter == f;
                return Expanded(
                  child: GestureDetector(
                    onTap: () => setState(() => _activityFilter = f),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      decoration: BoxDecoration(
                        color: isSelected ? AppColors.card : Colors.transparent,
                        borderRadius: BorderRadius.circular(8),
                        boxShadow: isSelected
                            ? [
                                BoxShadow(color: Colors.black.withValues(alpha: 0.08), blurRadius: 4, offset: const Offset(0, 1)),
                              ]
                            : null,
                      ),
                      child: Text(
                        f,
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                          color: isSelected ? AppColors.label : AppColors.label2,
                        ),
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
          ),

          const SizedBox(height: 16),

          // Activity Logs List
          Material(
            color: AppColors.card,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: const BorderSide(color: AppColors.sep, width: 0.5),
            ),
            clipBehavior: Clip.antiAlias,
            child: logs.isNotEmpty
                ? Column(
                    children: logs.asMap().entries.map((entry) {
                      final idx = entry.key;
                      final log = entry.value;
                      final veh = _service.getVehicle(log.vehicleId);
                      final drv = _service.getDriver(log.driverId);
                      final timeStr =
                          '${log.timestamp.hour.toString().padLeft(2, '0')}:${log.timestamp.minute.toString().padLeft(2, '0')}';

                      final iconColor = log.type == GateLogType.checkIn
                          ? AppColors.green
                          : (log.type == GateLogType.checkOut ? AppColors.blue : AppColors.red);
                      final iconData = log.type == GateLogType.checkIn
                          ? Icons.login
                          : (log.type == GateLogType.checkOut ? Icons.logout : Icons.close);

                      return Column(
                        children: [
                          if (idx > 0) const Divider(height: 1, indent: 56, color: AppColors.sep),
                          ListTile(
                            onTap: () => _showLogDetailModal(log),
                            leading: Container(
                              width: 32,
                              height: 32,
                              decoration: BoxDecoration(color: iconColor, borderRadius: BorderRadius.circular(8)),
                              child: Icon(iconData, color: Colors.white, size: 18),
                            ),
                            title: Text(
                              '${veh?.plate ?? log.vehicleId} · ${drv?.name.split(" ").first ?? log.driverId}',
                              style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                            ),
                            subtitle: Text(
                              '${log.typeLabel}${log.isFastReentry ? " · re-entry" : ""} · ${log.gate}',
                              style: const TextStyle(fontSize: 12, color: AppColors.label2),
                            ),
                            trailing: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(timeStr, style: const TextStyle(fontSize: 13, color: AppColors.label2, fontFamily: 'monospace')),
                                const SizedBox(width: 4),
                                const Icon(Icons.chevron_right, color: AppColors.label3, size: 18),
                              ],
                            ),
                          ),
                        ],
                      );
                    }).toList(),
                  )
                : const Padding(
                    padding: EdgeInsets.all(24),
                    child: Center(
                      child: Text('Belum ada aktivitas hari ini.', style: TextStyle(color: AppColors.label2, fontSize: 14)),
                    ),
                  ),
          ),
          _buildSesFooter(),
        ],
      ),
    );
  }

  // --- TAB 3: PROFILE ---
  Widget _buildProfileTab() {
    final user = _service.currentUser;
    final gate = _service.currentGate ?? 'KPBR Gate';

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text(
            'Profil',
            style: TextStyle(fontSize: 34, fontWeight: FontWeight.w700, color: AppColors.label, letterSpacing: -0.5),
          ),
          const SizedBox(height: 20),

          // User Info Card
          Material(
            color: AppColors.card,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: const BorderSide(color: AppColors.sep, width: 0.5),
            ),
            clipBehavior: Clip.antiAlias,
            child: ListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              leading: Container(
                width: 44,
                height: 44,
                decoration: const BoxDecoration(color: AppColors.blue, shape: BoxShape.circle),
                child: const Icon(Icons.person, color: Colors.white, size: 24),
              ),
              title: Text(user?.name ?? 'Dedi Kurniawan', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
              subtitle: Text('${user?.employeeId ?? "EMP-20211"} · Petugas Lapangan SES', style: const TextStyle(fontSize: 13, color: AppColors.label2)),
            ),
          ),

          const SizedBox(height: 24),

          // Duty Gate Section
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
            child: Text(
              'PENUGASAN SHIFT',
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
            child: ListTile(
              onTap: () {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => GateSelectionPage(preselectedGate: gate)),
                ).then((_) => setState(() {}));
              },
              leading: Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(color: AppColors.indigo, borderRadius: BorderRadius.circular(8)),
                child: const Icon(Icons.meeting_room, color: Colors.white, size: 18),
              ),
              title: const Text('Gerbang Aktif', style: TextStyle(fontWeight: FontWeight.w500, fontSize: 15)),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(gate, style: const TextStyle(fontSize: 14, color: AppColors.label2)),
                  const SizedBox(width: 4),
                  const Icon(Icons.chevron_right, color: AppColors.label3, size: 18),
                ],
              ),
            ),
          ),

          const SizedBox(height: 24),

          // SES Backend API Connection Card
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
            child: Text(
              'KONEKSI BACKEND SES API (ses-api :8084)',
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
              children: [
                ListTile(
                  leading: Container(
                    width: 34,
                    height: 34,
                    decoration: BoxDecoration(
                      color: AppColors.green.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(Icons.cloud_done, color: AppColors.green, size: 20),
                  ),
                  title: const Text('Status Koneksi Backend', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                  subtitle: Text(
                    'Terhubung Otomatis · ${ApiConfig.baseUrl}',
                    style: const TextStyle(fontSize: 12, color: AppColors.green, fontWeight: FontWeight.w500),
                  ),
                ),
                const Divider(height: 1, indent: 16, color: AppColors.sep),
                ListTile(
                  leading: const Icon(Icons.settings_ethernet, color: AppColors.blue, size: 20),
                  title: const Text('Endpoint Base URL', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
                  subtitle: Text(ApiConfig.baseUrl, style: const TextStyle(fontSize: 12, color: AppColors.label2)),
                  trailing: const Icon(Icons.edit, size: 16, color: AppColors.label3),
                  onTap: () {
                    final textController = TextEditingController(text: ApiConfig.baseUrl);
                    showDialog(
                      context: context,
                      builder: (ctx) => AlertDialog(
                        title: const Text('Ganti Base URL SES API'),
                        content: TextField(
                          controller: textController,
                          decoration: const InputDecoration(
                            hintText: 'http://localhost:8084/api/v1',
                            labelText: 'Base URL',
                          ),
                        ),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(ctx),
                            child: const Text('Batal'),
                          ),
                          ElevatedButton(
                            onPressed: () {
                              setState(() {
                                ApiConfig.baseUrl = textController.text.trim();
                              });
                              Navigator.pop(ctx);
                            },
                            child: const Text('Simpan'),
                          ),
                        ],
                      ),
                    );
                  },
                ),
                const Divider(height: 1, indent: 16, color: AppColors.sep),
                ListTile(
                  leading: const Icon(Icons.network_ping, color: AppColors.orange, size: 20),
                  title: const Text('Test Connection (Ping :8084)', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
                  onTap: () async {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Menguji koneksi ke server SES...'), duration: Duration(seconds: 1)),
                    );
                    final client = ApiClient();
                    final isAlive = await client.pingServer();
                    client.close();
                    if (!mounted) return;
                    showDialog(
                      context: context,
                      builder: (ctx) => AlertDialog(
                        title: Row(
                          children: [
                            Icon(
                              isAlive ? Icons.check_circle : Icons.error_outline,
                              color: isAlive ? AppColors.green : AppColors.red,
                            ),
                            const SizedBox(width: 8),
                            Text(isAlive ? 'Koneksi Berhasil' : 'Koneksi Gagal'),
                          ],
                        ),
                        content: Text(
                          isAlive
                              ? 'Server ses-api aktif dan merespons pada ${ApiConfig.baseUrl}!'
                              : 'Tidak dapat menghubungi ${ApiConfig.baseUrl}. Pastikan aplikasi Spring Boot ses-api berjalan di komputer.',
                        ),
                        actions: [
                          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Tutup')),
                        ],
                      ),
                    );
                  },
                ),
              ],
            ),
          ),

          const SizedBox(height: 24),

          // Actions: Sign Out (Real Production Mode)
          Material(
            color: AppColors.card,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: const BorderSide(color: AppColors.sep, width: 0.5),
            ),
            clipBehavior: Clip.antiAlias,
            child: ListTile(
              onTap: () {
                ApiConfig.reset();
                _service.currentUser = null;
                _service.currentGate = null;
                Navigator.of(context).pushAndRemoveUntil(
                  MaterialPageRoute(builder: (_) => const LoginPage()),
                  (route) => false,
                );
              },
              leading: const Icon(Icons.logout, color: AppColors.red),
              title: const Text('Keluar Akun', style: TextStyle(color: AppColors.red, fontWeight: FontWeight.w600)),
            ),
          ),
          _buildSesFooter(),
        ],
      ),
    );
  }
}
