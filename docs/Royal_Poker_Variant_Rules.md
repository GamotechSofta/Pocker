# Royal Poker — Game Rules for Every Variant

Royal Poker is a **play-money** multiplayer poker game. Chips are virtual and have no cash value.
This document describes the rules exactly as the game server enforces them today, for all five
variants: Texas Hold'em, Omaha, Pineapple, 5-Card Draw and Razz.

---

## 1. Quick comparison

| Variant | Private cards | Shared (board) cards | Betting rounds | Special step | Winner |
| --- | --- | --- | --- | --- | --- |
| Texas Hold'em | 2 | 5 | 4 | — | Best 5 of 7 cards (high) |
| Omaha | 4 | 5 | 4 | — | Best 5 using **exactly 2** private + **exactly 3** board cards (high) |
| Pineapple | 3 → 2 | 5 | 4 | Throw away 1 private card before betting starts | Best 5 of 7 cards (high) |
| 5-Card Draw | 5 | 0 | 2 | Swap 0–5 cards after the first betting round | Best 5-card hand (high) |
| Razz | 3 → 7 | 0 | 5 | One more private card each street | **Lowest** 5 of 7 cards |

All variants use the same betting style (**No-Limit**), the same blinds, the same 30-second turn
timer and the same showdown/side-pot rules described in section 2.

---

## 2. Rules common to all variants

### 2.1 Joining a table

1. Pick a variant in the lobby.
2. Choose **2 players** or **6 players**.
3. Choose the stakes. Blinds and buy-in move together; the buy-in is always **50 big blinds**:

   | Small / Big blind | Buy-in (starting chips) |
   | --- | --- |
   | 5 / 10 | 500 |
   | 10 / 20 (default) | 1,000 |
   | 25 / 50 | 2,500 |
   | 50 / 100 | 5,000 |
   | 100 / 200 | 10,000 |
   | 250 / 500 | 25,000 |
   | 500 / 1,000 | 50,000 |

4. Press **PLAY NOW**. Matchmaking seats you with other players at the same variant, blinds and
   table size. If no other human is waiting, computer players (bots) take the remaining seats one by
   one. The first hand is dealt as soon as the table is full.

### 2.2 Dealer button and blinds

- A **dealer button (D)** marks the dealer for the hand. It moves one seat **clockwise** every hand.
- Before any cards are seen, two forced bets are posted:
  - **Small blind (SB):** the player to the dealer's left.
  - **Big blind (BB):** the next player clockwise.
- **Heads-up (2 players):** the dealer posts the small blind and the other player posts the big blind.
- A player who cannot cover a blind posts all of their remaining chips (all-in).

### 2.3 Dealing

- A fresh 52-card deck (no jokers) is shuffled for every hand.
- Cards are dealt one at a time, clockwise, starting with the player to the dealer's left.
- In Hold'em, Omaha and Pineapple, one card is **burned** (removed face down) before the flop, the
  turn and the river. 5-Card Draw and Razz do not burn cards.

### 2.4 Order of play

- Play always moves **clockwise**.
- **First betting round:** the player to the left of the big blind acts first.
  Heads-up, the dealer (small blind) acts first.
- **Later betting rounds:** the first player still in the hand to the left of the dealer acts first.
  Heads-up, the big blind acts first.

### 2.5 Actions

| Action | When it is allowed | What it does |
| --- | --- | --- |
| **Fold** | Always on your turn | Give up the hand. Chips you already put in stay in the pot. |
| **Check** | No bet to match | Pass without adding chips. |
| **Call** | Facing a bet | Match the current bet. |
| **Bet** | No bet yet this round | Make the first bet of the round. |
| **Raise** | Facing a bet | Increase the bet others must match. |
| **All-in** | Always, if you have chips | Put every remaining chip in. |

### 2.6 Betting limits (No-Limit)

- **Minimum bet:** one big blind.
- **Minimum raise:** the raise must be at least as large as the previous bet or raise in the same
  round, and never less than one big blind. Example with blinds 10/20: the big blind is 20, so the
  smallest raise is to 40. If a player raises to 60 (a raise of 40), the next player must raise to at
  least 100.
- **Maximum:** your whole stack. There is no cap.
- **Short all-in:** an all-in smaller than a full raise does **not** re-open betting for players who
  have already acted; they may only call or fold.
- A betting round ends when every player still in the hand has acted and all bets are equal (or the
  remaining players are all-in).

### 2.7 All-in and side pots

- A player who is all-in can only win the part of the pot they contributed to, from each other
  player.
- Any extra chips form one or more **side pots** that only the players who put in those chips can win.
- When nobody can bet any more (everyone else is all-in), the remaining cards are dealt out
  automatically and the hand goes straight to the showdown.

### 2.8 Winning the pot

- **Uncontested:** if everyone else folds, the last player wins the whole pot. Their cards are not
  shown.
- **Showdown:** after the last betting round, every player still in the hand reveals their cards.
  The best hand wins each pot (highest hand in all variants except Razz, where the lowest wins).
- **Ties:** tied players split the pot equally. If the pot cannot be split evenly, the odd chip goes
  to the tied player closest to the dealer's left.
