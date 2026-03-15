# Simple Player Trades

A simple server-side Fabric mod for player-to-player item trading. Players connect with a vanilla client, no mods required on their end.


![Showcase](https://raw.githubusercontent.com/SirDoomTurtle/Simple-Player-Trades/tree/main/assets/showcase.gif)

---

## Features

- Server-side only, vanilla clients work fine
- Both players get a shared trade GUI with their own items on the left and the other player's items on the right
- Both players must click the accept button before anything is exchanged
- Moving items automatically resets your confirmation, so nothing goes through by accident
- The center divider turns green when a player confirms, and a sound notifies the other player
- If someone disconnects mid-trade, all items are returned to their owners
- Safe against inventory management client mods
- Full config file for tweaking settings

---

## Commands

| Command | Description |
|---|---|
| `/trade <player>` | Send a trade request |
| `/tradeaccept <player>` | Accept an incoming request |
| `/tradedeny <player>` | Deny an incoming request |
| `/tradecancel` | Cancel your outgoing request or active trade |

---

## How it works

1. Type `/trade <playername>` to send a request
2. The other player accepts with `/tradeaccept <yourname>`
3. A shared GUI opens, place your items on your side
4. When you are happy with the offer, click the green Accept button
5. Once both players confirm, the trade goes through

Clicking the red Cancel button or closing the GUI at any point cancels the trade and returns all items immediately.
