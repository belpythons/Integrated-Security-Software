import 'package:flutter_test/flutter_test.dart';
import 'package:ses_masuk_barang/main.dart';
import 'package:ses_masuk_barang/services/mock_permit_service.dart';

void main() {
  testWidgets('SesMoboApp main entrypoint smoke test', (WidgetTester tester) async {
    final service = MockPermitService();
    final permits = await service.fetchActivePermits();

    await tester.pumpWidget(SesMoboApp(permits: permits));
    await tester.pumpAndSettle();

    expect(find.text('SES Security Suite Mobile'), findsOneWidget);
  });
}
