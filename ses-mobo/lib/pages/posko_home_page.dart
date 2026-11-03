import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../core/app_constants.dart';
import '../models/permit_header.dart';
import 'permit_detail_page.dart';

class PoskoHomePage extends StatefulWidget {
  final List<MaterialPermitHeader> permits;
  final String inspectorNip;

  const PoskoHomePage({
    super.key,
    required this.permits,
    this.inspectorNip = 'SEC-88210',
  });

  @override
  State<PoskoHomePage> createState() => _PoskoHomePageState();
}

class _PoskoHomePageState extends State<PoskoHomePage> {
  final TextEditingController _searchController = TextEditingController();
  String _selectedFilter = 'ALL';

  List<MaterialPermitHeader> get _filteredPermits {
    return widget.permits.where((p) {
      if (_selectedFilter != 'ALL' && p.permitBasis.code != _selectedFilter) {
        return false;
      }
      final query = _searchController.text.trim().toLowerCase();
      if (query.isNotEmpty) {
        final matchesReq = p.requestNo.toLowerCase().contains(query);
        final matchesVendor = p.vendorName.toLowerCase().contains(query);
        final matchesPlate = p.vehicleItems.any((v) => v.licensePlateNo.toLowerCase().contains(query));
        return matchesReq || matchesVendor || matchesPlate;
      }
      return true;
    }).toList();
  }

  void _simulateQrScan() {
    showDialog(
      context: context,
      builder: (ctx) {
        final controller = TextEditingController();
        return AlertDialog(
          title: const Row(
            children: [
              Icon(Icons.qr_code_scanner, color: AppColors.navy),
              SizedBox(width: 8),
              Text('Simulasi Scan QR Izin'),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Arahkan kamera ke Master QR Code pada surat izin jalan pengemudi:',
                style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: controller,
                decoration: const InputDecoration(
                  labelText: 'Nomor Tiket / Token QR',
                  hintText: 'Misal: REQ-MAT-20261103-0001',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Batal'),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.blue,
                foregroundColor: Colors.white,
              ),
              onPressed: () {
                final text = controller.text.trim();
                Navigator.pop(ctx);
                final found = widget.permits.firstWhere(
                  (p) => p.requestNo.equalsIgnoreCase(text) || p.requestNo.contains(text),
                  orElse: () => widget.permits.first,
                );
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => PermitDetailPage(
                      permit: found,
                      inspectorNip: widget.inspectorNip,
                    ),
                  ),
                );
              },
              child: const Text('Buka Tiket'),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final permitsList = _filteredPermits;

    return Scaffold(
      appBar: AppBar(
        title: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'SES Security Suite Mobile',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            Text(
              'Posko Pemeriksaan Fisik Truk Lapangan Bola',
              style: TextStyle(fontSize: 11, color: AppColors.blueSubtleBorder),
            ),
          ],
        ),
        backgroundColor: AppColors.navy,
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            icon: const Icon(Icons.qr_code_scanner),
            tooltip: 'Scan Master QR Code Izin Masuk',
            onPressed: _simulateQrScan,
          ),
        ],
      ),
      body: Column(
        children: [
          // Banner Status Petugas
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            color: AppColors.canvas,
            child: Row(
              children: [
                const CircleAvatar(
                  radius: 16,
                  backgroundColor: AppColors.blue,
                  child: Icon(Icons.security, size: 18, color: Colors.white),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Petugas Aktif: NIP ${widget.inspectorNip}',
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                      ),
                      const Text(
                        'Gerbang Utama & Posko Lapangan Bola | Mode Terkoneksi',
                        style: TextStyle(fontSize: 11, color: AppColors.textSecondary),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.successBg,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppColors.success),
                  ),
                  child: const Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CircleAvatar(radius: 4, backgroundColor: AppColors.success),
                      SizedBox(width: 4),
                      Text(
                        'ONLINE',
                        style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.success),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const Divider(height: 1),

          // Search & Filter
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 6),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: 'Cari no tiket, vendor, atau plat kendaraan...',
                prefixIcon: const Icon(Icons.search),
                isDense: true,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: () {
                          _searchController.clear();
                          setState(() {});
                        },
                      )
                    : null,
              ),
              onChanged: (_) => setState(() {}),
            ),
          ),

          // Filter Chips
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            child: Row(
              children: [
                _buildFilterChip('ALL', 'Semua'),
                const SizedBox(width: 8),
                _buildFilterChip(PermitBasis.po.code, 'PO Reguler'),
                const SizedBox(width: 8),
                _buildFilterChip(PermitBasis.ca.code, 'Kontrak CA'),
                const SizedBox(width: 8),
                _buildFilterChip(PermitBasis.facilityMemo.code, 'Sembako/Fasilitas'),
              ],
            ),
          ),
          const SizedBox(height: 6),

          // Daftar Izin
          Expanded(
            child: permitsList.isEmpty
                ? const Center(
                    child: Text(
                      'Tidak ada permohonan yang sesuai filter.',
                      style: TextStyle(color: AppColors.textSecondary),
                    ),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    itemCount: permitsList.length,
                    itemBuilder: (context, index) {
                      final permit = permitsList[index];
                      return _buildPermitCard(permit);
                    },
                  ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _simulateQrScan,
        backgroundColor: AppColors.blue,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.qr_code_scanner),
        label: const Text('Scan QR Izin'),
      ),
    );
  }

  Widget _buildFilterChip(String code, String label) {
    final bool isSelected = _selectedFilter == code;
    return ChoiceChip(
      label: Text(label, style: const TextStyle(fontSize: 12)),
      selected: isSelected,
      selectedColor: AppColors.blueSubtleBg,
      labelStyle: TextStyle(
        color: isSelected ? AppColors.blueDark : AppColors.textSecondary,
        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
      ),
      onSelected: (selected) {
        if (selected) {
          setState(() {
            _selectedFilter = code;
          });
        }
      },
    );
  }

  Widget _buildPermitCard(MaterialPermitHeader permit) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      elevation: 1.5,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: InkWell(
        borderRadius: BorderRadius.circular(10),
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => PermitDetailPage(
                permit: permit,
                inspectorNip: widget.inspectorNip,
              ),
            ),
          );
        },
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Text(
                      permit.requestNo,
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: AppColors.navy),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: AppColors.blueSubtleBg,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      permit.permitBasis.code,
                      style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.blue),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                permit.vendorName,
                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 4),
              Text(
                'Zona: ${permit.destinationZone.label} | Kendaraan: ${permit.vehicleItems.map((v) => v.licensePlateNo).join(", ")}',
                style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const Divider(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Row(
                      children: [
                        const Icon(Icons.people_alt_outlined, size: 14, color: AppColors.textSecondary),
                        const SizedBox(width: 4),
                        Expanded(
                          child: Text(
                            'Sopir: ${permit.driverItems.map((d) => d.driverName).join(", ")}',
                            style: const TextStyle(fontSize: 11, color: AppColors.textSecondary),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        'Buka Posko',
                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.blue),
                      ),
                      Icon(Icons.chevron_right, size: 16, color: AppColors.blue),
                    ],
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

extension _StringCaseExtension on String {
  bool equalsIgnoreCase(String other) => toLowerCase() == other.toLowerCase();
}
