import com.fasterxml.jackson.annotation.JsonProperty
import org.flaviodiez.enums.Action

data class ServerResponse (
    @JsonProperty("action")
    val action: Action,

    @JsonProperty("participant")
    val participant: UpdateParticipant?,

    @JsonProperty("payload")
    val payload: Any,
)
