import com.fasterxml.jackson.annotation.JsonIgnore
import javax.websocket.Session

data class UpdateParticipant(
    val name: String,
    val id: Int,
    val voted: Boolean = false,
    val observer: Boolean = false
)
