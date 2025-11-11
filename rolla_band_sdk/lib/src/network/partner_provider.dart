class PartnerProvider {
  String _partnerId;

  PartnerProvider({required String initialPartnerId}) : _partnerId = initialPartnerId;

  String get partnerId => _partnerId;

  void updatePartnerId(String partnerId) {
    _partnerId = partnerId;
  }
}

