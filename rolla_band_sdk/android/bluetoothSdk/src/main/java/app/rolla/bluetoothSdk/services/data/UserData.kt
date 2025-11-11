package app.rolla.bluetoothSdk.services.data

import app.rolla.bluetoothSdk.services.commands.data.UserInfo

class UserData (
    val uuid: String,
    val userInfo: UserInfo,
    val error: String? = null
) {
    override fun toString(): String {
        return "UserData(uuid=$uuid, userInfo=$userInfo, error=$error)"
    }
}