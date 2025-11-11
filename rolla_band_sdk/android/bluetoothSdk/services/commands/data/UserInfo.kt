package app.rolla.bluetoothSdk.services.commands.data

data class UserInfo (
    val gender: Int,
    val age: Int,
    val height: Int,
    val weight: Float,
    val stride: Int,
    val userDeviceId: String
) {
    override fun toString(): String {
        return "UserInfo(gender=$gender, age=$age, height=$height, weight=$weight, stride=$stride, userDeviceId=$userDeviceId)"
    }
}