import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../core/app_colors.dart';
import '../core/qr_parser.dart';
import 'permit_detail_page.dart';

class ScannerPage extends StatefulWidget {
  const ScannerPage({super.key});

  @override
  State<ScannerPage> createState() => _ScannerPageState();
}

class _ScannerPageState extends State<ScannerPage> with SingleTickerProviderStateMixin {
  late MobileScannerController _scannerController;
  late AnimationController _animController;
  late Animation<double> _laserAnimation;
  bool _isTorchOn = false;
  bool _hasScanned = false;

  @override
  void initState() {
    super.initState();
    _scannerController = MobileScannerController(
      detectionSpeed: DetectionSpeed.noDuplicates,
      facing: CameraFacing.back,
      torchEnabled: false,
    );

    _animController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat(reverse: true);

    _laserAnimation = Tween<double>(begin: 20, end: 240).animate(
      CurvedAnimation(parent: _animController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _scannerController.dispose();
    _animController.dispose();
    super.dispose();
  }

  void _onDetect(BarcodeCapture capture) {
    if (_hasScanned) return;
    final List<Barcode> barcodes = capture.barcodes;
    for (final barcode in barcodes) {
      final code = barcode.rawValue?.trim();
      if (code != null && code.isNotEmpty) {
        final parsed = QrParser.parse(code);
        if (parsed.isNotEmpty) {
          _hasScanned = true;
          _navigateToPermit(parsed);
          break;
        }
      }
    }
  }

  void _navigateToPermit(String permitId) {
    final cleanId = QrParser.parse(permitId);
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(
        builder: (_) => PermitDetailPage(permitId: cleanId),
      ),
    );
  }

  void _toggleTorch() {
    _scannerController.toggleTorch();
    setState(() => _isTorchOn = !_isTorchOn);
  }

  void _showManualInputDialog() {
    final controller = TextEditingController(text: 'REQ-MAT-20261103-');
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('Input Nomor Izin Manual', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Masukkan Nomor Izin Masuk Barang fisik (format: REQ-MAT-yyyyMMdd-XXXX atau IMB-2026-XXXX).',
              style: TextStyle(fontSize: 13, color: AppColors.label2),
            ),
            const SizedBox(height: 14),
            TextField(
              controller: controller,
              textCapitalization: TextCapitalization.characters,
              autofocus: true,
              style: const TextStyle(fontFamily: 'monospace', fontSize: 16, fontWeight: FontWeight.w600),
              decoration: InputDecoration(
                filled: true,
                fillColor: AppColors.bg,
                hintText: 'REQ-MAT-...',
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
              ),
              onSubmitted: (val) {
                Navigator.of(ctx).pop();
                if (val.trim().isNotEmpty) _navigateToPermit(val.trim());
              },
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Batal', style: TextStyle(color: AppColors.label2)),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              if (controller.text.trim().isNotEmpty) {
                _navigateToPermit(controller.text.trim());
              }
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.blue,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
            ),
            child: const Text('Cari Nomor', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          // 1. Live Camera Stream
          MobileScanner(
            controller: _scannerController,
            onDetect: _onDetect,
            errorBuilder: (context, error) {
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.videocam_off, color: Colors.white54, size: 54),
                      const SizedBox(height: 16),
                      const Text(
                        'Kamera tidak dapat diakses atau izin kamera belum diizinkan.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'Pastikan izin kamera ponsel telah diaktifkan, atau gunakan input nomor izin manual di bawah.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Colors.white70, fontSize: 13),
                      ),
                      const SizedBox(height: 20),
                      ElevatedButton.icon(
                        onPressed: _showManualInputDialog,
                        icon: const Icon(Icons.keyboard, size: 18),
                        label: const Text('Input Nomor Izin Manual'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.blue,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),

          // 2. Viewfinder Box & Controls
          SafeArea(
            child: Column(
              children: [
                // Top App Bar Controls
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      IconButton.filled(
                        onPressed: () => Navigator.of(context).pop(),
                        icon: const Icon(Icons.close, color: Colors.white, size: 22),
                        style: IconButton.styleFrom(backgroundColor: Colors.black54),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                        decoration: BoxDecoration(
                          color: Colors.black54,
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(color: Colors.white24, width: 0.5),
                        ),
                        child: const Row(
                          children: [
                            Icon(Icons.qr_code_scanner, color: AppColors.cyan, size: 18),
                            SizedBox(width: 8),
                            Text(
                              'Pindai Izin Barang',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 15,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                        ),
                      ),
                      IconButton.filled(
                        onPressed: _toggleTorch,
                        icon: Icon(
                          _isTorchOn ? Icons.flash_on : Icons.flash_off,
                          color: _isTorchOn ? Colors.amber : Colors.white,
                          size: 22,
                        ),
                        style: IconButton.styleFrom(backgroundColor: Colors.black54),
                      ),
                    ],
                  ),
                ),

                const Spacer(),

                // Center Viewfinder
                Center(
                  child: SizedBox(
                    width: 260,
                    height: 260,
                    child: Stack(
                      children: [
                        // Viewfinder border box
                        Container(
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(24),
                            border: Border.all(color: Colors.white24, width: 1.2),
                          ),
                        ),
                        // Corner brackets (SES Mono Blue / Cyan Accent)
                        Align(
                          alignment: Alignment.topLeft,
                          child: Container(
                            width: 36,
                            height: 36,
                            decoration: const BoxDecoration(
                              border: Border(
                                top: BorderSide(color: AppColors.cyan, width: 4),
                                left: BorderSide(color: AppColors.cyan, width: 4),
                              ),
                              borderRadius: BorderRadius.only(topLeft: Radius.circular(24)),
                            ),
                          ),
                        ),
                        Align(
                          alignment: Alignment.topRight,
                          child: Container(
                            width: 36,
                            height: 36,
                            decoration: const BoxDecoration(
                              border: Border(
                                top: BorderSide(color: AppColors.cyan, width: 4),
                                right: BorderSide(color: AppColors.cyan, width: 4),
                              ),
                              borderRadius: BorderRadius.only(topRight: Radius.circular(24)),
                            ),
                          ),
                        ),
                        Align(
                          alignment: Alignment.bottomLeft,
                          child: Container(
                            width: 36,
                            height: 36,
                            decoration: const BoxDecoration(
                              border: Border(
                                bottom: BorderSide(color: AppColors.cyan, width: 4),
                                left: BorderSide(color: AppColors.cyan, width: 4),
                              ),
                              borderRadius: BorderRadius.only(bottomLeft: Radius.circular(24)),
                            ),
                          ),
                        ),
                        Align(
                          alignment: Alignment.bottomRight,
                          child: Container(
                            width: 36,
                            height: 36,
                            decoration: const BoxDecoration(
                              border: Border(
                                bottom: BorderSide(color: AppColors.cyan, width: 4),
                                right: BorderSide(color: AppColors.cyan, width: 4),
                              ),
                              borderRadius: BorderRadius.only(bottomRight: Radius.circular(24)),
                            ),
                          ),
                        ),

                        // Animated Laser Line
                        AnimatedBuilder(
                          animation: _laserAnimation,
                          builder: (context, child) {
                            return Positioned(
                              top: _laserAnimation.value,
                              left: 12,
                              right: 12,
                              child: Container(
                                height: 2.5,
                                decoration: BoxDecoration(
                                  color: AppColors.cyan,
                                  boxShadow: [
                                    BoxShadow(
                                      color: AppColors.cyan.withValues(alpha: 0.9),
                                      blurRadius: 10,
                                      spreadRadius: 1.5,
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                      ],
                    ),
                  ),
                ),

                const SizedBox(height: 20),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  decoration: BoxDecoration(
                    color: Colors.black54,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: const Text(
                    'Arahkan kamera ke QR Code surat jalan atau dokumen pengemudi',
                    style: TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w500),
                  ),
                ),

                const Spacer(),

                // Real Field Action Button: Manual Input
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
                  child: ElevatedButton.icon(
                    onPressed: _showManualInputDialog,
                    icon: const Icon(Icons.keyboard_outlined, size: 22),
                    label: const Text(
                      'Input Nomor Izin Manual',
                      style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.white.withValues(alpha: 0.92),
                      foregroundColor: const Color(0xFF003F7D),
                      elevation: 4,
                      minimumSize: const Size(double.infinity, 50),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
