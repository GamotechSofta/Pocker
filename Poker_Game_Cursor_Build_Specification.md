# Poker Game — Cursor AI Build Specification

## 0. Purpose

Build a complete, production-structured **Poker Game** from scratch using:

- Frontend: **React + Vite + TypeScript**
- Backend: **Spring Boot + Java**
- Database: **PostgreSQL**
- Real-time communication: **WebSocket / STOMP**
- Authentication: **JWT**
- Frontend styling: **Tailwind CSS**
- State management: **Zustand**
- Backend build: **Maven**
- API documentation: **OpenAPI / Swagger**
- Testing: **JUnit + Mockito + React Testing Library**
- Development environment: **Docker Compose**

### Important scope

This project is initially a **play-money poker game**.

Do NOT implement:
- real-money deposits
- withdrawals
- payment gateway
- gambling wallet
- cash prizes
- betting with real currency

Use virtual chips only.

---

# 1. Source of Poker Rules

The uploaded `Poker_Rules_Guide.docx` is the primary rules reference.

It covers five variants:

1. Texas Hold'em
2. Omaha
3. Pineapple
4. 5-Card Draw
5. Razz

The document defines the standard poker hand rankings, dealing sequence, betting actions, showdown rules, and variant-specific hand construction.

For Texas Hold'em, the source specifies:
- 2 private hole cards per player
- 5 community cards
- Pre-Flop
- Flop
- Turn
- River
- Showdown
- Small Blind and Big Blind
- Fold, Check, Bet, Call, Raise
- Best 5-card hand from the available 7 cards
- Players may use 0, 1, or 2 hole cards

For Omaha:
- 4 private cards
- 5 community cards
- exactly 2 hole cards MUST be used
- exactly 3 community cards MUST be used

For Pineapple:
- 3 private cards initially
- discard 1 before the flop
- continue like Texas Hold'em

For 5-Card Draw:
- 5 private cards
- betting
- discard selected cards
- receive replacements
- second betting round
- showdown

For Razz:
- 7 cards total
- no community cards
- lowest qualifying 5-card hand wins
- Aces are low
- straights and flushes do not hurt the low hand

Use the uploaded rules document as the source of truth for the initial implementation.

---

# 2. First Version Priority

Do NOT attempt to build all five variants simultaneously.

Build in this order:

### Phase 1
**Texas Hold'em — complete and playable**

### Phase 2
Poker engine abstraction

### Phase 3
Omaha

### Phase 4
Pineapple

### Phase 5
5-Card Draw

### Phase 6
Razz

The architecture must allow new variants without rewriting the entire application.

---

# 3. Project Architecture

Create this repository structure:

```text
poker-game/
│
├── frontend/
│   ├── src/
│   │   ├── api/
│   │   ├── assets/
│   │   ├── components/
│   │   ├── constants/
│   │   ├── hooks/
│   │   ├── layouts/
│   │   ├── pages/
│   │   ├── routes/
│   │   ├── services/
│   │   ├── store/
│   │   ├── types/
│   │   ├── utils/
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── package.json
│   └── vite.config.ts
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/poker/
│   │   │   │   ├── config/
│   │   │   │   ├── controller/
│   │   │   │   ├── dto/
│   │   │   │   ├── entity/
│   │   │   │   ├── enums/
│   │   │   │   ├── exception/
│   │   │   │   ├── mapper/
│   │   │   │   ├── repository/
│   │   │   │   ├── security/
│   │   │   │   ├── service/
│   │   │   │   ├── websocket/
│   │   │   │   └── game/
│   │   │   │       ├── engine/
│   │   │   │       ├── evaluator/
│   │   │   │       ├── model/
│   │   │   │       ├── rules/
│   │   │   │       └── variants/
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── application-dev.yml
│   │   └── test/
│   │
│   └── pom.xml
│
├── docker-compose.yml
├── README.md
└── docs/
    ├── architecture.md
    ├── api.md
    ├── poker-rules.md
    └── development.md
```

---

# 4. Core Domain Model

Create the following concepts.

## Card

```text
Card
- rank
- suit
```

Ranks:

```text
TWO
THREE
FOUR
FIVE
SIX
SEVEN
EIGHT
NINE
TEN
JACK
QUEEN
KING
ACE
```

Suits:

```text
CLUBS
DIAMONDS
HEARTS
SPADES
```

---

# 5. Deck

Create a standard 52-card deck.

Responsibilities:

```text
createDeck()
shuffle()
draw()
drawMultiple()
remainingCards()
reset()
```

Rules:

- exactly 52 cards
- no duplicate cards
- cryptographically secure/random shuffle where practical
- never expose the complete server deck to the frontend
- cards must be server-authoritative

