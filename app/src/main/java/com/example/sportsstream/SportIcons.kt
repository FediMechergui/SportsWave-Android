package com.example.sportsstream

object SportIcons {
    fun resource(sport: String): Int = when(sport) {
        "football" -> R.drawable.ic_sports_soccer
        "basketball" -> R.drawable.ic_sports_basketball
        "tennis" -> R.drawable.ic_sports_tennis
        "motorsports" -> R.drawable.ic_sports_motorsports
        "american-football" -> R.drawable.ic_sports_football
        "boxing", "mma-events" -> R.drawable.ic_sports_mma
        "ice-hockey" -> R.drawable.ic_sports_hockey
        "rugby", "aussie-rules" -> R.drawable.ic_sports_rugby
        "baseball" -> R.drawable.ic_sports_baseball
        "cricket" -> R.drawable.ic_sports_cricket
        "golf" -> R.drawable.ic_sports_golf
        "darts" -> R.drawable.ic_sport_darts
        "snooker" -> R.drawable.ic_sport_billiards
        "volleyball" -> R.drawable.ic_sports_volleyball
        "handball" -> R.drawable.ic_sports_handball
        "horse-racing" -> R.drawable.ic_sport_racing_flag
        "cycling" -> R.drawable.ic_directions_bike
        else -> R.drawable.ic_sports
    }
}
