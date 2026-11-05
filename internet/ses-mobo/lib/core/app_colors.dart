import 'package:flutter/material.dart';

/// Palet warna resmi Security Suite SES & Gate Security Mobile — PT Badak NGL
/// Selaras 100% dengan SES Web Design System (theme-ses-modern.css & tailwind.config.js)
/// Bernuansa Mono Blue sebagai warna utama (Primary).
class AppColors {
  AppColors._();

  // ─── SES Brand Mono Blue (Corporate Badak NGL) ───────────────────────────
  /// SES Modern Brand Mono Blue (--brand: #0064d9)
  static const Color brand = Color(0xFF0064D9);
  static const Color brandHover = Color(0xFF0058C0);
  /// Corporate Badak Core Blue (primary.blue: #0054a6)
  static const Color primaryBlue = Color(0xFF0054A6);
  /// Primary CTA alias
  static const Color blue = Color(0xFF0064D9);
  static const Color blueDark = Color(0xFF003F7D);
  static const Color blueActive = Color(0xFF00274D);

  /// Brand soft tints for pill cards, tags, and subtle button highlights
  static const Color brandSoft = Color(0xFFEBF4FF);      // --brand-soft: #ebf4ff
  static const Color brandSel = Color(0xFFE8F2FF);       // --sel: #e8f2ff
  static const Color blueBg = Color(0xFFEBF4FF);         // Alias for soft tint
  static const Color blueSubtleBg = Color(0xFFEBF4FF);
  static const Color blueSubtleBorder = Color(0xFFB9D4EF);

  // ─── SES Corporate Shell Navy ────────────────────────────────────────────
  /// Deep Corporate Shell (--shell: #1d2d3e)
  static const Color shell = Color(0xFF1D2D3E);
  static const Color shellDark = Color(0xFF001024);
  static const Color shellMid = Color(0xFF00274D);
  static const Color shellMuted = Color(0xFFA9B4BE);
  static const Color navy = Color(0xFF1D2D3E);
  static const Color navyDark = Color(0xFF001024);
  static const Color navyMid = Color(0xFF00274D);

  // ─── Accent / High-Tech ──────────────────────────────────────────────────
  /// Cyan Accent for scanner laser, badge highlights (--accent.cyan: #00e5ff)
  static const Color cyan = Color(0xFF00E5FF);
  static const Color indigo = Color(0xFF5856D6);
  static const Color indigoBg = Color(0x1F5856D6);

  // ─── Semantic Colors (SES Standard) ──────────────────────────────────────
  /// OK / Success (--ok: #256f3a, --ok-bg: #f5fae5)
  static const Color ok = Color(0xFF256F3A);
  static const Color okBg = Color(0xFFF5FAE5);
  static const Color green = Color(0xFF256F3A);
  static const Color greenBg = Color(0xFFF5FAE5);
  static const Color success = Color(0xFF256F3A);
  static const Color successBg = Color(0xFFF5FAE5);

  /// Warning (--warn: #a93e00, --warn-bg: #fff8d6)
  static const Color warn = Color(0xFFA93E00);
  static const Color warnBg = Color(0xFFFFF8D6);
  static const Color orange = Color(0xFFA93E00);
  static const Color orangeBg = Color(0xFFFFF8D6);
  static const Color warning = Color(0xFFA93E00);
  static const Color warningBg = Color(0xFFFFF8D6);

  /// Danger / Error (--err: #aa0808, --err-bg: #ffeaf4)
  static const Color err = Color(0xFFAA0808);
  static const Color errBg = Color(0xFFFFEAF4);
  static const Color red = Color(0xFFAA0808);
  static const Color redBg = Color(0xFFFFEAF4);
  static const Color danger = Color(0xFFAA0808);
  static const Color dangerBg = Color(0xFFFFEAF4);

  /// Info
  static const Color info = Color(0xFF0064D9);
  static const Color infoDark = Color(0xFF003F7D);
  static const Color infoBg = Color(0xFFE1F4FF);

  // ─── Surfaces & Backgrounds ──────────────────────────────────────────────
  /// Clean Soft Slate Canvas (--bg: #f5f6f7)
  static const Color bg = Color(0xFFF5F6F7);
  static const Color canvas = Color(0xFFF5F6F7);
  static const Color card = Colors.white;
  static const Color surface = Colors.white;
  static const Color bar = Color(0xFFFFFFFF);

  // ─── Borders, Dividers & Lines ───────────────────────────────────────────
  /// Crisp Border Line (--line: #d9dee3)
  static const Color line = Color(0xFFD9DEE3);
  static const Color lineSoft = Color(0xFFEAECEE);
  static const Color sep = Color(0xFFD9DEE3);
  static const Color border = Color(0xFFD9DEE3);
  static const Color divider = Color(0xFFD9DEE3);
  static const Color fill = Color(0x1F556B82);
  static const Color fill2 = Color(0x33556B82);

  // ─── Typography (High Contrast Mono Blue / Slate) ─────────────────────────
  /// Primary Text (--fg: #1d2d3e)
  static const Color textPrimary = Color(0xFF1D2D3E);
  /// Muted Slate Secondary Text (--muted: #556b82)
  static const Color textSecondary = Color(0xFF556B82);
  static const Color textMuted = Color(0xFF556B82);
  static const Color label = Color(0xFF1D2D3E);
  static const Color label2 = Color(0xFF556B82);
  static const Color label3 = Color(0xFFA9B4BE);
}