Create unit tests for:
- deck size
- uniqueness
- shuffle
- drawing
- exhaustion

---

# 6. Poker Hand Rankings

Implement evaluator for:

1. Royal Flush
2. Straight Flush
3. Four of a Kind
4. Full House
5. Flush
6. Straight
7. Three of a Kind
8. Two Pair
9. One Pair
10. High Card

The uploaded guide defines these rankings from highest to lowest.

Implement a deterministic hand evaluator.

Suggested structure:

```text
HandRank
- ROYAL_FLUSH
- STRAIGHT_FLUSH
- FOUR_OF_A_KIND
- FULL_HOUSE
- FLUSH
- STRAIGHT
- THREE_OF_A_KIND
- TWO_PAIR
- ONE_PAIR
- HIGH_CARD
```

Create:

```text
PokerHandEvaluation
- rank
- rankingValues
- cards
- description
```

`rankingValues` must allow accurate tie-breaking.

Example:

```text
Pair of Aces + K kicker
beats
Pair of Aces + Q kicker
```

Do not compare hands using only the hand category.

---

# 7. Texas Hold'em Engine

Implement Texas Hold'em first.

## Game lifecycle

```text
WAITING
↓
STARTING
↓
PRE_FLOP
↓
FLOP
↓
TURN
↓
RIVER
↓
SHOWDOWN
↓
PAYOUT
↓
NEXT_HAND
```

If everybody except one player folds:

```text
ACTIVE_BETTING
↓
PLAYER_WON_BY_FOLD
↓
PAYOUT
↓
NEXT_HAND
```

---

# 8. Texas Hold'em Dealing

At the beginning of a hand:

1. Move dealer button.
2. Determine Small Blind.
3. Determine Big Blind.
4. Shuffle deck.
5. Deal 2 hole cards to each active player.
6. Start Pre-Flop betting.

Then:

```text
Flop = 3 community cards
Turn = 1 community card
River = 1 community card
```

At showdown:

```text
available cards = 2 hole + 5 community
best hand = best possible 5-card combination
```

The player may use:
- both hole cards
- one hole card
- zero hole cards

---

# 9. Betting Engine

Implement:

```text
FOLD
CHECK
BET
CALL
RAISE
```

Also track:

```text
currentBet
playerContribution
pot
minimumRaise
lastRaiseAmount
```

Validate every action on the backend.

Never trust frontend validation.

Examples:

### Check

Allowed only when:

```text
playerContribution == currentBet
```

### Call

Required amount:

```text
currentBet - playerContribution
```

### Raise

Validate:
- player has enough chips
- raise meets minimum raise rules
- player is currently active
- it is player's turn
- betting round is active

Do not hard-code betting validation only in React.

---

# 10. Blinds

Create configurable table settings:

```text
smallBlind
bigBlind
startingChips
maxPlayers
```

Example development configuration:

```text
smallBlind = 10
bigBlind = 20
startingChips = 1000
maxPlayers = 6
```

Make these configurable through environment/configuration rather than hard-coding them throughout the codebase.

---

# 11. Pot Management

Create a dedicated PotManager.

Responsibilities:

```text
collectBet()
calculatePot()
calculateSidePots()
distributePot()
```

Even if Phase 1 initially uses a simple main pot, design the system so side pots can be added safely.

Players can only win chips that are legally present in the pot.

Add tests for:
- equal contributions
- unequal contributions
- all-in
- multiple winners
- split pots

---

# 12. Turn Management

Create a TurnManager.

Track:

```text
currentPlayer
activePlayers
foldedPlayers
allInPlayers
playersWhoActed
```

The backend determines whose turn it is.

Frontend only displays the state.

Never allow:

```text
Player A sending a request
while Player B's turn is active
```

to change the game.

---

# 13. Game State

Create a server-side game state model:

```text
GameState
- gameId
- variant
- status
- handNumber
- dealerSeat
- smallBlindSeat
- bigBlindSeat
- currentTurnSeat
- players
- communityCards
- pot
- currentBet
- minimumRaise
- bettingRound
```

Player state:

```text
PlayerState
- playerId
- username
- seat
- chipStack
- holeCards
- contribution
- status
- folded
- allIn
- connected
```

Do not expose another player's private cards before showdown.

---

# 14. Real-Time Architecture

Use WebSocket/STOMP.

Frontend should receive events such as:

```text
GAME_STATE
PLAYER_JOINED
PLAYER_LEFT
HAND_STARTED
CARDS_DEALT
BETTING_STARTED
PLAYER_ACTION
COMMUNITY_CARDS
TURN_CHANGED
SHOWDOWN
POT_AWARDED
HAND_COMPLETED
ERROR
```

Example:

```text
/ws/game/{gameId}
```

Player action endpoint:

