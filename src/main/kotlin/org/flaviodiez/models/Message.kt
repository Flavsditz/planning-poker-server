import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.flaviodiez.enums.Action
import java.time.LocalDateTime

data class Message (
    @JsonProperty("action")
    val action: Action,

    @JsonProperty("payload")
    val payload: String,
)
