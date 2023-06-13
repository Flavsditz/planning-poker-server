import com.fasterxml.jackson.databind.ObjectMapper
import org.flaviodiez.enums.Action.*
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import javax.websocket.OnClose
import javax.websocket.OnMessage
import javax.websocket.OnOpen
import javax.websocket.Session
import javax.websocket.server.PathParam
import javax.websocket.server.ServerEndpoint
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@ServerEndpoint("/room/{roomId}/user/{userName}")
@Path("/")
class PlanningPokerResource {

    // A map that stores the rooms by their name.
    private val rooms = mutableMapOf<String, Room>()

    private val objectMapper = ObjectMapper()

    @OnOpen
    fun onOpen(
        session: Session,
        @PathParam("roomId") roomId: String,
        @PathParam("userName") participantName: String
    ) {

        // Store the participant name and roomId in the session's user properties.
        session.userProperties["participantName"] = participantName
        session.userProperties["roomId"] = roomId

        val room = rooms[roomId]!!
        val roomParticipants = room.participants
        val participantId = generateUniqueIdForRoom(roomParticipants)

        session.userProperties["participantId"] = participantId

        // Store room session
        val newJoiner = Participant(session, participantName, participantId)
        roomParticipants.add(newJoiner)

        // Update user with current states (in case they joined late)
        val updateList = roomParticipants.filterNot { it.session == session }
            .map { UpdateParticipant(it.name, it.id, !it.currentVote.isNullOrBlank(), it.observer) }
        val listResponse = ServerResponse(PARTICIPANTS_LIST, null, updateList)
        session.asyncRemote.sendObject(listResponse.toJson())

        val deckResponse = ServerResponse(UPDATE_DECK, null, room.deck)
        session.asyncRemote.sendObject(deckResponse.toJson())

        // Tell others about the user joining
        val joinResponse = ServerResponse(JOINED, newJoiner.toUpdateParticipant(), "")
        roomParticipants.filterNot { it.session == session }.forEach {
            it.session.asyncRemote.sendObject(joinResponse.toJson())
        }
    }

    private fun generateUniqueIdForRoom(roomParticipants: MutableList<Participant>): Int {
        val ids = roomParticipants.map { it.id }
        val random = SecureRandom()

        var uniqueId = 0
        while (uniqueId == 0 || ids.contains(uniqueId)) {
            uniqueId = random.nextInt()
        }

        return uniqueId
    }

    @OnClose
    fun onClose(session: Session) {
        val roomName = session.userProperties["roomId"] as String
        val participantId = session.userProperties["participantId"] as Int

        val roomParticipants = rooms[roomName]!!.participants
        val participant = roomParticipants.find { p -> p.id == participantId }!!
        roomParticipants.remove(participant)

        //Tell others
        val leaveResponse = ServerResponse(LEAVE, participant.toUpdateParticipant(), "")
        roomParticipants.filterNot { it.session == session }.forEach {
            it.session.asyncRemote.sendObject(leaveResponse.toJson())
        }
    }

    //    @Scheduled(cron = "0 0 5-21 * * *")
    fun closeStaleRooms() {
        rooms.forEach {
            val room = it.value

            val currentTime = LocalDateTime.now()
            val timeAgo = currentTime.minus(Duration.ofHours(2L))

            // Remove room if empty and over the time limit
            if (room.lastUpdated.isBefore(timeAgo) && room.participants.size == 0) {
                rooms.remove(room.name)
            }
        }
    }