```text
/app/game/{gameId}/action
```

Payload:

```json
{
  "action": "RAISE",
  "amount": 100
}
```

Backend validates and broadcasts the updated sanitized state.

---

# 15. Security Rule

The backend is the authoritative game server.

Never calculate the real game result only on React.

Frontend may calculate/display previews for UX, but:

- card dealing = backend
- shuffle = backend
- turn = backend
- legal action = backend
- hand evaluation = backend
- winner = backend
- pot = backend
- chip balance = backend

The frontend must never be trusted.

---

# 16. Authentication

Implement JWT authentication.

Endpoints:

```text
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me
```

User fields:

```text
id
username
email
passwordHash
createdAt
updatedAt
```

Never store plain-text passwords.

Use BCrypt/secure password hashing.

---

# 17. Lobby

Create lobby functionality.

Pages:

```text
/login
/register
/lobby
/game/:gameId
/profile
```

Lobby should display:

- table name
- game variant
- blinds
- players
- maximum seats
- status
- Join button

Example:

```text
Texas Hold'em
6 Max
10 / 20
4 / 6 Players
PLAY
```

---

# 18. Poker Table UI

Create a professional poker-table interface.

Desktop-first, responsive.

Table should contain:

```text
                 Player 2

       Player 3             Player 1


                 COMMUNITY
              [ ] [ ] [ ] [ ] [ ]


                 POT
                ₹100


       Player 4             Player 6

                 YOU
              [CARD] [CARD]

       Fold     Check     Call     Raise
```

Use virtual chip terminology such as:

```text
1,000 chips
```

Do not display real currency.

---

# 19. Frontend Components

Create reusable components:

```text
PokerTable
PlayerSeat
PlayerAvatar
HoleCards
CommunityCards
PlayingCard
PotDisplay
ChipStack
DealerButton
BlindIndicator
ActionPanel
BetSlider
BetInput
GameHeader
GameLog
WinnerAnnouncement
ShowdownPanel
LobbyTableCard
ConnectionStatus
Toast
Modal
```

---

# 20. Card Design

Create cards using CSS/React rather than depending on copyrighted external assets.

Each card should support:

```text
rank
suit
faceUp
faceDown
highlighted
winning
```

Card back:

- premium poker style
- clean pattern
- no external copyrighted logo

Cards should animate when dealt.

---

# 21. Action Panel

Show only legal actions.

Examples:

When no bet exists:

```text
CHECK
BET
FOLD
```

When a bet exists:

```text
CALL
RAISE
FOLD
```

If player is all-in:

```text
ALL-IN
```

The frontend can hide invalid buttons, but backend remains the final validator.

---

# 22. Bet Slider

Create:

```text
minimum raise
maximum allowed amount
current bet
player chips
```

Buttons:

```text
MIN
1/2 POT
POT
MAX
```

Only enable options valid for the current game state.

---

# 23. Showdown

At showdown:

1. Reveal remaining players' hole cards.
2. Evaluate hands.
3. Display hand name.
4. Display winning combination.
5. Calculate pot distribution.
6. Announce winner.
7. Start next hand after a short delay.

Example:

```text
ROSHAN
Full House
Aces full of Kings

Winner
+1,240 chips
```

Do not claim a winner until backend evaluation is complete.

---

# 24. Game Log

Create a small activity panel:

```text
Roshan posted Small Blind 10
Player 2 posted Big Blind 20
Player 3 called 20
Roshan raised to 60
Player 2 folded
Player 3 called 60
Flop dealt
```

This is useful for debugging and user understanding.

---

# 25. Database Design

Create tables/entities:

```text
users
poker_tables
poker_table_players
game_sessions
hands
hand_players
player_actions
```

Suggested fields:

### poker_tables

```text
id
name
variant
small_blind
big_blind
max_players
status
created_at
updated_at
```

### game_sessions

```text
id
table_id
hand_number
dealer_seat
status
started_at
ended_at
```

### player_actions

```text
id
hand_id
player_id
action
amount
betting_round
created_at
```

Do not store private hole cards in client-accessible responses.

---

# 26. REST API

Create:

```text
GET    /api/tables
POST   /api/tables
GET    /api/tables/{id}
POST   /api/tables/{id}/join
POST   /api/tables/{id}/leave
POST   /api/tables/{id}/start
GET    /api/tables/{id}/state
```

Use WebSocket for real-time game actions/state.

Use REST for lobby/table management.

---

# 27. Error Handling

Create standard API errors:

```json
{
  "timestamp": "...",
  "status": 400,
  "code": "INVALID_GAME_ACTION",
  "message": "It is not your turn.",
  "path": "/api/..."
}
```

Create custom exceptions:

