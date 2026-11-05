import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../models/inspection_checklist_model.dart';

class Checklist11PointsWidget extends StatefulWidget {
  final VehicleInspectionReport report;
  final bool isZone1;
  final ValueChanged<VehicleInspectionReport>? onChanged;

  const Checklist11PointsWidget({
    super.key,
    required this.report,
    this.isZone1 = false,
    this.onChanged,
  });

  @override
  State<Checklist11PointsWidget> createState() => _Checklist11PointsWidgetState();
}

class _Checklist11PointsWidgetState extends State<Checklist11PointsWidget> {
  late VehicleInspectionReport _report;

  @override
  void initState() {
    super.initState();
    _report = widget.report;
  }

  void _notifyChange() {
    setState(() {});
    widget.onChanged?.call(_report);
  }

  @override
  Widget build(BuildContext context) {
    final bool isTotalPass = _report.isTotalPassed(isZone1: widget.isZone1);
    final failedCategories = _report.getFailedCategoryNames();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Status Banner
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: isTotalPass
                ? AppColors.success.withValues(alpha: 0.12)
                : AppColors.danger.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
              color: isTotalPass ? AppColors.success : AppColors.danger,
              width: 1.5,
            ),
          ),
          child: Row(
            children: [
              Icon(
                isTotalPass ? Icons.check_circle : Icons.warning_amber_rounded,
                color: isTotalPass ? AppColors.success : AppColors.danger,
                size: 32,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      isTotalPass
                          ? 'Kelaikan Fisik: MEMENUHI SYARAT (PASS)'
                          : 'Kelaikan Fisik: DITOLAK (FAIL)',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: isTotalPass ? AppColors.success : AppColors.danger,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      isTotalPass
                          ? 'Seluruh 11 kategori kelaikan dan APD terverifikasi standar.'
                          : 'Pilar tidak layak: ${failedCategories.isEmpty ? "Kelengkapan APD Pengemudi" : failedCategories.join(", ")}',
                      style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // Section: Driver APD
        Card(
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.person_pin, color: AppColors.navy),
                    const SizedBox(width: 8),
                    const Text(
                      'Pemeriksaan APD Pengemudi',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                    const Spacer(),
                    if (widget.isZone1)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                          color: AppColors.warning.withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: const Text(
                          'Zone 1 (Wajib Helm+Kacamata)',
                          style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.warning),
                        ),
                      ),
                  ],
                ),
                const Divider(height: 20),
                CheckboxListTile(
                  dense: true,
                  title: const Text('Sepatu Keselamatan (Safety Shoes)'),
                  value: _report.driverPpe.safetyShoes,
                  onChanged: (val) {
                    _report.driverPpe.safetyShoes = val ?? false;
                    _notifyChange();
                  },
                ),
                CheckboxListTile(
                  dense: true,
                  title: const Text('Celana Panjang Bahan Tebal (Bukan Robek/Pendek)'),
                  value: _report.driverPpe.longPants,
                  onChanged: (val) {
                    _report.driverPpe.longPants = val ?? false;
                    _notifyChange();
                  },
                ),
                CheckboxListTile(
                  dense: true,
                  title: const Text('Baju Berkerah / Wearpack / Coverall'),
                  value: _report.driverPpe.collaredShirtOrCoverall,
                  onChanged: (val) {
                    _report.driverPpe.collaredShirtOrCoverall = val ?? false;
                    _notifyChange();
                  },
                ),
                CheckboxListTile(
                  dense: true,
                  title: Text(
                    'Helm Keselamatan & Kacamata Safety ${widget.isZone1 ? "(WAJIB Zone 1)" : "(Opsional Zone 2/3)"}',
                  ),
                  value: _report.driverPpe.helmetGlasses,
                  onChanged: (val) {
                    _report.driverPpe.helmetGlasses = val ?? false;
                    _notifyChange();
                  },
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),

        // Section: 11 Categories List
        const Text(
          '11 Pilar Kelaikan Teknis Kendaraan (Form kkb_kks.jpg)',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: AppColors.textPrimary),
        ),
        const SizedBox(height: 8),
        ListView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          itemCount: _report.categories.length,
          itemBuilder: (context, index) {
            final cat = _report.categories[index];
            final bool isPass = cat.isPass;

            return Card(
              margin: const EdgeInsets.only(bottom: 8),
              shape: RoundedRectangleBorder(
                side: BorderSide(
                  color: isPass ? AppColors.border : AppColors.danger.withValues(alpha: 0.6),
                ),
                borderRadius: BorderRadius.circular(8),
              ),
              child: ExpansionTile(
                initiallyExpanded: false,
                leading: CircleAvatar(
                  radius: 14,
                  backgroundColor: isPass ? AppColors.success : AppColors.danger,
                  child: Icon(
                    isPass ? Icons.check : Icons.close,
                    color: Colors.white,
                    size: 16,
                  ),
                ),
                title: Text(
                  cat.categoryTitle,
                  style: TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 13,
                    color: isPass ? AppColors.textPrimary : AppColors.danger,
                  ),
                ),
                subtitle: Text(
                  cat.subItemsDescription,
                  style: const TextStyle(fontSize: 11, color: AppColors.textSecondary),
                ),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ChoiceChip(
                      label: const Text('PASS', style: TextStyle(fontSize: 11)),
                      selected: isPass,
                      selectedColor: AppColors.success.withValues(alpha: 0.2),
                      onSelected: (selected) {
                        if (selected) {
                          cat.status = InspectionStatus.pass;
                          _notifyChange();
                        }
                      },
                    ),
                    const SizedBox(width: 4),
                    ChoiceChip(
                      label: const Text('FAIL', style: TextStyle(fontSize: 11)),
                      selected: !isPass,
                      selectedColor: AppColors.danger.withValues(alpha: 0.2),
                      onSelected: (selected) {
                        if (selected) {
                          cat.status = InspectionStatus.fail;
                          _notifyChange();
                        }
                      },
                    ),
                  ],
                ),
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: TextFormField(
                      initialValue: cat.notes,
                      decoration: const InputDecoration(
                        labelText: 'Catatan Temuan / Defek Fisik',
                        hintText: 'Misal: Ban aus, lampu sein kiri putus...',
                        isDense: true,
                        border: OutlineInputBorder(),
                      ),
                      onChanged: (val) {
                        cat.notes = val;
                        widget.onChanged?.call(_report);
                      },
                    ),
                  ),
                ],
              ),
            );
          },
        ),
      ],
    );
  }
}
