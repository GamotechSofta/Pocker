package com.poker;

import com.poker.support.AbstractMongoTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PokerBackendApplicationTests extends AbstractMongoTest {

    @Test
    void contextLoads() {
    }
}