```text
GameNotFoundException
PlayerNotFoundException
InvalidGameActionException
NotPlayerTurnException
InsufficientChipsException
InvalidRaiseException
GameAlreadyStartedException
SeatUnavailableException
```

---

# 28. Reconnection

WebSocket connections can disconnect.

Implement:

```text
connected
disconnected
reconnecting
reconnected
```

When a player reconnects:

1. Authenticate again.
2. Identify player.
3. Rejoin the game session.
4. Send current sanitized game state.
5. Restore UI.

The server remains the source of truth during disconnects.

---

# 29. Anti-Cheat Requirements

Implement server-side protection against:

- invalid actions
- acting out of turn
- seeing opponent hole cards
- changing chip balance
- changing player seat
- manipulating pot
- submitting duplicate actions
- replaying stale actions

Use action IDs / request IDs where appropriate to prevent duplicate processing.

---

# 30. Testing Strategy

## Backend

Write unit tests for:

### Card

```text
CardTest
DeckTest
```

### Evaluator

```text
RoyalFlushTest
StraightFlushTest
FourOfAKindTest
FullHouseTest
FlushTest
StraightTest
ThreeOfAKindTest
TwoPairTest
PairTest
HighCardTest
TieBreakerTest
```

### Texas Hold'em

```text
PreFlopTest
FlopTest
TurnTest
RiverTest
FoldWinTest
ShowdownTest
BlindsTest
TurnOrderTest
PotTest
AllInTest
```

### Security

```text
UnauthorizedActionTest
WrongPlayerTurnTest
PrivateCardVisibilityTest
```

---

# 31. Frontend Testing

Test:

- login
- lobby
- join table
- table rendering
- card rendering
- action buttons
- bet slider
- WebSocket updates
- reconnection
- showdown
- error messages

---

# 32. Development Seed Data

Create development users:

```text
player1
player2
player3
player4
player5
player6
```

Give each:

```text
1000 virtual chips
```

Never use real money.

---

# 33. Docker

Create `docker-compose.yml`.

Services:

```text
postgres
backend
frontend
```

Postgres development database:

```text
database: poker
username: poker
password: poker
```

Make all values configurable using environment variables.

---

# 34. Environment Variables

Frontend:

```text
VITE_API_URL
VITE_WS_URL
```

Backend:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
```

Never commit secrets.

Create:

```text
.env.example
```

---

# 35. Logging

Backend logs:

```text
GAME_CREATED
PLAYER_JOINED
HAND_STARTED
ACTION_RECEIVED
ACTION_REJECTED
FLOP_DEALT
TURN_DEALT
RIVER_DEALT
SHOWDOWN
POT_AWARDED
PLAYER_DISCONNECTED
PLAYER_RECONNECTED
```

Do NOT log private hole cards in production logs.

---

# 36. Observability

Add:

- Spring Boot Actuator
- health endpoint
- structured logging
- basic metrics

Example:

```text
/actuator/health
```

---

# 37. Swagger

Configure OpenAPI.

Document:

- auth APIs
- lobby APIs
- table APIs
- game state APIs

Add descriptions for request/response DTOs.

---

# 38. Important Game Engine Design

Do NOT put all poker logic inside controllers.

Bad:

```text
GameController
    -> 1000 lines of poker logic
```

Good:

```text
Controller
    ↓
GameService
    ↓
PokerGameEngine
    ↓
RuleSet
    ↓
HandEvaluator
    ↓
PotManager
    ↓
GameState
```

The poker engine must be testable without Spring or WebSocket.

---

# 39. Variant Abstraction

Create an interface similar to:

```java
public interface PokerVariant {

    String getName();

    int getInitialHoleCards();

    boolean usesCommunityCards();

    List<Card> dealInitialCards(...);

    List<Card> getCommunityCards(...);

    HandEvaluation evaluateHand(...);

