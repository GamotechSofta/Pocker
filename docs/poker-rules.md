# Poker rules

Source of truth: `Poker_Rules_Guide.docx` at the repository root.

This is a play-money game. Chip amounts are virtual.

## Hand rankings (high to low)

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

A Royal Flush is the highest Straight Flush (10-J-Q-K-A of one suit). Ties use kickers, not category alone.

## Variants

### Texas Hold'em (first to implement)

- 2 private hole cards, 5 community cards
- Stages: Pre-Flop, Flop (3), Turn (1), River (1), Showdown
- Best 5-card hand from 7 cards; a player may use 0, 1, or 2 hole cards
- Small Blind and Big Blind; dealer button moves clockwise
- Actions: Fold, Check, Bet, Call, Raise
- If everyone else folds, the remaining player wins without showing cards

### Omaha

- 4 hole cards, 5 community cards
- Final hand must use exactly 2 hole cards and exactly 3 community cards
- Same high-hand rankings as Hold'em
- Same streets and betting as Hold'em

### Pineapple

- 3 hole cards; discard exactly 1 before the flop
- Discarded card is out of play and is never a community card
- Then play like Texas Hold'em with the remaining 2 hole cards
- Best 5 of 7; highest hand wins

### 5-Card Draw

- No community cards
- Deal 5, bet, discard and replace, bet, showdown
- Maximum cards drawn is a house rule (`app.table.max-draw-count`, default 5)

### Razz

- 7-Card Stud low: no community cards, lowest 5-card hand wins
- Start with 3 private cards; deal one more each street through 7
- Aces are low; straights and flushes do not hurt; pairs are bad
- Best hand is A-2-3-4-5 (the wheel)
- Compare from the highest card downward

Implementation order is Hold'em, Omaha, Pineapple, 5-Card Draw, then Razz.
