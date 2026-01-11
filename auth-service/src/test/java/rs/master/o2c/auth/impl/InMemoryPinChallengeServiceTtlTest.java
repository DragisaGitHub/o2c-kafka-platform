package rs.master.o2c.auth.impl;

import org.junit.jupiter.api.Test;
import rs.master.o2c.auth.config.AuthMfaProperties;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPinChallengeServiceTtlTest {

    @Test
    void expiredChallengeIsRejected() throws Exception {
        InMemoryPinChallengeService svc = new InMemoryPinChallengeService(new AuthMfaProperties(1));

        var ch = svc.createChallenge("alice");
        Thread.sleep(1200);

        var consumed = svc.consume(ch.challengeId());
        assertThat(consumed).isNull();
    }
}
