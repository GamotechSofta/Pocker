package com.poker.tournament.service;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.poker.exception.ApiException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class TournamentTransactions {
    private static final TransactionOptions OPTIONS = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).build();
    private final MongoClient client;
    private final MongoTemplate mongo;

    public TournamentTransactions(MongoClient client, MongoTemplate mongo) {
        this.client = client;
        this.mongo = mongo;
    }

    public <T> T run(Function<MongoTemplate, T> action) {
        try (var session = client.startSession()) {
            return session.withTransaction(() -> {
                try {
                    return action.apply(mongo.withSession(session));
                } catch (DataAccessException error) {
                    for (Throwable cause = error.getCause(); cause != null; cause = cause.getCause()) {
                        if (cause instanceof MongoException mongoError) throw mongoError;
                    }
                    throw error;
                }
            }, OPTIONS);
        } catch (MongoCommandException error) {
            if (error.getErrorCode() == 20 || error.getErrorCode() == 263) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REGISTRATION_UNAVAILABLE",
                        "Tournament operations require a transaction-capable MongoDB deployment.");
            }
            throw error;
        }
    }
}
