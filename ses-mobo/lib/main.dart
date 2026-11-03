import 'package:flutter/material.dart';
import 'core/app_colors.dart';
import 'models/permit_header.dart';
import 'services/mock_permit_service.dart';
import 'pages/posko_home_page.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final service = MockPermitService();
  final permits = await service.fetchActivePermits();

  runApp(SesMoboApp(permits: permits));
}

class SesMoboApp extends StatelessWidget {
  final List<MaterialPermitHeader> permits;

  const SesMoboApp({super.key, required this.permits});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SES Security Suite Mobile - PT Badak NGL',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: AppColors.canvas,
        primaryColor: AppColors.navy,
        colorScheme: ColorScheme.fromSeed(
          seedColor: AppColors.navy,
          primary: AppColors.blue,
          secondary: AppColors.navyMid,
          surface: AppColors.surface,
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: AppColors.navy,
          foregroundColor: Colors.white,
          elevation: 2,
        ),
      ),
      home: PoskoHomePage(
        permits: permits,
        inspectorNip: 'SEC-88210',
      ),
    );
  }
}
