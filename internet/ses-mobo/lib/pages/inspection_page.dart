import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import '../core/app_colors.dart';
import '../models/gate_app_models.dart';
import '../services/mock_permit_service.dart';
import 'decision_page.dart';

class InspectionPage extends StatefulWidget {
  final String requestId;
  final String driverId;
  final String vehicleId;

  const InspectionPage({
    super.key,
    required this.requestId,
    required this.driverId,
    required this.vehicleId,
  });

  @override
  State<InspectionPage> createState() => _InspectionPageState();
}

class _InspectionPageState extends State<InspectionPage> {
  final _service = MockPermitService();
  final Map<int, ChecklistAnswer> _answers = {};
  final Map<int, String> _photos = {};
  String? _vehicleOverviewPhoto;
  final ImagePicker _picker = ImagePicker();

  Future<void> _takePhoto(dynamic target) async {
    try {
      final XFile? image = await _picker.pickImage(
        source: ImageSource.camera,
        maxWidth: 1024,
        maxHeight: 1024,
        imageQuality: 80,
      );
      if (image != null) {
        setState(() {
          if (target == 'vehicle') {
            _vehicleOverviewPhoto = image.path;
          } else if (target is int) {
            _photos[target] = image.path;
          }
        });
      }
    } catch (_) {
      // Fallback in case camera permission was denied or run in simulator
      setState(() {
        if (target == 'vehicle') {
          _vehicleOverviewPhoto = 'simulated_camera_photo_vehicle.jpg';
        } else if (target is int) {
          _photos[target] = 'simulated_camera_photo_$target.jpg';
        }
      });
    }
  }

