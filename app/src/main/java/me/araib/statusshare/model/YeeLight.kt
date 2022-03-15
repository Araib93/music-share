package me.araib.statusshare.model

data class YeeLight(val location: String?) {
    val info = YeeLightInfo(location?.split("//")?.get(1))

    data class YeeLightInfo(val ipInfo: String?) {
        val ip = ipInfo?.split(":")?.get(0)
        val port = ipInfo?.split(":")?.get(1)
    }
}