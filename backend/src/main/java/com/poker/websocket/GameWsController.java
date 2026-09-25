package com.poker.websocket;

import com.poker.dto.GameActionRequest;
import com.poker.exception.ApiException;
import com.poker.exception.ErrorCodes;
import com.poker.game.engine.GameActionException;
import com.poker.service.TableService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
public class GameWsController {

    private final TableService tableService;
    private final GameBroadcaster broadcaster;

    public GameWsController(TableService tableService, GameBroadcaster broadcaster) {
        this.tableService = tableService;
        this.broadcaster = broadcaster;
    }

    @MessageMapping("/game/{gameId}/action")
    public void action(
            @DestinationVariable UUID gameId,
            @Valid GameActionRequest request,
            Principal principal
    ) {
        UUID userId = UUID.fromString(principal.getName());
        try {
            tableService.applyAction(gameId, userId, request);
        } catch (ApiException ex) {
            broadcaster.sendError(gameId, userId, ex.getCode(), ex.getMessage());
        } catch (GameActionException ex) {
            broadcaster.sendError(gameId, userId, ex.getCode(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            broadcaster.sendError(gameId, userId, ErrorCodes.INVALID_GAME_ACTION, ex.getMessage());
        }
    }

    @MessageExceptionHandler
    public void handle(Exception ex, Principal principal) {
        if (principal == null) {
            return;
        }
        UUID userId = UUID.fromString(principal.getName());
        String code = ex instanceof ApiException api ? api.getCode() : ErrorCodes.INVALID_GAME_ACTION;
        String message = ex.getMessage() == null ? "Invalid game action." : ex.getMessage();
        broadcaster.sendError(null, userId, code, message);
    }
}
