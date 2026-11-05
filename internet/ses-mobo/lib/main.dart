import 'package:flutter/material.dart';
import 'core/app_theme.dart';
import 'models/permit_header.dart';
import 'pages/login_page.dart';
import 'pages/posko_home_page.dart';
import 'services/mock_permit_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final service = MockPermitService();
  service.currentUser = service.users.first;
  service.currentGate = service.gates.first;

  runApp(const SesMoboApp());
}

class SesMoboApp extends StatelessWidget {
  final List<MaterialPermitHeader>? permits;

  const SesMoboApp({super.key, this.permits});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SES Security Suite Mobile - PT. S',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      home: permits != null
          ? PoskoHomePage(
              permits: permits,
              inspectorNip: 'SEC-88210',
            )
          : const LoginPage(),
    );
  }
}
