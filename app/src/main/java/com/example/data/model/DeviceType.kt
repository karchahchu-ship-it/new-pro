package com.example.data.model

enum class DeviceType(
    val title: String,
    val iconEmoji: String,
    val description: String
) {
    MODULE("Module", "🔌", "Module connecté"),
    CHAUDIERE("Chaudière", "🔥", "Système de chauffage / chaudière"),
    AUTOMATISME("Automatisme", "⚙️", "Automate / Relais programmable"),
    TEMPERATURE("Température", "🌡️", "Sonde de température"),
    HUMIDITE("Humidité", "💧", "Capteur d'humidité"),
    ECLAIRAGE("Éclairage", "💡", "Contrôleur d'éclairage / LED"),
    VENTILATION("Ventilation", "🌀", "Extracteur / Ventilation"),
    POMPE("Pompe", "🚰", "Contrôleur de pompe"),
    INDUSTRIE("Industrie", "🏭", "Équipement industriel"),
    WEB_SERVER("Serveur Web", "🌐", "Serveur Web embarqué");

    companion object {
        fun fromName(name: String?): DeviceType {
            if (name.equals("ESP32", ignoreCase = true)) return MODULE
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MODULE
        }
    }
}
