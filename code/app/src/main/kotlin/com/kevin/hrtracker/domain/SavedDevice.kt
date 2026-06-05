package com.kevin.hrtracker.domain

import kotlinx.serialization.Serializable

@Serializable
data class SavedDevice(val address: String, val name: String)
