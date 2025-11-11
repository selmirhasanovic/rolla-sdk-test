package app.rolla.bluetoothSdk.services.exceptions

class InvalidUUIDException(message: String) : Exception(
    message,
    Throwable(),
    false,
    true
)