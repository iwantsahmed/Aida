package com.rayluxeo.aida.domain.salah

import java.time.ZoneId

/**
 * A built-in city or district for offline Salah calculation without requiring GPS or location tracking.
 * Covers all 64 districts of Bangladesh and major international/Islamic cities.
 */
data class PrayerCity(
    val id: String,
    val englishName: String,
    val bengaliName: String,
    val regionOrCountry: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: ZoneId,
    val isPopular: Boolean = false,
) {
    fun toPrayerLocation(useBengaliLabel: Boolean = false): PrayerLocation = PrayerLocation(
        latitude = latitude,
        longitude = longitude,
        cityLabel = if (useBengaliLabel) bengaliName else englishName,
        zoneId = zoneId,
    )
}

object BuiltInCities {

    private val DHAKA_ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

    val POPULAR: List<PrayerCity> by lazy {
        ALL.filter { it.isPopular }
    }

    val BANGLADESH_DISTRICTS: List<PrayerCity> by lazy {
        ALL.filter { it.regionOrCountry.contains("Bangladesh") }
    }

    val INTERNATIONAL: List<PrayerCity> by lazy {
        ALL.filter { !it.regionOrCountry.contains("Bangladesh") }
    }

    fun find(query: String): List<PrayerCity> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return ALL
        return ALL.filter { city ->
            city.englishName.lowercase().contains(q) ||
                city.bengaliName.contains(q) ||
                city.regionOrCountry.lowercase().contains(q) ||
                city.id.lowercase().contains(q)
        }
    }

    fun findById(id: String): PrayerCity? =
        ALL.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun findByName(name: String): PrayerCity? {
        val q = name.trim().lowercase()
        return ALL.firstOrNull {
            it.englishName.lowercase() == q ||
                it.bengaliName == name.trim() ||
                it.id.lowercase() == q
        } ?: ALL.firstOrNull {
            it.englishName.lowercase().contains(q) || it.bengaliName.contains(name.trim())
        }
    }

    val DEFAULT: PrayerCity by lazy {
        ALL.first { it.id == "dhaka" }
    }

    val ALL: List<PrayerCity> = listOf(
        // ==================== BANGLADESH (Dhaka Division) ====================
        PrayerCity("dhaka", "Dhaka", "ঢাকা", "Dhaka, Bangladesh", 23.8103, 90.4125, DHAKA_ZONE, isPopular = true),
        PrayerCity("gazipur", "Gazipur", "গাজীপুর", "Dhaka, Bangladesh", 23.9999, 90.4203, DHAKA_ZONE),
        PrayerCity("narayanganj", "Narayanganj", "নারায়ণগঞ্জ", "Dhaka, Bangladesh", 23.6238, 90.5000, DHAKA_ZONE),
        PrayerCity("tangail", "Tangail", "টাঙ্গাইল", "Dhaka, Bangladesh", 24.2513, 89.9167, DHAKA_ZONE),
        PrayerCity("narsingdi", "Narsingdi", "নরসিংদী", "Dhaka, Bangladesh", 23.9322, 90.7154, DHAKA_ZONE),
        PrayerCity("kishoreganj", "Kishoreganj", "কিশোরগঞ্জ", "Dhaka, Bangladesh", 24.4449, 90.7766, DHAKA_ZONE),
        PrayerCity("manikganj", "Manikganj", "মানিকগঞ্জ", "Dhaka, Bangladesh", 23.8617, 90.0003, DHAKA_ZONE),
        PrayerCity("munshiganj", "Munshiganj", "মুন্সীগঞ্জ", "Dhaka, Bangladesh", 23.5422, 90.5305, DHAKA_ZONE),
        PrayerCity("rajbari", "Rajbari", "রাজবাড়ী", "Dhaka, Bangladesh", 23.7574, 89.6445, DHAKA_ZONE),
        PrayerCity("faridpur", "Faridpur", "ফরিদপুর", "Dhaka, Bangladesh", 23.6071, 89.8429, DHAKA_ZONE),
        PrayerCity("gopalganj", "Gopalganj", "গোপালগঞ্জ", "Dhaka, Bangladesh", 23.0051, 89.8266, DHAKA_ZONE),
        PrayerCity("madaripur", "Madaripur", "মাদারীপুর", "Dhaka, Bangladesh", 23.1641, 90.1897, DHAKA_ZONE),
        PrayerCity("shariatpur", "Shariatpur", "শরীয়তপুর", "Dhaka, Bangladesh", 23.2423, 90.4348, DHAKA_ZONE),

        // ==================== BANGLADESH (Chattogram Division) ====================
        PrayerCity("chattogram", "Chattogram", "চট্টগ্রাম", "Chattogram, Bangladesh", 22.3569, 91.7832, DHAKA_ZONE, isPopular = true),
        PrayerCity("coxs_bazar", "Cox's Bazar", "কক্সবাজার", "Chattogram, Bangladesh", 21.4272, 92.0058, DHAKA_ZONE),
        PrayerCity("cumilla", "Cumilla", "কুমিল্লা", "Chattogram, Bangladesh", 23.4682, 91.1788, DHAKA_ZONE, isPopular = true),
        PrayerCity("feni", "Feni", "ফেনী", "Chattogram, Bangladesh", 23.0159, 91.3976, DHAKA_ZONE),
        PrayerCity("brahmanbaria", "Brahmanbaria", "ব্রাহ্মণবাড়িয়া", "Chattogram, Bangladesh", 23.9571, 91.1119, DHAKA_ZONE),
        PrayerCity("rangamati", "Rangamati", "রাঙ্গামাটি", "Chattogram, Bangladesh", 22.7324, 92.2985, DHAKA_ZONE),
        PrayerCity("noakhali", "Noakhali", "নোয়াখালী", "Chattogram, Bangladesh", 22.8696, 91.0994, DHAKA_ZONE),
        PrayerCity("chandpur", "Chandpur", "চাঁদপুর", "Chattogram, Bangladesh", 23.2333, 90.6667, DHAKA_ZONE),
        PrayerCity("lakshmipur", "Lakshmipur", "লক্ষ্মীপুর", "Chattogram, Bangladesh", 22.9425, 90.8412, DHAKA_ZONE),
        PrayerCity("khagrachhari", "Khagrachhari", "খাগড়াছড়ি", "Chattogram, Bangladesh", 23.1193, 91.9847, DHAKA_ZONE),
        PrayerCity("bandarban", "Bandarban", "বান্দরবান", "Chattogram, Bangladesh", 22.1953, 92.2184, DHAKA_ZONE),

        // ==================== BANGLADESH (Sylhet Division) ====================
        PrayerCity("sylhet", "Sylhet", "সিলেট", "Sylhet, Bangladesh", 24.8949, 91.8687, DHAKA_ZONE, isPopular = true),
        PrayerCity("moulvibazar", "Moulvibazar", "মৌলভীবাজার", "Sylhet, Bangladesh", 24.4829, 91.7774, DHAKA_ZONE),
        PrayerCity("habiganj", "Habiganj", "হবিগঞ্জ", "Sylhet, Bangladesh", 24.3750, 91.4167, DHAKA_ZONE),
        PrayerCity("sunamganj", "Sunamganj", "সুনামগঞ্জ", "Sylhet, Bangladesh", 25.0658, 91.3950, DHAKA_ZONE),

        // ==================== BANGLADESH (Rajshahi Division) ====================
        PrayerCity("rajshahi", "Rajshahi", "রাজশাহী", "Rajshahi, Bangladesh", 24.3636, 88.6241, DHAKA_ZONE, isPopular = true),
        PrayerCity("bogura", "Bogura", "বগুড়া", "Rajshahi, Bangladesh", 24.8465, 89.3777, DHAKA_ZONE, isPopular = true),
        PrayerCity("pabna", "Pabna", "পাবনা", "Rajshahi, Bangladesh", 24.0064, 89.2372, DHAKA_ZONE),
        PrayerCity("sirajganj", "Sirajganj", "সিরাজগঞ্জ", "Rajshahi, Bangladesh", 24.4534, 89.7008, DHAKA_ZONE),
        PrayerCity("naogaon", "Naogaon", "নওগাঁ", "Rajshahi, Bangladesh", 24.7936, 88.9318, DHAKA_ZONE),
        PrayerCity("natore", "Natore", "নাটোর", "Rajshahi, Bangladesh", 24.4206, 88.9324, DHAKA_ZONE),
        PrayerCity("chapainawabganj", "Chapainawabganj", "চাঁপাইনবাবগঞ্জ", "Rajshahi, Bangladesh", 24.5965, 88.2775, DHAKA_ZONE),
        PrayerCity("joypurhat", "Joypurhat", "জয়পুরহাট", "Rajshahi, Bangladesh", 25.1015, 89.0277, DHAKA_ZONE),

        // ==================== BANGLADESH (Khulna Division) ====================
        PrayerCity("khulna", "Khulna", "খুলনা", "Khulna, Bangladesh", 22.8456, 89.5403, DHAKA_ZONE, isPopular = true),
        PrayerCity("jashore", "Jashore", "যশোর", "Khulna, Bangladesh", 23.1664, 89.2081, DHAKA_ZONE),
        PrayerCity("kushtia", "Kushtia", "কুষ্টিয়া", "Khulna, Bangladesh", 23.9013, 89.1205, DHAKA_ZONE),
        PrayerCity("jhenaidah", "Jhenaidah", "ঝিনাইদহ", "Khulna, Bangladesh", 23.5448, 89.1539, DHAKA_ZONE),
        PrayerCity("satkhira", "Satkhira", "সাতক্ষীরা", "Khulna, Bangladesh", 22.7185, 89.0705, DHAKA_ZONE),
        PrayerCity("bagerhat", "Bagerhat", "বাগেরহাট", "Khulna, Bangladesh", 22.6516, 89.7859, DHAKA_ZONE),
        PrayerCity("chuadanga", "Chuadanga", "চুয়াডাঙ্গা", "Khulna, Bangladesh", 23.6402, 88.8418, DHAKA_ZONE),
        PrayerCity("meherpur", "Meherpur", "মেহেরপুর", "Khulna, Bangladesh", 23.7747, 88.6318, DHAKA_ZONE),
        PrayerCity("magura", "Magura", "মাগুরা", "Khulna, Bangladesh", 23.4873, 89.4199, DHAKA_ZONE),
        PrayerCity("narail", "Narail", "নড়াইল", "Khulna, Bangladesh", 23.1725, 89.5127, DHAKA_ZONE),

        // ==================== BANGLADESH (Barishal Division) ====================
        PrayerCity("barishal", "Barishal", "বরিশাল", "Barishal, Bangladesh", 22.7010, 90.3535, DHAKA_ZONE, isPopular = true),
        PrayerCity("patuakhali", "Patuakhali", "পটুয়াখালী", "Barishal, Bangladesh", 22.3596, 90.3299, DHAKA_ZONE),
        PrayerCity("bhola", "Bhola", "ভোলা", "Barishal, Bangladesh", 22.6859, 90.6482, DHAKA_ZONE),
        PrayerCity("pirojpur", "Pirojpur", "পিরোজপুর", "Barishal, Bangladesh", 22.5841, 89.9720, DHAKA_ZONE),
        PrayerCity("barguna", "Barguna", "বরগুনা", "Barishal, Bangladesh", 22.0953, 90.1121, DHAKA_ZONE),
        PrayerCity("jhalokati", "Jhalokati", "ঝালকাঠি", "Barishal, Bangladesh", 22.6406, 90.1987, DHAKA_ZONE),

        // ==================== BANGLADESH (Rangpur Division) ====================
        PrayerCity("rangpur", "Rangpur", "রংপুর", "Rangpur, Bangladesh", 25.7439, 89.2752, DHAKA_ZONE, isPopular = true),
        PrayerCity("dinajpur", "Dinajpur", "দিনাজপুর", "Rangpur, Bangladesh", 25.6217, 88.6355, DHAKA_ZONE),
        PrayerCity("gaibandha", "Gaibandha", "গাইবান্ধা", "Rangpur, Bangladesh", 25.3288, 89.5430, DHAKA_ZONE),
        PrayerCity("kurigram", "Kurigram", "কুড়িগ্রাম", "Rangpur, Bangladesh", 25.8054, 89.6362, DHAKA_ZONE),
        PrayerCity("lalmonirhat", "Lalmonirhat", "লালমনিরহাট", "Rangpur, Bangladesh", 25.9923, 89.2847, DHAKA_ZONE),
        PrayerCity("nilphamari", "Nilphamari", "নীলফামারী", "Rangpur, Bangladesh", 25.9318, 88.8560, DHAKA_ZONE),
        PrayerCity("panchagarh", "Panchagarh", "পঞ্চগড়", "Rangpur, Bangladesh", 26.3411, 88.5542, DHAKA_ZONE),
        PrayerCity("thakurgaon", "Thakurgaon", "ঠাকুরগাঁও", "Rangpur, Bangladesh", 26.0337, 88.4617, DHAKA_ZONE),

        // ==================== BANGLADESH (Mymensingh Division) ====================
        PrayerCity("mymensingh", "Mymensingh", "ময়মনসিংহ", "Mymensingh, Bangladesh", 24.7471, 90.4203, DHAKA_ZONE, isPopular = true),
        PrayerCity("jamalpur", "Jamalpur", "জামালপুর", "Mymensingh, Bangladesh", 24.9375, 89.9378, DHAKA_ZONE),
        PrayerCity("netrokona", "Netrokona", "নেত্রকোণা", "Mymensingh, Bangladesh", 24.8709, 90.7279, DHAKA_ZONE),
        PrayerCity("sherpur", "Sherpur", "শেরপুর", "Mymensingh, Bangladesh", 25.0205, 90.0153, DHAKA_ZONE),

        // ==================== HOLY CITIES ====================
        PrayerCity("makkah", "Makkah", "মক্কা", "Saudi Arabia", 21.3891, 39.8579, ZoneId.of("Asia/Riyadh"), isPopular = true),
        PrayerCity("madinah", "Madinah", "মদিনা", "Saudi Arabia", 24.5247, 39.5692, ZoneId.of("Asia/Riyadh"), isPopular = true),
        PrayerCity("jerusalem", "Jerusalem (Al-Quds)", "আল-কুদস (জেরুজালেম)", "Palestine", 31.7683, 35.2137, ZoneId.of("Asia/Jerusalem")),

        // ==================== MIDDLE EAST & SOUTH ASIA ====================
        PrayerCity("dubai", "Dubai", "দুবাই", "United Arab Emirates", 25.2048, 55.2708, ZoneId.of("Asia/Dubai"), isPopular = true),
        PrayerCity("abu_dhabi", "Abu Dhabi", "আবুধাবি", "United Arab Emirates", 24.4539, 54.3773, ZoneId.of("Asia/Dubai")),
        PrayerCity("riyadh", "Riyadh", "রিয়াদ", "Saudi Arabia", 24.7136, 46.6753, ZoneId.of("Asia/Riyadh")),
        PrayerCity("jeddah", "Jeddah", "জেদ্দা", "Saudi Arabia", 21.4858, 39.1925, ZoneId.of("Asia/Riyadh")),
        PrayerCity("doha", "Doha", "দোহা", "Qatar", 25.2854, 51.5310, ZoneId.of("Asia/Qatar")),
        PrayerCity("kuwait", "Kuwait City", "কুয়েত সিটি", "Kuwait", 29.3759, 47.9774, ZoneId.of("Asia/Kuwait")),
        PrayerCity("muscat", "Muscat", "মাস্কাট", "Oman", 23.5880, 58.3829, ZoneId.of("Asia/Muscat")),
        PrayerCity("istanbul", "Istanbul", "ইস্তাম্বুল", "Turkey", 41.0082, 28.9784, ZoneId.of("Europe/Istanbul")),
        PrayerCity("cairo", "Cairo", "কায়রো", "Egypt", 30.0444, 31.2357, ZoneId.of("Africa/Cairo")),
        PrayerCity("karachi", "Karachi", "করাচি", "Pakistan", 24.8607, 67.0011, ZoneId.of("Asia/Karachi")),
        PrayerCity("lahore", "Lahore", "লাহোর", "Pakistan", 31.5204, 74.3587, ZoneId.of("Asia/Karachi")),
        PrayerCity("islamabad", "Islamabad", "ইসলামাবাদ", "Pakistan", 33.6844, 73.0479, ZoneId.of("Asia/Karachi")),
        PrayerCity("delhi", "Delhi", "দিল্লি", "India", 28.6139, 77.2090, ZoneId.of("Asia/Kolkata")),
        PrayerCity("kolkata", "Kolkata", "কলকাতা", "India", 22.5726, 88.3639, ZoneId.of("Asia/Kolkata")),
        PrayerCity("mumbai", "Mumbai", "মুম্বাই", "India", 19.0760, 72.8777, ZoneId.of("Asia/Kolkata")),
        PrayerCity("kuala_lumpur", "Kuala Lumpur", "কুয়ালালামপুর", "Malaysia", 3.1390, 101.6869, ZoneId.of("Asia/Kuala_Lumpur")),
        PrayerCity("jakarta", "Jakarta", "জাকার্তা", "Indonesia", -6.2088, 106.8456, ZoneId.of("Asia/Jakarta")),
        PrayerCity("singapore", "Singapore", "সিঙ্গাপুর", "Singapore", 1.3521, 103.8198, ZoneId.of("Asia/Singapore")),

        // ==================== EUROPE, NORTH AMERICA & OCEANIA ====================
        PrayerCity("london", "London", "লন্ডন", "United Kingdom", 51.5074, -0.1278, ZoneId.of("Europe/London")),
        PrayerCity("new_york", "New York", "নিউ ইয়র্ক", "United States", 40.7128, -74.0060, ZoneId.of("America/New_York")),
        PrayerCity("toronto", "Toronto", "টরন্টো", "Canada", 43.6532, -79.3832, ZoneId.of("America/Toronto")),
        PrayerCity("sydney", "Sydney", "সিডনি", "Australia", -33.8688, 151.2093, ZoneId.of("Australia/Sydney")),
        PrayerCity("tokyo", "Tokyo", "টোকিও", "Japan", 35.6762, 139.6503, ZoneId.of("Asia/Tokyo")),
    )
}