    @OnMessage
    fun onMessage(message: String, session: Session) {
        // Message Type to act accordingly
        val msg = getMessageObject(message)

        // Get the participant's details from the session's user properties.
        val participantId = session.userProperties["participantId"] as Int
        val roomName = session.userProperties["roomId"] as String

        //Todo error case?
        val participant = rooms[roomName]!!.participants.find { p -> p.id == participantId }!!

        // Send the message to all participants in the room.
        when (msg.action) {
            CAST_VOTE -> {
                //TODO: check if user's vote is valid, i.e. is it in the room's current deck

                //Update participant's vote
                participant.currentVote = msg.payload

                //Tell everyone user voted
                val resp = ServerResponse(VOTED, participant.toUpdateParticipant(), "")
                rooms[roomName]!!.participants.forEach { it.session.asyncRemote.sendObject(resp.toJson()) }
            }

            REMOVE_VOTE -> {
                //Update participant's vote
                participant.currentVote = null

                // Tell everyone user removed his vote
                val resp = ServerResponse(REMOVE_VOTE, participant.toUpdateParticipant(), "")
                rooms[roomName]!!.participants.forEach { it.session.asyncRemote.sendObject(resp.toJson()) }
            }

            REVEAL_VOTES -> {
                // Gather all votes
                val votesMap = rooms[roomName]!!.participants.associateBy({it.id}, {it.currentVote})

                // Tell everyone user's votes
                val resp = ServerResponse(REVEAL_VOTES, null, votesMap)
                rooms[roomName]!!.participants.forEach { it.session.asyncRemote.sendObject(resp.toJson()) }
            }

            CLEAR_VOTES -> {
                clearVotes(roomName)
            }

            SIT_OUT -> {
                //Update participant's state
                participant.observer = true

                // Tell everyone user is not participating
                val resp = ServerResponse(SIT_OUT, participant.toUpdateParticipant(), "")
                rooms[roomName]!!.participants.forEach { it.session.asyncRemote.sendObject(resp.toJson()) }
            }

            SIT_IN -> {
                //Update participant's state
                participant.observer = false

                // Tell everyone user is participating on the round
                val resp = ServerResponse(SIT_IN, participant.toUpdateParticipant(), "")
                rooms[roomName]!!.participants.forEach { it.session.asyncRemote.sendObject(resp.toJson()) }
            }

            // Actions that should not be sent TO the server, only FROM it.
            JOINED,
            LEAVE,
            PARTICIPANTS_LIST,
            VOTED,
            UPDATE_DECK,
            ERROR-> {
                val resp = ServerResponse(ERROR, participant.toUpdateParticipant(), "You sent an invalid action. Here is a list of valid actions: CAST_VOTE, REMOVE_VOTE, REVEAL_VOTES, CLEAR_VOTES, SIT_OUT, SIT_IN")
                participant.session.asyncRemote.sendObject(resp)
            }

            // Unrecognized action
            else ->{
                val resp = ServerResponse(ERROR, participant.toUpdateParticipant(), "You sent an unrecognized action. Here is a list of valid actions: CAST_VOTE, REMOVE_VOTE, REVEAL_VOTES, CLEAR_VOTES, SIT_OUT, SIT_IN")
                participant.session.asyncRemote.sendObject(resp)
            }
        }
    }

    private fun clearVotes(roomName: String) {
        // Clear all votes
        rooms[roomName]!!.participants.forEach { p -> p.currentVote = null }

        // Tell everyone user's votes
        val resp = ServerResponse(CLEAR_VOTES, null, "")
        rooms[roomName]!!.participants.forEach { it.session.asyncRemote.sendObject(resp.toJson()) }
    }

    fun getMessageObject(messageString: String): Message {
        val objectMapper = ObjectMapper()

        return objectMapper.readValue(messageString, Message::class.java)
    }

    fun ServerResponse.toJson(): String {
        return objectMapper.writeValueAsString(this)
    }

    @PUT
    @Path("/rooms")
    fun createRoom(): Response? {
        // Create a new room and store it in the map of rooms.
        var roomKey = generateRandomKey()

        while (rooms.contains(roomKey)) {
            roomKey = generateRandomKey()
        }

        rooms.put(roomKey, Room(roomKey))

        return Response.ok(roomKey).build()
    }

    private fun generateRandomKey(): String {
        val keyLength = 5
        val random = SecureRandom()
        val characters = ('A'..'Z') + ('0'..'9')
        return (1..keyLength)
            .map { random.nextInt(characters.size) }
            .map(characters::get)
            .joinToString("")
    }

    @GET
    @Path("/rooms")
    @Produces(MediaType.APPLICATION_JSON)
    fun getRooms(): Response? {
        // TODO: This should be deleted for prod version
        // Create a new room and store it in the map of rooms.
        return Response.ok(rooms).build()
    }

    @POST
    @Path("/rooms/{roomName}/deck")
    @Consumes(MediaType.APPLICATION_JSON)
    fun changeDeck(@PathParam("roomName") roomName: String, newDeck: List<String>) {
        //Update room's deck
        rooms[roomName]!!.deck.clear()
        rooms[roomName]!!.deck.addAll(newDeck)

        // Tell everyone deck changed
        val resp = ServerResponse(UPDATE_DECK, null, newDeck)
        rooms[roomName]!!.participants.forEach { it.session.asyncRemote.sendObject(resp.toJson()) }

        // Clear current votes
        clearVotes(roomName)
    }
}