- After every hand a result screen shows the winner, the winning hand, every revealed hand and each
  player's chips won or lost. The next hand is dealt automatically after a short countdown
  (about 7 seconds).

### 2.9 Turn timer

- Each player has **30 seconds** to act. The first turn of a hand also waits for the deal animation.
- If time runs out, the server acts for the player:
  - **Check** if checking is allowed, otherwise **Fold**.
  - In the Pineapple discard step, the **first** card is thrown away.
  - In the 5-Card Draw draw step, the player **keeps all five cards** (stands pat).

### 2.10 Running out of chips, leaving the table

- A player who reaches **0 chips** automatically gets a free play-money **rebuy** of one buy-in when
  the next hand starts.
- Leaving the table during a hand **folds** that hand; chips already in the pot are lost.
  Closing or refreshing the game page also leaves the table.

### 2.11 Hand rankings (high games: Hold'em, Omaha, Pineapple, 5-Card Draw)

From strongest to weakest:

| # | Hand | Example | Meaning |
| --- | --- | --- | --- |
| 1 | Royal Flush | A♠ K♠ Q♠ J♠ 10♠ | A-K-Q-J-10 of one suit |
| 2 | Straight Flush | 9♥ 8♥ 7♥ 6♥ 5♥ | Five in a row, same suit |
| 3 | Four of a Kind | Q♣ Q♦ Q♥ Q♠ 4♦ | Four cards of one rank |
| 4 | Full House | K♠ K♥ K♦ 7♣ 7♠ | Three of a kind plus a pair |
| 5 | Flush | A♦ J♦ 8♦ 6♦ 2♦ | Five cards of one suit |
| 6 | Straight | 10♣ 9♦ 8♠ 7♥ 6♣ | Five in a row, mixed suits |
| 7 | Three of a Kind | 7♠ 7♥ 7♦ K♣ 2♠ | Three cards of one rank |
| 8 | Two Pair | J♥ J♣ 4♠ 4♦ A♥ | Two different pairs |
| 9 | One Pair | 10♠ 10♥ K♦ 8♣ 3♠ | Two cards of one rank |
| 10 | High Card | A♣ Q♦ 9♠ 6♥ 3♣ | None of the above |

- Only the best **five** cards count.
- Ace is high (A-K-Q-J-10) and can also be low in the straight **A-2-3-4-5**, which is the lowest
  straight (5-high).
- Suits never break ties. Equal hands are compared by their cards in order (for example pair rank
  first, then the remaining "kicker" cards). If everything is equal, the pot is split.

---

## 3. Texas Hold'em

**The most popular poker game. Two private cards, five shared cards.**

| | |
| --- | --- |
| Players | 2 or 6 |
| Private cards | 2 per player |
| Board cards | 5 (shared by everyone) |
| Betting rounds | 4 — Pre-flop, Flop, Turn, River |
| Winning hand | Best 5-card high hand from your 2 cards + the 5 board cards |

### How a hand plays

1. **Blinds** are posted (section 2.2).
2. Every player receives **2 private cards**.
3. **Pre-flop betting** — starts with the player left of the big blind.
4. **Flop** — one card is burned, then **3** board cards are dealt face up. Betting round.
5. **Turn** — burn one, deal the **4th** board card. Betting round.
6. **River** — burn one, deal the **5th** board card. Final betting round.
7. **Showdown** — remaining players reveal; best five cards out of seven wins.

### Key points

- You may use **both**, **one** or **none** of your private cards. If the five board cards are your
  best hand ("playing the board"), you split with anyone else who also plays the board.
- Example: you hold A♠ K♠, board is Q♠ J♠ 10♠ 4♥ 2♦ → Royal Flush.

---

## 4. Omaha

**Like Hold'em, but with four private cards and a strict two-plus-three rule.**

| | |
| --- | --- |
| Players | 2 or 6 |
| Private cards | 4 per player |
| Board cards | 5 |
| Betting rounds | 4 — Pre-flop, Flop, Turn, River |
| Winning hand | Best high hand made from **exactly 2** private cards and **exactly 3** board cards |

### How a hand plays

Same order as Texas Hold'em (blinds → pre-flop → flop → turn → river → showdown), except that every
player receives **4** private cards.

### The 2 + 3 rule (most important Omaha rule)

- Your final five cards must be **exactly two** of your four private cards plus **exactly three** of
  the five board cards. The server checks every possible combination and picks your best one.
- You **cannot** play one private card and four board cards, and you cannot play the board.
- Example: you hold A♥ K♥ Q♣ J♣ and the board is 2♥ 7♥ 9♥ 4♠ 5♦.
  You have a flush: A♥ K♥ (2 private) + 2♥ 7♥ 9♥ (3 board).
- Example: you hold A♠ 3♦ 8♣ 9♥ and the board is K♠ Q♠ J♠ 10♠ 2♠.
  You do **not** have a royal flush, because you hold only one spade. Your best hand uses two of
  your cards with three from the board.

### Note for this game

- Omaha here uses **No-Limit** betting like every other variant (casinos usually play Pot-Limit Omaha).

---

