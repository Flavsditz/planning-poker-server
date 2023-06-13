import com.fasterxml.jackson.annotation.JsonIgnore
import javax.websocket.Session

data class Participant(
    @JsonIgnore
    var session: Session,

    val name: String,
    val id: Int,
    var currentVote: String? = null,
    var observer: Boolean = false
) {
    fun toUpdateParticipant(): UpdateParticipant {
        return UpdateParticipant(name, id, currentVote != null, observer)
    }
}
