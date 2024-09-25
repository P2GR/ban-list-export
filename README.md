
# Clan Ban List Export
This is a plugin that is used to export usernames banned from the clan.

## How to use
1. Once logged into the game open the Clan interface via Clan Chat channel 'Settings' button.
2. Click 'Bans' on the left side.
3. Export should now be in your clipboard ready to paste.

### Features
* Export banned usernames in CSV or JSON.
* Export to clipboard
* Can create a post to an url of your choosing.
* More to come

 



Web request body example for export via web request
```json
{
  "clanName": "Name of your clan",
  "clanBanListMaps": [
    {
      "rsn": "ClanMember 1",

    },
    {
      "rsn": "ClanMember 2",
    }
  ]
}
```


## Special Thanks
This plugin is loosely based off of [Clan Roster Helper](https://github.com/simbleau/third-party-roster). 
Some code may be present in this repo since I used their plugin as an example.