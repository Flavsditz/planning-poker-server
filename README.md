# planning-poker

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.package.type=native
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.package.type=native -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/planning-poker-1.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/gradle-tooling.

## Related Guides

- WebSockets ([guide](https://quarkus.io/guides/websockets)): WebSocket communication channel support
- Kotlin ([guide](https://quarkus.io/guides/kotlin)): Write your services in Kotlin

## Provided Code

### Workflow
1. The user goes on his selected Front-end application 
2. There the user should have 2 options: "New Session" or "Join existing session" (or equivalent wording)

#### New Session
- The front-end application should call the PUT `/rooms` path REST endpoint in order to get a `roomId` back 
- This ID should be displayed for the user, so they can share it with others and the user should enter a `userName`.
    - As of this point others can join the room without any problems
- Upon confirming the `userName` the websocket endpoint should be called with `ws://[server-url:port]/room/{roomId}/user/{userName}` 


#### Join existing Session
- The front-end application should show a field allowing the user to enter the **5 character** `roomId` and their `userName`
- Upon confirming the `userName` the websocket endpoint should be called with `ws://[server-url:port]/room/{roomId}/user/{userName}`

### Workflow - continued
As of here all messages (exception below) use the websocket stream

3. The websocket endpoint called will add the user to the room with ID `roomId` and announce its entrance to the other already connected participant.
4. Now the user can **SEND** any of the following actions and which will generate others in return for all participants in order to allow to update the Frontend:
   1. `CAST_VOTE` - announce that you decided to vot. The payload should contain the vote itself
   2. `REMOVE_VOTE` - announce you are removing your vote. Only your vote will be removed
   3. `REVEAL_VOTES` - Tell to turn all the cards
   4. `CLEAR_VOTES` - Clear the whole table. 
   5. `SIT_OUT` - The user becomes an observer
   6. `SIT_IN` - The user that was previously an observer becomes an active player again
7. Upon one of the previous actions, all other users will then **RECEIVE** another action in response to update their UI. These can be:
    1. `VOTED` - This tells some user voted. It doesn't reveal the vote itself just that they voted and we can respond accordingly in the FrontEnd. The object contains: the action and the user who voted.
    2. `REMOVE_VOTE` - This tells some user removed their previous vote. The object contains: the action and the user who remove their vote.
    3. `REVEAL_VOTES` - This prompts the frontend to show a short animation and show the votes.The object will contain: the action and a payload with a list of the user IDs and their vote so the FE can match it properly.
    4. `CLEAR_VOTES` - This clear the whole table and the FE should make sure that the current user also has his selection cleared. The object contains: Just the action. 
    5. `SIT_OUT` - Tells that someone is now an observer and thus should be rendered so. The object contains: the action and the user.
    6. `SIT_IN` - Tells that an observer now "sat-in" and can vote. The object contains: the action and the user.
    7. `JOINED` - This happens when a new user joins the room. The object contains: the action and the user.
    8. `LEAVE` - This happens when someone disconnects. The object contains: the action and the user.
    9. `PARTICIPANTS_LIST` - When someone joins, they get a list of the already existing participants so they can create an appropriate list.
   10. `UPDATE_DECK` - This is sent to everyone when the deck changes and it should update the deck being displayed. The object contains: the action and the payload is a comma-separated list of the cards.
   11. `ERROR` - Response to a unrecognized action, or an action that shouldn't be sent to the server (only come from the server). The response object has the same structure just the payload contain the error message


The workflow works from sending and receiving these messages. One could enforce some rules in the front-end by not showing some options depending of the status (e.g. not showing the deck if I am an observer), but the server won't enforce a workflow. Just like in real-life people can be a bother and reveal cards before everyone votes but it can also work in favor if someone has to leave shortly to attend the door or so...

#### Deck Changes
Deck changes is still happening through a REST endpoint. The reasoning behind this is that if the FE wants they could implement this in the registration screen before the user connects through the Websocket channel.
The idea is to allow users to select any symbol they like (numbers, characters, emoji, etc) so the format of the deck is a list of strings like:
```python
["?", "1", "2", "3", "5", "8", "13", "100", "Pause"]
```
When there is a deck change the front-end should display the new deck. From the server a new deck will be sent as well as a `CLEAR_VOTES` action.

### The objects

#### Sending messages to the server
A `message` to the server contains just the action (string but should be upper-case since it is case-sensitive) and the payload (string, but can be an empty string):
```json
{
   "action": "CAST_VOTE",
   "payload": "2"
}
```

#### Responses from the Server
The `server response` is comprised of the action (string but should be upper-case since it is case-sensitive), the user (nullable since not all actions have a user) and a payload (string, but can be an empty string)

Here are a couple of examples:
```json
{
   "action": "JOINED",
   "participant": {
      "name": "FD",
      "id": 1600281574,
      "voted": false,
      "observer": false
   },
   "payload": ""
}
```
```json
{
   "action": "VOTED",
   "participant": {
      "name": "ReallyLongName",
      "id": 1904063193,
      "voted": true,
      "observer": false
   },
   "payload": ""
}
```
```json
{
    "action": "REVEAL_VOTES",
    "participant": null,
    "payload": {
        "-447263855": "5",
        "1600281574": "5",
        "1904063193": "2"
    }
}
```

## Observations

- The deck is a string since we are not adding anything, that means they can be anything: numbers, letters, emojis, etc.
This still allows us to do comparisons and see if people voted the same (create an alignment feature), and allow everyone to fit their own ways of working to the process.
- Some smart UI implementations can create checks such as having a deck with no repeats or something and they can be implemented in the future.
- Still need to implement a message for errors, such as someone sending the wrong action to the server for example or checking the vote is in the deck. 