    List<Action> getLegalActions(...);
}
```

Do not blindly copy this exact interface if a better design is needed. Preserve clean separation between:

- dealing rules
- betting rules
- hand construction rules
- hand evaluation
- round progression

---

# 40. Texas Hold'em Variant

Implement:

```text
TexasHoldemVariant
```

Rules:

```text
2 hole cards
5 community cards
best 5 of 7
0–2 hole cards may be used
highest hand wins
```

---

# 41. Omaha Variant

After Hold'em is stable:

```text
OmahaVariant
```

Rules from the source guide:

```text
4 hole cards
5 community cards
exactly 2 hole cards
exactly 3 community cards
highest hand wins
```

Do NOT reuse Hold'em hand construction logic incorrectly.

The evaluator must generate combinations according to Omaha's exact 2+3 rule.

---

# 42. Pineapple Variant

Implement:

```text
PineappleVariant
```

Rules:

```text
3 hole cards
discard 1 before flop
2 remaining hole cards
then normal Hold'em
```

The discarded card must never become a community card.

---

# 43. 5-Card Draw Variant

Implement:

```text
FiveCardDrawVariant
```

Rules:

```text
5 private cards
first betting round
draw/discard
replacement cards
second betting round
showdown
```

Make the maximum draw count configurable because the uploaded guide notes that house rules can vary.

---

# 44. Razz Variant

Implement:

```text
RazzVariant
```

Rules from the source guide:

```text
7 cards total
no community cards
lowest qualifying 5-card hand wins
Aces are low
straights do not hurt
flushes do not hurt
pairs are bad
```

Implement a dedicated low-hand evaluator.

Do not reuse the normal high-hand evaluator as the winner comparison.

---

# 45. UI/UX Requirements

Design should feel like a modern online poker room.

Use:

- dark poker-table theme
- premium but simple interface
- readable cards
- clear turn indicator
- chip animations
- subtle transitions
- responsive layout
- accessible buttons
- clear action feedback

Avoid:

- excessive gradients
- clutter
- tiny buttons
- unnecessary animation
- confusing casino-style effects

The game must remain easy to understand for a beginner.

---

# 46. Responsive Design

Support:

```text
Desktop
Laptop
Tablet
Mobile
```

On mobile:

- poker table scales correctly
- player seats remain readable
- cards remain visible
- action buttons stay accessible
- no horizontal overflow

---

# 47. Beginner-Friendly UX

Because the user may not know poker initially, add optional:

```text
What is this?
```

help tooltips.

Examples:

```text
Check:
Pass your turn without adding chips when no bet is required.

Call:
Match the current bet.

Raise:
Increase the amount other players must match.

