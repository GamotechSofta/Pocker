package com.poker.controller;

import com.poker.exception.ApiException;
import com.poker.tournament.dto.TournamentRegistration;
import com.poker.tournament.dto.TournamentAssignment;
import com.poker.tournament.service.TournamentRegistrationService;
import com.poker.tournament.service.TournamentAssignmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentRegistrationController {
    private final TournamentRegistrationService registration;
    private final TournamentAssignmentService assignments;
    private final Map<String, Integer> inviteRequests = new HashMap<>();
    private long inviteMinute;
    private int totalInviteRequests;

    public TournamentRegistrationController(TournamentRegistrationService registration, TournamentAssignmentService assignments) {
        this.registration = registration;
        this.assignments = assignments;
    }

    @PostMapping("/{id}/register")
    public ResponseEntity<TournamentRegistration.Status> register(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return respond(registration.register(id, viewer(jwt)));
    }

    @PostMapping("/{id}/unregister")
    public ResponseEntity<TournamentRegistration.Status> unregister(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return respond(registration.unregister(id, viewer(jwt)));
    }

    @GetMapping("/{id}/registration")
    public ResponseEntity<TournamentRegistration.Status> registration(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return respond(registration.registration(id, viewer(jwt)));
    }

    @GetMapping("/{id}/waiting-room")
    public ResponseEntity<TournamentRegistration.WaitingRoom> waitingRoom(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return respond(registration.waitingRoom(id, viewer(jwt)));
    }

    @GetMapping("/{id}/assignment")
    public ResponseEntity<TournamentAssignment> assignment(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return respond(assignments.get(id, viewer(jwt)));
    }

    @PostMapping("/private/resolve")
    public ResponseEntity<TournamentRegistration.Invitation> resolve(@RequestBody TournamentRegistration.InviteRequest invite,
            HttpServletRequest request, @AuthenticationPrincipal Jwt jwt) {
        limitInvites(request.getRemoteAddr(), viewer(jwt));
        return respond(registration.resolve(invite.code()));
    }

    @PostMapping("/private/register")
    public ResponseEntity<TournamentRegistration.Status> registerPrivate(@RequestBody TournamentRegistration.InviteRequest invite,
            HttpServletRequest request, @AuthenticationPrincipal Jwt jwt) {
        var user = viewer(jwt);
        limitInvites(request.getRemoteAddr(), user);
        return respond(registration.registerPrivate(invite.tournamentId(), user, invite.code()));
    }

    private synchronized void limitInvites(String address, UUID viewer) {
        long minute = Instant.now().getEpochSecond() / 60;
        if (minute != inviteMinute) {
            inviteMinute = minute;
            inviteRequests.clear();
            totalInviteRequests = 0;
        }
        String player = viewer.toString();
        if (totalInviteRequests >= 300 || inviteRequests.getOrDefault(address, 0) >= 30
                || inviteRequests.getOrDefault(player, 0) >= 12) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "INVITE_RATE_LIMIT",
                    "Too many invite attempts. Try again in a minute.");
        }
        totalInviteRequests++;
        inviteRequests.merge(address, 1, Integer::sum);
        inviteRequests.merge(player, 1, Integer::sum);
    }

    private static UUID viewer(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static <T> ResponseEntity<T> respond(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
