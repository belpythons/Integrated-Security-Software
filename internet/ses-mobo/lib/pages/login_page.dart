import 'package:flutter/material.dart';
import '../core/app_colors.dart';
import '../api/api_config.dart';
import '../api/api_client.dart';
import '../models/gate_app_models.dart';
import '../services/mock_permit_service.dart';
import 'gate_selection_page.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _service = MockPermitService();
  final _apiClient = ApiClient();
  final _usernameController = TextEditingController(text: 'dedi.k');
  final _passwordController = TextEditingController(text: 'ses2026');
  bool _obscurePassword = true;
  bool _isLoading = false;
  String? _errorMessage;

  @override
  void dispose() {
    _apiClient.close();
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _doLogin() async {
    final username = _usernameController.text.trim();
    final password = _passwordController.text.trim();

    if (username.isEmpty) {
      setState(() => _errorMessage = 'Username wajib diisi.');
      return;
    }
    if (password.isEmpty) {
      setState(() => _errorMessage = 'Password wajib diisi.');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      // 1. Percobaan autentikasi langsung ke backend ses-api
      final res = await _apiClient.login(username, password);

      if (res.success && res.data != null) {
        final data = res.data!;
        ApiConfig.authToken = data.token;
        ApiConfig.authenticatedUsername = data.username;
        ApiConfig.authenticatedFullName = data.fullName;
        ApiConfig.authenticatedRoles = data.roles;

        // Set active officer session
        _service.currentUser = GateSecurityUser(
          username: data.username,
          name: data.fullName,
          employeeId: data.username,
          pin: '',
          defaultGate: 'KPBR Gate',
          active: true,
        );
        _service.currentGate = 'KPBR Gate';

        if (!mounted) return;
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(
            builder: (_) => const GateSelectionPage(preselectedGate: 'KPBR Gate'),
          ),
        );
        return;
      }

      // 2. Jika server mengembalikan respon gagal autentikasi kredensial (401/403)
      if (!res.message.contains('Koneksi gagal') &&
          !res.message.contains('Tidak dapat menghubungi') &&
          !res.message.contains('Waktu koneksi')) {
        setState(() {
          _errorMessage = res.message.isNotEmpty ? res.message : 'Username atau password tidak valid.';
          _isLoading = false;
        });
        return;
      }

      // 3. Fallback Graceful Sesi Lokal (jika jaringan posko offline/server belum terhubung)
      final localUser = _service.users.firstWhere(
        (x) => x.username.toLowerCase() == username.toLowerCase(),
        orElse: () => GateSecurityUser(
          username: '',
          pin: '',
          name: '',
          employeeId: '',
          defaultGate: '',
          active: false,
        ),
      );

      if (localUser.username.isNotEmpty && localUser.active) {
        ApiConfig.authenticatedUsername = localUser.username;
        ApiConfig.authenticatedFullName = localUser.name;
        ApiConfig.authenticatedRoles = const ['ROLE_SECURITY'];
        _service.currentUser = localUser;
        _service.currentGate = localUser.defaultGate;

        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Server ses-api offline. Menggunakan sesi kredensial tersimpan di perangkat.',
            ),
            backgroundColor: AppColors.orange,
            duration: Duration(seconds: 3),
          ),
        );

        Navigator.of(context).pushReplacement(
          MaterialPageRoute(
            builder: (_) => GateSelectionPage(preselectedGate: localUser.defaultGate),
          ),
        );
        return;
      }

      setState(() {
        _errorMessage = 'Tidak dapat menghubungi server SES (:8084). Pastikan jaringan atau server ses-api aktif.';
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _errorMessage = 'Terjadi kesalahan sistem: $e';
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        width: double.infinity,
        height: double.infinity,
        decoration: const BoxDecoration(
          gradient: RadialGradient(
            center: Alignment(0.0, -0.35),
            radius: 1.15,
            colors: [
              Color(0xFF002244),
              Color(0xFF001224),
              Color(0xFF000814),
            ],
            stops: [0.0, 0.60, 1.0],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: Container(
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.95),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: Colors.white.withValues(alpha: 0.55), width: 1),
                    boxShadow: [
                      BoxShadow(
                        color: const Color(0xFF000A1E).withValues(alpha: 0.55),
                        blurRadius: 45,
                        offset: const Offset(0, 18),
                      ),
                      BoxShadow(
                        color: const Color(0xFF00F0FF).withValues(alpha: 0.12),
                        blurRadius: 30,
                        offset: const Offset(0, 0),
                      ),
                    ],
                  ),
                  padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 34),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // Logo PT Badak NGL
                      Center(
                        child: Image.asset(
                          'assets/image/ptb_logo.png',
                          height: 72,
                          fit: BoxFit.contain,
                          errorBuilder: (context, error, stackTrace) => Container(
                            width: 68,
                            height: 68,
                            decoration: BoxDecoration(
                              color: const Color(0xFF003F7D),
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: const Icon(Icons.shield, color: Colors.white, size: 36),
                          ),
                        ),
                      ),
                      const SizedBox(height: 14),

                      // Brand Header Identik SES Web
                      const Text(
                        'Security Suite',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 26,
                          fontWeight: FontWeight.w800,
                          letterSpacing: 1.4,
                          color: Color(0xFF003F7D),
                        ),
                      ),
                      const SizedBox(height: 6),
                      const Text(
                        'Sistem Pengamanan Terpadu Kilang PT Badak NGL',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 12.5,
                          fontWeight: FontWeight.w500,
                          color: Color(0xFF64748B),
                          letterSpacing: 0.2,
                        ),
                      ),
                      const SizedBox(height: 28),

                      // Error Alert Box
                      if (_errorMessage != null) ...[
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                          margin: const EdgeInsets.only(bottom: 18),
                          decoration: BoxDecoration(
                            color: const Color(0xFFFEE2E2),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: const Color(0xFFFCA5A5)),
                          ),
                          child: Row(
                            children: [
                              const Icon(Icons.error_outline, color: Color(0xFFDC2626), size: 18),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  _errorMessage!,
                                  style: const TextStyle(
                                    color: Color(0xFF991B1B),
                                    fontSize: 12.5,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],

                      // Username Input Field
                      const Text(
                        'Username',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: Color(0xFF1E293B),
                        ),
                      ),
                      const SizedBox(height: 6),
                      TextField(
                        controller: _usernameController,
                        textInputAction: TextInputAction.next,
                        style: const TextStyle(fontSize: 14, color: Color(0xFF0F172A)),
                        decoration: InputDecoration(
                          hintText: 'Masukkan username atau badge',
                          hintStyle: const TextStyle(fontSize: 13, color: Color(0xFF94A3B8)),
                          prefixIcon: const Icon(Icons.person_outline, color: Color(0xFF64748B), size: 20),
                          filled: true,
                          fillColor: const Color(0xFFF8FAFC),
                          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10),
                            borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10),
                            borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10),
                            borderSide: const BorderSide(color: Color(0xFF0064D9), width: 1.5),
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),

                      // Password Input Field
                      const Text(
                        'Password',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: Color(0xFF1E293B),
                        ),
                      ),
                      const SizedBox(height: 6),
                      TextField(
                        controller: _passwordController,
                        obscureText: _obscurePassword,
                        textInputAction: TextInputAction.done,
                        onSubmitted: (_) => _doLogin(),
                        style: const TextStyle(fontSize: 14, color: Color(0xFF0F172A)),
                        decoration: InputDecoration(
                          hintText: 'Masukkan password',
                          hintStyle: const TextStyle(fontSize: 13, color: Color(0xFF94A3B8)),
                          prefixIcon: const Icon(Icons.lock_outline, color: Color(0xFF64748B), size: 20),
                          suffixIcon: IconButton(
                            icon: Icon(
                              _obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                              color: const Color(0xFF64748B),
                              size: 20,
                            ),
                            onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                          ),
                          filled: true,
                          fillColor: const Color(0xFFF8FAFC),
                          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10),
                            borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10),
                            borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10),
                            borderSide: const BorderSide(color: Color(0xFF0064D9), width: 1.5),
                          ),
                        ),
                      ),
                      const SizedBox(height: 24),

                      // Tombol Login Gradient Pill
                      Container(
                        decoration: BoxDecoration(
                          gradient: const LinearGradient(
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                            colors: [Color(0xFF004B99), Color(0xFF002D5A)],
                          ),
                          borderRadius: BorderRadius.circular(26),
                          boxShadow: [
                            BoxShadow(
                              color: const Color(0xFF004B99).withValues(alpha: 0.35),
                              blurRadius: 14,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                        child: Material(
                          color: Colors.transparent,
                          child: InkWell(
                            borderRadius: BorderRadius.circular(26),
                            onTap: _isLoading ? null : _doLogin,
                            child: Padding(
                              padding: const EdgeInsets.symmetric(vertical: 14),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  if (_isLoading) ...[
                                    const SizedBox(
                                      width: 20,
                                      height: 20,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2.2,
                                        valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                                      ),
                                    ),
                                    const SizedBox(width: 10),
                                    const Text(
                                      'Menghubungkan...',
                                      style: TextStyle(
                                        color: Colors.white,
                                        fontSize: 15,
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                  ] else ...[
                                    const Icon(Icons.login_rounded, color: Colors.white, size: 20),
                                    const SizedBox(width: 8),
                                    const Text(
                                      'Login',
                                      style: TextStyle(
                                        color: Colors.white,
                                        fontSize: 15,
                                        fontWeight: FontWeight.w700,
                                        letterSpacing: 0.3,
                                      ),
                                    ),
                                  ],
                                ],
                              ),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 28),

                      // Footer Card Identik SES Web
                      const Divider(height: 1, color: Color(0xFFE2E8F0)),
                      const SizedBox(height: 14),
                      const Text(
                        '© 2026 Security Suite • PT BADAK NGL',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 11.5,
                          fontWeight: FontWeight.w500,
                          color: Color(0xFF64748B),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
