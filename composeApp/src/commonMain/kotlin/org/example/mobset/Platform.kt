package org.example.mobset

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform