package org.flaviodiez.enums

enum class Action {
    CAST_VOTE,
    VOTED,
    REMOVE_VOTE,
    REVEAL_VOTES,
    CLEAR_VOTES,
    SIT_OUT, //participate only as observer
    SIT_IN,  //participate on the votes
    UPDATE_DECK, //someone changed the deck config
    JOINED,
    LEAVE,
    PARTICIPANTS_LIST,
    ERROR
}