Fold:
Leave the current hand.
```

Do not interfere with experienced players.

---

# 48. State Synchronization

Frontend state must always be derived from the latest server state.

Use Zustand store:

```text
authStore
lobbyStore
gameStore
uiStore
```

Game store should handle:

```text
setGameState()
updatePlayer()
setCommunityCards()
setCurrentTurn()
setPot()
setConnectionStatus()
resetGame()
```

Do not duplicate authoritative game calculations in multiple frontend components.

---

# 49. WebSocket Message Security

Server must sanitize outgoing state per player.

Example:

Player A should receive:

```json
{
  "you": {
    "holeCards": ["AS", "KH"]
  },
  "opponents": [
    {
      "username": "Player2",
      "holeCards": ["HIDDEN", "HIDDEN"]
    }
  ]
}
```

At showdown, reveal eligible cards.

Never send hidden cards to the browser early.

---

# 50. Cursor Execution Rules

When you start implementing this project:

### Rule 1
First inspect the repository.

### Rule 2
Do not overwrite existing files blindly.

### Rule 3
If the repository is empty, create the complete project structure.

### Rule 4
Implement one phase at a time.

### Rule 5
After each phase:

```text
compile
test
fix errors
document changes
```

### Rule 6
Do not move to the next phase if the current phase has compilation or test failures.

### Rule 7
Do not create fake APIs just to make the frontend work.

### Rule 8
The frontend must integrate with the actual Spring Boot backend.

### Rule 9
Do not hard-code game results.

### Rule 10
Do not trust client-side poker logic.

---

# 51. Implementation Sequence

Follow exactly this order.

## Step 1 — Repository setup

Create:

```text
frontend
backend
docs
docker-compose.yml
README.md
.env.example
```

Verify both applications start.

---

## Step 2 — Backend foundation

Implement:

- Spring Boot
- Maven
- PostgreSQL
- JPA
- Flyway
- validation
- exception handling
- logging
- Swagger
- Actuator

Verify:

```text
mvn test
mvn spring-boot:run
```

---

## Step 3 — Authentication

Implement:

- User entity
- registration
- login
- JWT
- BCrypt
- Spring Security
- current user endpoint

Write tests.

---

## Step 4 — Card engine

Implement:

```text
Card
Rank
Suit
Deck
```

Write complete tests.

---

## Step 5 — Hand evaluator

Implement all 10 high-hand categories.

Implement tie breakers.

Test extensively.

Do NOT continue until evaluator tests pass.

---

## Step 6 — Texas Hold'em engine

Implement:

- game state
- seats
- dealer button
- blinds
- hole cards
- pre-flop
- flop
- turn
- river
- showdown
- fold win
- pot
- turn order

Test the complete lifecycle.

---

## Step 7 — REST lobby

Implement:

- create table
- list tables
- join
- leave
- start

---

## Step 8 — WebSocket

Implement:

- connection
- authentication
- game subscription
- action messages
- game-state broadcasting
- disconnect/reconnect

---

## Step 9 — React foundation

Implement:

- Vite
- TypeScript
- Tailwind
- routing
- Zustand
- API layer
- WebSocket service

---

## Step 10 — Authentication UI

Create:

```text
Login
Register
```

---

## Step 11 — Lobby UI

Create:

```text
Lobby
TableCard
CreateTableModal
JoinTable
```

---

## Step 12 — Poker Table UI

Implement:

- table
- seats
- cards
- community cards
- pot
- chip stacks
- dealer button
- blinds
- turn indicator

---

## Step 13 — Player Actions

Implement:

```text
Fold
Check
Call
Bet
Raise
All-in
```

Connect to backend.

---

## Step 14 — Showdown UI

Implement:

- card reveal
- hand ranking
- winner
- pot result
- next hand

---

## Step 15 — Reconnection

Implement reconnect and state resync.

---

## Step 16 — Full Integration Testing

Test:

```text
Player registration
↓
Login
↓
Lobby
↓
Create table
↓
Join table
↓
Start game
↓
Deal
↓
Bet
↓
Flop
↓
Bet
↓
Turn
↓
Bet
↓
River
↓
Bet
↓
Showdown
↓
Winner
↓
Next hand
```

---

# 52. Phase 2 — Omaha

Only after Texas Hold'em is stable.

Implement:

```text
OmahaVariant
```

Test the exact:

```text
2 hole + 3 board
```

rule.

Create Omaha-specific UI text.

---

# 53. Phase 3 — Pineapple

Implement:

```text
3 cards
↓
discard 1
↓
2 cards
↓
Hold'em flow
```

Add discard UI.

---

# 54. Phase 4 — 5-Card Draw

Implement:

```text
5 cards
↓
bet
↓
select cards to discard
↓
draw replacements
↓
bet
↓
showdown
```

Add card selection UI.

---

# 55. Phase 5 — Razz

Implement:

```text
3 starting cards
↓
betting
↓
additional exposed/private cards
↓
7 total
↓
low-hand evaluation
↓
lowest wins
```

Create a dedicated Razz evaluator and tests.

---

# 56. Definition of Done

The project is considered complete only when:

### Backend

- [ ] Compiles successfully
- [ ] Tests pass
- [ ] PostgreSQL works
- [ ] JWT works
- [ ] WebSocket works
- [ ] Texas Hold'em works
- [ ] Omaha works
- [ ] Pineapple works
- [ ] 5-Card Draw works
- [ ] Razz works
- [ ] Hand evaluator is tested
- [ ] Pot logic is tested
- [ ] Reconnection works
- [ ] Swagger works
- [ ] Actuator works

### Frontend

- [ ] Compiles successfully
- [ ] Login works
- [ ] Register works
- [ ] Lobby works
- [ ] Create table works
- [ ] Join table works
- [ ] Poker table works
- [ ] Cards render correctly
- [ ] Actions work
- [ ] Real-time updates work
- [ ] Showdown works
- [ ] Responsive UI works
- [ ] Reconnection works
- [ ] All five variants have UI support

---

# 57. Important Cursor Prompt

Use this as the initial prompt in Cursor:

```text
You are the lead full-stack engineer for this project.

Build a complete play-money Poker Game from scratch using React + TypeScript on the frontend and Spring Boot + Java on the backend.

The uploaded Poker Rules Guide is the primary source of truth for the poker rules.

The application must eventually support:
1. Texas Hold'em
2. Omaha
3. Pineapple
4. 5-Card Draw
5. Razz

However, DO NOT implement all variants at once.

Start with Texas Hold'em and create a clean variant-based poker engine so the other variants can be added later.

Requirements:

- React + Vite + TypeScript
- Tailwind CSS
- Zustand
- Spring Boot
- Java
- Maven
- PostgreSQL
- JPA/Hibernate
- Flyway
- Spring Security
- JWT
- WebSocket/STOMP
- Swagger/OpenAPI
- Actuator
- Docker Compose
- JUnit
- Mockito
- React Testing Library

The server must be authoritative.

The backend must control:
- deck
- shuffle
- dealing
- player turn
- legal actions
- blinds
- bets
- raises
- pot
- hand evaluation
- winners
- chip balances
- game progression

Never trust frontend game logic.

Never send opponent private hole cards to the frontend before showdown.

Build the project incrementally.

PHASE 1:
Create repository structure and development environment.

PHASE 2:
Create Spring Boot backend foundation.

PHASE 3:
Create authentication.

PHASE 4:
Create Card, Suit, Rank and Deck.

PHASE 5:
Create complete poker hand evaluator with all 10 hand rankings and tie breakers.

PHASE 6:
Create Texas Hold'em game engine.

PHASE 7:
Create lobby REST APIs.

