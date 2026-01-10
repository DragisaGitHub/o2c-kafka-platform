package rs.master.o2c.auth.service;

import rs.master.o2c.auth.service.model.PinChallenge;

public interface PinChallengeService {

    PinChallenge createChallenge(String username);
    PinChallenge consume(String challengeId);
}