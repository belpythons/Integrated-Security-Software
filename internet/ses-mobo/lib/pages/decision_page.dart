import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../models/gate_app_models.dart';
import '../services/mock_permit_service.dart';
import 'result_page.dart';

class DecisionPage extends StatefulWidget {
  final String requestId;
  final String driverId;
  final String vehicleId;
  final Map<int, ChecklistAnswer> answers;
  final Map<int, String> photos;
  final String? vehicleOverviewPhoto;

  const DecisionPage({
    super.key,
    required this.requestId,
    required this.driverId,
    required this.vehicleId,
    required this.answers,
    required this.photos,
    this.vehicleOverviewPhoto,
  });

  @override
  State<DecisionPage> createState() => _DecisionPageState();
}

class _DecisionPageState extends State<DecisionPage> {
  final _service = MockPermitService();
  bool? _isFit;
  final _remarksController = TextEditingController();
  final _badgeController = TextEditingController(text: 'V-082');
  bool _ktpHeld = true;

  @override
  void initState() {
    super.initState();
    // Default conclusion based on whether any mandatory item failed
    final checklist = _service.checklist;
    final mandatoryFailed = checklist.any(
      (c) => c.mandatory && widget.answers[c.id] != ChecklistAnswer.presentAndFit,
    );
    _isFit = !mandatoryFailed;
  }