## 5. Pineapple

**Hold'em with one extra private card that must be thrown away before betting.**

| | |
| --- | --- |
| Players | 2 or 6 |
| Private cards | 3 dealt, 1 discarded, 2 kept |
| Board cards | 5 |
| Betting rounds | 4 — Pre-flop, Flop, Turn, River |
| Winning hand | Best 5-card high hand from your 2 kept cards + 5 board cards |

### How a hand plays

1. **Blinds** are posted.
2. Every player receives **3 private cards**.
3. **Discard step** — starting left of the dealer and going clockwise, each player throws away
   **exactly one** of their three cards. There is no betting in this step.
4. From here the hand plays **exactly like Texas Hold'em**: pre-flop betting, flop, turn, river,
   showdown — with each player holding 2 private cards.

### Key points

- The discarded card is out of play for the rest of the hand. It is never shown and never becomes a
  board card.
- If the timer runs out during the discard step, the server throws away your first card.
- This is the classic "Pineapple" (discard **before** the flop). In "Crazy Pineapple" the discard
  happens after the flop — that version is not used here.

---

## 6. 5-Card Draw

**No board cards. Five private cards, one chance to swap.**

| | |
| --- | --- |
| Players | 2 or 6 |
| Private cards | 5 per player |
| Board cards | None |
| Betting rounds | 2 — before the draw and after the draw |
| Winning hand | Best 5-card high hand |

### How a hand plays

1. **Blinds** are posted.
2. Every player receives **5 private cards**.
3. **First betting round** — starts with the player left of the big blind.
4. **Draw step** — starting left of the dealer, each player still in the hand chooses which cards to
   throw away (**0 to 5 cards**) and receives the same number of new cards from the deck.
   Choosing none is called **standing pat**.
5. **Second betting round** — starts with the first active player left of the dealer.
6. **Showdown** — remaining players reveal their five cards; best hand wins.

### Key points

- There are no shared cards, so everyone's hand is completely private until the showdown.
- The maximum number of cards you may swap is a house setting (`MAX_DRAW_COUNT`), currently **5**.
- If the timer runs out during the draw step, you keep all your cards (stand pat).

---

## 7. Razz

**Seven-card lowball: the LOWEST hand wins.**

| | |
| --- | --- |
| Players | 2 or 6 |
| Private cards | Start with 3, one more each street, up to 7 |
| Board cards | None |
| Betting rounds | 5 — 3rd, 4th, 5th, 6th and 7th street |
| Winning hand | Lowest 5 cards out of your 7 |

### How a hand plays

1. **Blinds** are posted.
2. Every player receives **3 private cards** (**3rd street**). Betting round, starting left of the big blind.
3. **4th street** — every player still in the hand gets one more card. Betting round.
4. **5th street** — one more card. Betting round.
5. **6th street** — one more card. Betting round.
6. **7th street** — the seventh and last card. Final betting round.
7. **Showdown** — each player's best (lowest) five cards out of seven are compared.

### How low hands are ranked

- **Aces are always low** (Ace = 1).
- **Straights and flushes do not count** against you; 5-4-3-2-A of one suit is still a perfect low.
- **Pairs are bad.** Any hand with a pair loses to any hand of five different ranks; two pair loses to
  one pair, and so on.
- Compare hands by their **highest card first**, then the next highest, and so on. The lower card wins.
- The best possible hand is **A-2-3-4-5**, called the **wheel** (5-high).

| Hand | Result |
| --- | --- |
| 5-4-3-2-A | Best possible hand (wheel) |
| 6-4-3-2-A | Beats 6-5-3-2-A (4 is lower than 5 at the second card) |
| 7-5-4-3-2 | Beats 8-4-3-2-A (7 is lower than 8 at the highest card) |
| K-Q-J-10-8 | Loses to any 9-high or lower unpaired hand, beats any paired hand |
| 2-2-5-6-7 | A pair: loses to every unpaired hand |

### Notes for this game

- Razz here uses **blinds** like the other variants. Casino Razz usually uses antes and a "bring-in"
  forced bet instead.
- All seven cards are **private** (face down) until the showdown. In casino Razz some cards are dealt
  face up.

---

## 8. Glossary

| Term | Meaning |
| --- | --- |
| Blinds | Forced bets posted before the cards, so there is always something to win. |
| Button / Dealer (D) | Marks the dealer; it moves clockwise each hand and decides who acts first. |
| Board / Community cards | Face-up cards everyone can use (Hold'em, Omaha, Pineapple). |
| Hole cards | Your private cards. |
| Flop / Turn / River | The 3rd, 4th and 5th board cards (flop = first three together). |
| Burn | A card removed face down before board cards are dealt. |
| Street | A stage of the hand where cards are dealt and a betting round follows. |
| Kicker | A side card used to break a tie between hands of the same type. |
| All-in | Betting every chip you have. |
| Side pot | Extra chips that an all-in player cannot win. |
| Showdown | Revealing cards at the end to decide the winner. |
| Stand pat | In 5-Card Draw, keeping all your cards. |
| Wheel | A-2-3-4-5: the lowest straight in high games and the best hand in Razz. |
