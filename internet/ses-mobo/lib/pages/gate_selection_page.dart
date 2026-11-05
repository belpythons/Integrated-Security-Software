import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../services/mock_permit_service.dart';
import 'posko_home_page.dart';

class GateSelectionPage extends StatefulWidget {
  final String? preselectedGate;

  const GateSelectionPage({super.key, this.preselectedGate});

  @override
  State<GateSelectionPage> createState() => _GateSelectionPageState();
}

class _GateSelectionPageState extends State<GateSelectionPage> {
  final _service = MockPermitService();
  late String _selectedGate;

  @override
  void initState() {
    super.initState();
    _selectedGate = widget.preselectedGate ?? _service.gates.first;
  }

  void _startShift() {
    _service.currentGate = _selectedGate;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const PoskoHomePage()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final user = _service.currentUser;
    final firstName = user?.name.split(' ').first ?? 'Petugas';

    return Scaffold(
      backgroundColor: AppColors.bg,
      appBar: AppBar(
        title: const Text('Pilih Gerbang Tugas'),
        automaticallyImplyLeading: false,
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Pilih Pos Gerbang',
                style: const TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.w700,
                  color: AppColors.label,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                'Halo $firstName, di pos mana Anda bertugas hari ini?',
                style: const TextStyle(
                  fontSize: 15,
                  color: AppColors.label2,
                ),
              ),
              const SizedBox(height: 28),

              // Grouped Cards for Gates
              Container(
                decoration: BoxDecoration(
                  color: AppColors.card,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.sep, width: 0.5),
                ),
                child: Column(
                  children: _service.gates.asMap().entries.map((entry) {
                    final index = entry.key;
                    final gate = entry.value;
                    final isSelected = _selectedGate == gate;

                    return Column(
                      children: [
                        if (index > 0) const Divider(height: 1, indent: 56, color: AppColors.sep),
                        InkWell(
                          onTap: () => setState(() => _selectedGate = gate),
                          borderRadius: BorderRadius.vertical(
                            top: index == 0 ? const Radius.circular(16) : Radius.zero,
                            bottom: index == _service.gates.length - 1 ? const Radius.circular(16) : Radius.zero,
                          ),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                            child: Row(
                              children: [
                                Container(
                                  width: 36,
                                  height: 36,
                                  decoration: BoxDecoration(
                                    color: gate.contains('Zona 2')
                                        ? AppColors.orange
                                        : (gate.contains('SD') ? AppColors.indigo : AppColors.brand),
                                    borderRadius: BorderRadius.circular(10),
                                  ),
                                  child: Icon(
                                    gate.contains('Zona 2')
                                        ? Icons.warehouse
                                        : (gate.contains('SD') ? Icons.security : Icons.meeting_room),
                                    color: Colors.white,
                                    size: 20,
                                  ),
                                ),
                                const SizedBox(width: 14),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Wrap(
                                        crossAxisAlignment: WrapCrossAlignment.center,
                                        spacing: 8,
                                        runSpacing: 4,
                                        children: [
                                          Text(
                                            gate,
                                            style: const TextStyle(
                                              fontSize: 16,
                                              fontWeight: FontWeight.w600,
                                              color: AppColors.label,
                                            ),
                                          ),
                                          if (gate.contains('Zona 2'))
                                            Container(
                                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                              decoration: BoxDecoration(
                                                color: AppColors.orangeBg,
                                                borderRadius: BorderRadius.circular(6),
                                                border: Border.all(color: AppColors.orange, width: 0.5),
                                              ),
                                              child: const Text(
                                                'APD KETAT',
                                                style: TextStyle(
                                                  fontSize: 9,
                                                  fontWeight: FontWeight.w700,
                                                  color: AppColors.orange,
                                                ),
                                              ),
                                            ),
                                        ],
                                      ),
                                      const SizedBox(height: 2),
                                      Text(
                                        gate.contains('KPBR')
                                            ? 'Akses Utama Kompleks Perumahan Badak'
                                            : (gate.contains('SD')
                                                ? 'Jalur Logistik Rekanan & Kontraktor'
                                                : 'Pintu Pergudangan Kilang Zona 2'),
                                        style: const TextStyle(
                                          fontSize: 12,
                                          color: AppColors.label2,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                if (isSelected)
                                  const Icon(Icons.check_circle, color: AppColors.blue, size: 22)
                                else
                                  const Icon(Icons.circle_outlined, color: AppColors.label3, size: 22),
                              ],
                            ),
                          ),
                        ),
                      ],
                    );
                  }).toList(),
                ),
              ),

              const SizedBox(height: 12),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 8),
                child: Text(
                  'Setiap aktivitas check-in dan check-out yang Anda catat akan otomatis ditandai dengan gerbang ini.',
                  style: TextStyle(fontSize: 13, color: AppColors.label2, height: 1.4),
                ),
              ),

              const SizedBox(height: 36),

              ElevatedButton(
                onPressed: _startShift,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.blue,
                  foregroundColor: Colors.white,
                  minimumSize: const Size(double.infinity, 52),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                ),
                child: const Text('Mulai Shift Jaga', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