PHASE 8:
Create WebSocket real-time game communication.

PHASE 9:
Create React application foundation.

PHASE 10:
Create login/register UI.

PHASE 11:
Create lobby UI.

PHASE 12:
Create complete Texas Hold'em table UI.

PHASE 13:
Connect frontend actions to WebSocket backend.

PHASE 14:
Create showdown and winner UI.

PHASE 15:
Create reconnect/state synchronization.

After each phase:
1. Compile.
2. Run tests.
3. Fix all errors.
4. Verify the implementation.
5. Update documentation.
6. Only then continue.

Do not skip tests.

Do not create fake/mock APIs in place of the actual backend.

Do not hard-code winners or cards.

Do not put poker business logic inside React components.

Do not put all poker business logic inside controllers.

Use clean architecture with:
Controller
→ Service
→ Poker Engine
→ Variant Rules
→ Evaluator
→ Pot Manager
→ Game State

First inspect the repository and determine whether it is empty or contains existing code.

If empty, create the complete structure.

Before writing a large amount of code, provide a concise implementation plan and then begin Phase 1.

At the end of every phase, report:
- files created
- files changed
- functionality implemented
- tests added
- test result
- next phase

Start now with Phase 1 only.
```

---

# 58. Subsequent Cursor Prompts

After Phase 1 is successfully completed, use:

```text
Phase 1 is complete.

Now implement Phase 2 only: Spring Boot backend foundation.

Follow the Poker Game Build Specification.

Create the backend with:
- Spring Boot
- Java
- Maven
- PostgreSQL
- JPA/Hibernate
- Flyway
- validation
- global exception handling
- logging
- Swagger/OpenAPI
- Actuator

Do not implement poker game logic yet.

Run all backend tests and verify the application starts successfully.

Do not move to Phase 3 until Phase 2 is fully working.
```

Then:

```text
Phase 2 is complete.

Now implement Phase 3 only: Authentication.

Implement:
- User entity
- registration
- login
- BCrypt password hashing
- JWT authentication
- Spring Security
- GET /api/auth/me
- validation
- authentication tests

Do not implement poker gameplay yet.

Run tests and fix all failures before finishing.
```

Then:

```text
Phase 3 is complete.

Now implement Phase 4 only: Card Engine.

Create:
- Card
- Rank
- Suit
- Deck
- shuffle
- draw
- drawMultiple
- reset

Create complete unit tests.

The deck must contain exactly 52 unique cards.

Do not implement Texas Hold'em yet.

Run all tests before finishing.
```

Then:

```text
Phase 4 is complete.

Now implement Phase 5 only: Poker Hand Evaluator.

Implement all ten hand rankings:
Royal Flush
Straight Flush
Four of a Kind
Full House
Flush
Straight
Three of a Kind
Two Pair
One Pair
High Card

Implement exact tie-breaking.

Test every ranking and difficult edge cases including Ace-low straight.

Do not implement game flow yet.

Do not continue until evaluator tests pass.
```

Then:

```text
Phase 5 is complete.

Now implement Phase 6: Texas Hold'em game engine.

Implement:
- table seats
- dealer button
- small blind
- big blind
- 2 hole cards
- pre-flop
- flop
- turn
- river
- betting
- fold
- check
- call
- bet
- raise
- all-in
- showdown
- winner
- pot
- next hand

The backend is authoritative.

Write extensive unit tests.

Do not build the React table yet.
```

Then:

```text
Texas Hold'em engine is complete and tested.

Now implement Phase 7: Lobby REST APIs.

Create:
- list tables
- create table
- get table
- join table
- leave table
- start table
- get sanitized table state

Add validation and tests.

Do not implement frontend yet.
```

Then:

```text
Lobby APIs are complete.

Now implement Phase 8: WebSocket/STOMP real-time communication.

Implement:
- authenticated WebSocket connection
- game subscription
- player action messages
- server validation
- sanitized state broadcasting
- turn updates
- community card updates
- showdown events
- errors
- disconnect handling

Do not expose private opponent cards.

Write integration tests where practical.
```

Then:

```text
Backend gameplay infrastructure is complete.

Now implement Phase 9: React frontend foundation.

Create:
- Vite
- React
- TypeScript
- Tailwind
- Zustand
- routing
- API service
- WebSocket service
- environment configuration
- reusable UI structure

Do not build the complete poker table yet.
```

Then:

```text
Frontend foundation is complete.

Now implement Phase 10: Login and Register pages.

Connect them to the real Spring Boot authentication APIs.

Implement:
- form validation
- loading states
- API errors
- JWT storage using a secure practical approach
- protected routes
- logout

Test the flow.
```

Then:

```text
Authentication UI is complete.

Now implement Phase 11: Lobby.

