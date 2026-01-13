package com.app.sample.extra


object ApiConstant {
    const val PAID = "paid"
    val CURRENT_PLATFORM = Platform.ANDROID
    const val DRM_TYPE = "widevine"
   // const val TOKEN = "ZXlKaGJHY2lPaUpJVXpJMU5pSXNJblI1Y0NJNklrcFhWQ0o5LmV5SmhkWFJvYjNKcGVtVmtJanAwY25WbExDSjFjMlZ5Ym1GdFpTSTZJbVJsWm1GMWJIUmZZV1J0YVc0aUxDSjBiMnRsYmlJNklqVm1aREZqWmpKbVl6bG1ORFVpTENKaGNIQmZhV1FpT2pjeE55d2liM2R1WlhKZmFXUWlPalUzTUN3aVlYQndYMjVoYldVaU9pSmtaV1poZFd4MFgyRmtiV2x1SWl3aVpYaHdJam94TnpRd01EWXdPVGt3ZlEuUjllX0wwSFItVG5HZ2NQZkp3MnRSelRnRUtDQ2hFdmt4NU9aVVJuaVBEbw=="
    const val TOKEN = "ZXlKaGJHY2lPaUpJVXpJMU5pSXNJblI1Y0NJNklrcFhWQ0o5LmV5SmhkWFJvYjNKcGVtVmtJanAwY25WbExDSjFjMlZ5Ym1GdFpTSTZJbVJsWm1GMWJIUmZZV1J0YVc0aUxDSjBiMnRsYmlJNklqVm1aREZqWmpKbVl6bG1ORFVpTENKaGNIQmZhV1FpT2pjeE55d2liM2R1WlhKZmFXUWlPalUzTUN3aVlYQndYMjVoYldVaU9pSmtaV1poZFd4MFgyRmtiV2x1SWl3aVpYaHdJam94TnpRME5UVXdOVGM1ZlEuR2RsRExTRlpWUV8yc2dpYXd3UGRTWURaTDdNbng5UzRqa183YWQ4aDNzRQ=="


}

enum class ApiPathKeys(val value: String) {
    DEVICE("device"),
    CURRENT_OFFSET("current_offset"),
    MAX_COUNTER("max_counter"),
    GENRE_ID("genre_id"),
    SEASON_ID("season_id"),
    CID("cid"),
    ENCRYPTION("enc"),
}

enum class Platform(val value: String) {
    ANDROID("android")
}

enum class Encryption(val value: String) {
    TRUE("true"),
    FALSE("false")
}
