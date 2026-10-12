package com.kevin.hrtracker.ui.scan

/** Display name of the connected device: saved name, else scan/pending name, else null (status only). */
internal fun resolveDeviceName(
    address: String?,
    savedNames: Map<String, String?>,
    scannedNames: Map<String, String?>,
): String? {
    if (address == null) return null
    return savedNames[address]?.takeIf { it.isNotBlank() }
        ?: scannedNames[address]?.takeIf { it.isNotBlank() }
}