Create:
- table list
- table cards
- create table
- join table
- leave table
- loading states
- empty state
- errors
- responsive layout

Connect to real backend APIs.
```

Then:

```text
Lobby is complete.

Now implement Phase 12: Texas Hold'em Poker Table UI.

Create:
- poker table
- player seats
- player avatars
- cards
- community cards
- pot
- chips
- dealer button
- blinds
- current-turn indicator
- game log
- action panel

Use the real WebSocket game state.

Do not hard-code gameplay.
```

Then:

```text
Poker table UI is complete.

Now implement Phase 13: Player Actions.

Connect:
- Fold
- Check
- Call
- Bet
- Raise
- All-in

to the real WebSocket backend.

Show only legal actions.

Handle server-side action errors.

Make the UI update immediately from server state.
```

Then:

```text
Player actions are connected.

Now implement Phase 14: Showdown and Winner UI.

Implement:
- reveal eligible cards
- hand ranking display
- winning hand highlight
- pot result
- winner announcement
- next hand
- game log

All winner information must come from the backend.
```

Then:

```text
Texas Hold'em is playable.

Now implement Phase 15: Reconnection and state synchronization.

Handle:
- WebSocket disconnect
- reconnect
- authentication
- resubscription
- game-state synchronization
- connection status UI

Test disconnect/reconnect behavior.
```

---

# 59. Adding Other Variants

After Texas Hold'em is stable, use separate prompts.

## Omaha

```text
Texas Hold'em is complete.

Now add Omaha using the existing variant architecture.

Follow the uploaded Poker Rules Guide.

Rules:
- 4 hole cards
- 5 community cards
- exactly 2 hole cards must be used
- exactly 3 community cards must be used
- highest qualifying hand wins

Do not modify Texas Hold'em behavior.

Create Omaha-specific tests, evaluator combination logic, game flow, API support and frontend UI.

Run the complete existing Texas Hold'em test suite after the changes.
```

## Pineapple

```text
Add Pineapple using the existing variant architecture.

Rules from the uploaded guide:
- receive 3 hole cards
- discard exactly 1 before the flop
- continue with 2 cards like Texas Hold'em
- best 5-card hand from the remaining cards and community cards
- highest hand wins

Add backend tests and frontend discard UI.

Do not break Texas Hold'em or Omaha.
```

## 5-Card Draw

```text
Add 5-Card Draw.

Rules:
- 5 private cards
- first betting round
- player selects cards to discard
- replacement cards are dealt
- second betting round
- showdown
- highest normal poker hand wins

Make maximum draw count configurable because the uploaded guide notes that house rules can vary.

Create card-selection UI and backend validation.
```

## Razz

```text
Add Razz.

Rules from the uploaded Poker Rules Guide:
- no community cards
- 7 cards total
- 3 starting cards
- lowest qualifying 5-card hand wins
- Aces are low
- straights do not hurt
- flushes do not hurt
- pairs are bad
- compare low hands from highest card downward

Create a dedicated Razz low-hand evaluator.

Do not reuse normal high-hand comparison as the winner calculation.

Add extensive Razz tests.
```

---

# 60. Final Cursor Instruction

When all variants are implemented, run:

```text
Perform a complete production-readiness audit of the Poker Game.

Check:
- backend compilation
- frontend compilation
- unit tests
- integration tests
- authentication
- authorization
- WebSocket security
- private card visibility
- turn validation
- pot calculation
- side pots
- all-in
- split pots
- hand evaluator
- Ace-low straight
- Omaha 2+3 rule
- Pineapple discard
- 5-Card Draw replacement
- Razz low evaluation
- reconnection
- duplicate action protection
- database migrations
- environment variables
- error handling
- responsive UI
- accessibility
- performance

Fix real issues you find.

Do not rewrite working code unnecessarily.

At the end provide:
1. architecture summary
2. completed features
3. test summary
4. known limitations
5. production deployment checklist
6. local development commands
```

---

# 61. Local Development Commands

Expected commands:

## Backend

```bash
cd backend
./mvnw clean test
./mvnw spring-boot:run
```

Windows:

```bash
mvnw.cmd clean test
mvnw.cmd spring-boot:run
```

## Frontend

```bash
cd frontend
npm install
npm run dev
```

## Docker

```bash
docker compose up -d
```

Stop:

```bash
docker compose down
```

---

# 62. Final Principle

Build the poker engine first, UI second.

The correct dependency direction is:

```text
Poker Rules
     ↓
Game Engine
     ↓
Game State
     ↓
REST + WebSocket
     ↓
React State
     ↓
React UI
```

Never reverse this relationship.

The UI should visualize the game.

The backend should run the game.

The poker engine should implement the rules.

The tests should prove the rules.
