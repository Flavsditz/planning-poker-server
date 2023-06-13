import java.time.LocalDateTime

data class Room(
    val name: String,
    val deck: MutableList<String> = mutableListOf("1","2","3","5","8","13","21","?","BRK"),
    val participants: MutableList<Participant> = mutableListOf(),
    val lastUpdated: LocalDateTime = LocalDateTime.now(),
)