  void _goToDecision() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => DecisionPage(
          requestId: widget.requestId,
          driverId: widget.driverId,
          vehicleId: widget.vehicleId,
          answers: _answers,
          photos: _photos,
          vehicleOverviewPhoto: _vehicleOverviewPhoto,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final driver = _service.getDriver(widget.driverId);
    final vehicle = _service.getVehicle(widget.vehicleId);
    final checklist = _service.checklist;
    final answeredCount = _answers.length;
    final isComplete = answeredCount == checklist.length;

    // Group items by category
    final Map<ChecklistCategory, List<GateChecklistItem>> grouped = {};
    for (final item in checklist) {
      grouped.putIfAbsent(item.category, () => []).add(item);
    }

    return Scaffold(
      backgroundColor: AppColors.bg,
      appBar: AppBar(
        title: const Text('Pemeriksaan Fisik Lapangan'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 20),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Top Progress Bar Card
            Container(
              color: AppColors.card,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '${vehicle?.plate ?? widget.vehicleId} · ${driver?.name ?? widget.driverId}',
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: AppColors.label2,
                        ),
                      ),
                      Text(
                        '$answeredCount/${checklist.length}',
                        style: const TextStyle(
                          fontFamily: 'monospace',
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: AppColors.label,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                      value: checklist.isEmpty ? 0 : answeredCount / checklist.length,
                      minHeight: 6,
                      backgroundColor: AppColors.fill,
                      valueColor: const AlwaysStoppedAnimation<Color>(AppColors.blue),
                    ),
                  ),
                ],
              ),
            ),
            if (_service.currentGate != null &&
                (_service.currentGate!.contains('Warehouse') || _service.currentGate!.contains('Zona 2')))
              Container(
                color: AppColors.orangeBg,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: const Row(
                  children: [
                    Icon(Icons.warning_amber_rounded, size: 16, color: AppColors.orange),
                    SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Warehouse Gate (Zona 2): APD Pengemudi (Helm, Sepatu Safety, Rompi Reflektor) Wajib Lengkap!',
                        style: TextStyle(
                          fontSize: 12,
                          color: AppColors.orange,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            const Divider(height: 1, color: AppColors.sep),

            // Scrollable Checklist
            Expanded(
              child: ListView(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
                children: [
                  ...grouped.entries.map((catEntry) {
                    final catName = catEntry.value.first.categoryName;
                    final items = catEntry.value;

                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
                          child: Text(
                            catName.toUpperCase(),
                            style: const TextStyle(
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
                            children: items.asMap().entries.map((itemEntry) {
                              final idx = itemEntry.key;
                              final item = itemEntry.value;
                              final currentAns = _answers[item.id];
                              final photoPath = _photos[item.id];

                              return Column(
                                children: [
                                  if (idx > 0) const Divider(height: 1, indent: 16, color: AppColors.sep),
                                  Padding(
                                    padding: const EdgeInsets.all(14),
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        // Header: Title & Mandatory Pill
                                        Row(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: [
                                            Expanded(
                                              child: Text(
                                                item.name,
                                                style: const TextStyle(
                                                  fontWeight: FontWeight.w600,
                                                  fontSize: 14,
                                                  color: AppColors.label,
                                                ),
                                              ),
                                            ),
                                            if (item.mandatory)
                                              Container(
                                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                                decoration: BoxDecoration(
                                                  color: AppColors.redBg,
                                                  borderRadius: BorderRadius.circular(4),
                                                ),
                                                child: const Text(
                                                  'WAJIB',
                                                  style: TextStyle(
                                                    color: AppColors.red,
                                                    fontSize: 10,
                                                    fontWeight: FontWeight.w700,
                                                  ),
                                                ),
                                              ),
                                          ],
                                        ),
                                        const SizedBox(height: 10),

                                        // 3-way Segmented Control
                                        Container(
                                          decoration: BoxDecoration(
                                            color: AppColors.fill,
                                            borderRadius: BorderRadius.circular(10),
                                          ),
                                          padding: const EdgeInsets.all(3),
                                          child: Row(
                                            children: [
                                              _buildSegmentButton(
                                                label: 'Ada & Laik',
                                                isSelected: currentAns == ChecklistAnswer.presentAndFit,
                                                activeColor: AppColors.green,
                                                onTap: () => setState(() => _answers[item.id] = ChecklistAnswer.presentAndFit),
                                              ),
                                              _buildSegmentButton(
                                                label: 'Ada, Tidak Laik',
                                                isSelected: currentAns == ChecklistAnswer.presentNotFit,
                                                activeColor: AppColors.orange,
                                                onTap: () => setState(() => _answers[item.id] = ChecklistAnswer.presentNotFit),
                                              ),
                                              _buildSegmentButton(
                                                label: 'Tidak Ada',
                                                isSelected: currentAns == ChecklistAnswer.notPresent,
                                                activeColor: AppColors.red,
                                                onTap: () => setState(() => _answers[item.id] = ChecklistAnswer.notPresent),
                                              ),
                                            ],
                                          ),
                                        ),

                                        // Photo Row (if required)
                                        if (item.photoRequired) ...[
                                          const SizedBox(height: 10),
                                          Row(
                                            children: [
                                              InkWell(
                                                onTap: () => _takePhoto(item.id),
                                                borderRadius: BorderRadius.circular(8),
                                                child: Container(
                                                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                                                  decoration: BoxDecoration(
                                                    color: AppColors.blueBg,
                                                    borderRadius: BorderRadius.circular(8),
                                                  ),
                                                  child: Row(
                                                    mainAxisSize: MainAxisSize.min,
                                                    children: [
                                                      const Icon(Icons.camera_alt, color: AppColors.blue, size: 16),
                                                      const SizedBox(width: 6),
                                                      Text(
                                                        photoPath != null ? 'Foto Ulang' : 'Ambil Foto',
                                                        style: const TextStyle(
                                                          color: AppColors.blue,
                                                          fontSize: 13,
                                                          fontWeight: FontWeight.w600,
                                                        ),
                                                      ),
                                                    ],
                                                  ),
                                                ),
                                              ),
                                              const SizedBox(width: 10),
                                              if (photoPath != null)
                                                const Row(
                                                  children: [
                                                    Icon(Icons.check_circle, color: AppColors.green, size: 16),
                                                    SizedBox(width: 4),
                                                    Text('Foto terlampir', style: TextStyle(fontSize: 12, color: AppColors.green)),
                                                  ],
                                                )
                                              else
                                                const Text(
                                                  'Wajib sertakan foto',
                                                  style: TextStyle(fontSize: 12, color: AppColors.orange),
                                                ),
                                            ],
                                          ),
                                        ],
                                      ],
                                    ),
                                  ),
                                ],
                              );
                            }).toList(),
                          ),
                        ),
                        const SizedBox(height: 16),
                      ],
                    );
                  }),

                  // Vehicle Overview Photo Card
                  const Padding(
                    padding: EdgeInsets.symmetric(horizontal: 4, vertical: 8),
                    child: Text(
                      'FOTO KESELURUHAN KENDARAAN',
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
                    padding: const EdgeInsets.all(16),
                    child: Row(
                      children: [
                        InkWell(
                          onTap: () => _takePhoto('vehicle'),
                          borderRadius: BorderRadius.circular(8),
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            decoration: BoxDecoration(
                              color: AppColors.blueBg,
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Row(
                              children: [
                                const Icon(Icons.camera_alt, color: AppColors.blue, size: 18),
                                const SizedBox(width: 8),
                                Text(
                                  _vehicleOverviewPhoto != null ? 'Foto Ulang Kendaraan' : 'Ambil Foto Kendaraan',
                                  style: const TextStyle(color: AppColors.blue, fontSize: 13, fontWeight: FontWeight.w600),
                                ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Text(
                            _vehicleOverviewPhoto != null ? 'Tampak depan terlampir' : 'Tampak depan, plat nomor terlihat',
                            style: TextStyle(
                              fontSize: 13,
                              color: _vehicleOverviewPhoto != null ? AppColors.green : AppColors.label2,
                              fontWeight: _vehicleOverviewPhoto != null ? FontWeight.w600 : FontWeight.normal,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 28),

                  // Continue to Decision Button
                  ElevatedButton(
                    onPressed: isComplete ? _goToDecision : null,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.blue,
                      foregroundColor: Colors.white,
                      minimumSize: const Size(double.infinity, 52),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    ),
                    child: const Text('Lanjut ke Penetapan Keputusan', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
                  ),
                  if (!isComplete) ...[
                    const SizedBox(height: 8),
                    Text(
                      'Lengkapi pemeriksaan seluruh ${checklist.length} item fisik di atas untuk melanjutkan',
                      textAlign: TextAlign.center,
                      style: const TextStyle(fontSize: 12, color: AppColors.label2),
                    ),
                  ],
                  const SizedBox(height: 20),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSegmentButton({
    required String label,
    required bool isSelected,
    required Color activeColor,
    required VoidCallback onTap,
  }) {
    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 8),
          decoration: BoxDecoration(
            color: isSelected ? activeColor : Colors.transparent,
            borderRadius: BorderRadius.circular(8),
            boxShadow: isSelected
                ? [
                    BoxShadow(
                      color: activeColor.withValues(alpha: 0.3),
                      blurRadius: 4,
                      offset: const Offset(0, 1),
                    ),
                  ]
                : null,
          ),
          child: Text(
            label,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 11.5,
              fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
              color: isSelected ? Colors.white : AppColors.label,
            ),
          ),
        ),
      ),
    );
  }
}