  void _submitDecision() {
    if (_isFit == null) return;
    if (_isFit == false && _remarksController.text.trim().length < 5) return;

    final allPhotos = widget.photos.values.toList();
    if (widget.vehicleOverviewPhoto != null) {
      allPhotos.add(widget.vehicleOverviewPhoto!);
    }

    final log = _service.commitCheckin(
      requestId: widget.requestId,
      driverId: widget.driverId,
      vehicleId: widget.vehicleId,
      isFit: _isFit!,
      fastReentry: false,
      remarks: _remarksController.text.trim(),
      answers: widget.answers,
      photos: allPhotos,
      visitorBadgeNo: _badgeController.text.trim().isEmpty ? 'V-082' : _badgeController.text.trim(),
      ktpHeld: _ktpHeld,
    );

    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => ResultPage(logId: log.id)),
      (route) => route.isFirst,
    );
  }

  @override
  Widget build(BuildContext context) {
    final checklist = _service.checklist;
    final fitCount = checklist.where((c) => widget.answers[c.id] == ChecklistAnswer.presentAndFit).length;
    final notFitCount = checklist.where((c) => widget.answers[c.id] == ChecklistAnswer.presentNotFit).length;
    final notPresentCount = checklist.where((c) => widget.answers[c.id] == ChecklistAnswer.notPresent).length;

    final badMandatory = checklist.where(
      (c) => c.mandatory && widget.answers[c.id] != ChecklistAnswer.presentAndFit,
    ).toList();

    final missingPhotos = checklist.where(
      (c) => c.photoRequired && !widget.photos.containsKey(c.id),
    ).toList();

    final canSubmit = _isFit != null && (_isFit == true || _remarksController.text.trim().length >= 5);

    return Scaffold(
      backgroundColor: AppColors.bg,
      appBar: AppBar(
        title: const Text('Keputusan Izin Masuk'),
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
              // 1. Stats Row
              Row(
                children: [
                  _buildStatCard('Ada & Laik', fitCount, AppColors.green),
                  const SizedBox(width: 8),
                  _buildStatCard('Tidak Laik', notFitCount, AppColors.orange),
                  const SizedBox(width: 8),
                  _buildStatCard('Tidak Ada', notPresentCount, AppColors.red),
                ],
              ),
              const SizedBox(height: 16),

              // 2. Alert Banners
              if (badMandatory.isNotEmpty) ...[
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.redBg,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: AppColors.red.withValues(alpha: 0.3), width: 0.5),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.warning_amber_rounded, color: AppColors.red, size: 20),
                          const SizedBox(width: 8),
                          Text(
                            '${badMandatory.length} item wajib tidak laik',
                            style: const TextStyle(color: AppColors.red, fontWeight: FontWeight.w700, fontSize: 14),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      ...badMandatory.map(
                        (c) => Padding(
                          padding: const EdgeInsets.only(left: 28, top: 2),
                          child: Text('• ${c.name}', style: const TextStyle(fontSize: 13, color: AppColors.label)),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
              ] else ...[
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.greenBg,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: AppColors.green.withValues(alpha: 0.3), width: 0.5),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.check_circle, color: AppColors.green, size: 20),
                      SizedBox(width: 10),
                      Text(
                        'Seluruh item wajib berstatus Ada & Laik.',
                        style: TextStyle(color: AppColors.green, fontWeight: FontWeight.w600, fontSize: 14),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
              ],

              if (missingPhotos.isNotEmpty) ...[
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.orangeBg,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: AppColors.orange.withValues(alpha: 0.3), width: 0.5),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.camera_alt, color: AppColors.orange, size: 20),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          '${missingPhotos.length} foto belum terlampir. Anda tetap dapat melanjutkan; rekaman akan ditandai.',
                          style: const TextStyle(color: AppColors.orange, fontSize: 13),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
              ],

              // 3. Choice Cards
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                child: Text(
                  'PENETAPAN KEPUTUSAN',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.label2, letterSpacing: 0.5),
                ),
              ),
              Row(
                children: [
                  Expanded(
                    child: _buildChoiceCard(
                      label: 'Diizinkan Masuk (Laik)',
                      icon: Icons.check,
                      color: AppColors.green,
                      bg: AppColors.greenBg,
                      isSelected: _isFit == true,
                      onTap: () => setState(() => _isFit = true),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _buildChoiceCard(
                      label: 'Ditolak Masuk (Tidak Laik)',
                      icon: Icons.close,
                      color: AppColors.red,
                      bg: AppColors.redBg,
                      isSelected: _isFit == false,
                      onTap: () => setState(() => _isFit = false),
                    ),
                  ),
                ],
              ),

              if (_isFit == true && badMandatory.isNotEmpty) ...[
                const SizedBox(height: 8),
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 4),
                  child: Text(
                    'Anda mengizinkan masuk meskipun ada item wajib yang tidak laik. Jelaskan alasannya pada catatan petugas.',
                    style: TextStyle(color: AppColors.orange, fontSize: 12),
                  ),
                ),
              ],

              const SizedBox(height: 20),

              // 4. Remarks Field
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                child: Text(
                  _isFit == false ? 'CATATAN PETUGAS (WAJIB, MIN. 5 KARAKTER)' : 'CATATAN PETUGAS (OPSIONAL)',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: _isFit == false ? AppColors.red : AppColors.label2,
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
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                child: TextField(
                  controller: _remarksController,
                  maxLines: 3,
                  onChanged: (_) => setState(() {}),
                  decoration: InputDecoration(
                    hintText: _isFit == false
                        ? 'Mengapa kendaraan tidak laik? Apa yang harus diperbaiki pengemudi?'
                        : 'Catatan tambahan untuk proses check-in ini (opsional)',
                    border: InputBorder.none,
                    enabledBorder: InputBorder.none,
                    focusedBorder: InputBorder.none,
                    contentPadding: EdgeInsets.zero,
                  ),
                ),
              ),

              const SizedBox(height: 20),

              // 5. SES Security Posko Controls (DTO ses-api)
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                child: Text(
                  'KONTROL POSKO KEAMANAN SES (ses-api)',
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
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                      child: TextField(
                        controller: _badgeController,
                        decoration: const InputDecoration(
                          icon: Icon(Icons.badge_outlined, color: AppColors.blue),
                          labelText: 'Nomor Seri Visitor Badge',
                          hintText: 'Misal: V-082',
                          border: InputBorder.none,
                        ),
                      ),
                    ),
                    const Divider(height: 1, indent: 16, color: AppColors.sep),
                    SwitchListTile(
                      value: _ktpHeld,
                      onChanged: (val) => setState(() => _ktpHeld = val),
                      secondary: Icon(
                        Icons.credit_card,
                        color: _ktpHeld ? AppColors.green : AppColors.red,
                      ),
                      title: const Text(
                        'Fisik KTP Disimpan Petugas',
                        style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
                      ),
                      subtitle: Text(
                        _ktpHeld
                            ? 'SOP Dipatuhi: KTP supir ditahan di posko selama kunjungan'
                            : 'Wajib ditahan! Backend ses-api akan menolak jika KTP tidak ditahan',
                        style: TextStyle(
                          fontSize: 12,
                          color: _ktpHeld ? AppColors.green : AppColors.red,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 32),

              // 6. Submit Button
              ElevatedButton(
                onPressed: canSubmit ? _submitDecision : null,
                style: ElevatedButton.styleFrom(
                  backgroundColor: _isFit == false ? AppColors.red : AppColors.green,
                  foregroundColor: Colors.white,
                  minimumSize: const Size(double.infinity, 52),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                ),
                child: Text(
                  _isFit == false ? 'Tolak Check-In' : 'Selesaikan Check-In',
                  style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatCard(String label, int count, Color color) {
    return Expanded(
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.sep, width: 0.5),
        ),
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Column(
          children: [
            Text(
              '$count',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700, color: color, fontFamily: 'monospace'),
            ),
            const SizedBox(height: 2),
            Text(
              label,
              style: const TextStyle(fontSize: 11, color: AppColors.label2, fontWeight: FontWeight.w500),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildChoiceCard({
    required String label,
    required IconData icon,
    required Color color,
    required Color bg,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 18),
        decoration: BoxDecoration(
          color: isSelected ? bg : AppColors.card,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isSelected ? color : AppColors.sep,
            width: isSelected ? 2 : 0.5,
          ),
        ),
        child: Column(
          children: [
            Icon(icon, color: color, size: 36),
            const SizedBox(height: 8),
            Text(
              label,
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w700,
                color: isSelected ? color : AppColors.label,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

